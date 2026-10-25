package me.kavishdevar.librepods.link

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.kavishdevar.librepods.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Orchard Link — 폰과 PC가 서명된 작은 신호를 주고받아 에어팟을 아이폰처럼 바로 넘긴다(shared/link.md).
 *
 * - 와이파이: UDP 47820 소켓 하나로 받고 보낸다(짝의 마지막 주소 + 255.255.255.255 + 각 네트워크 브로드캐스트).
 * - BLE: WANT에 400ms 안에 답이 없으면 같은 신호(본문 없음)를 회사 번호 0xFFFF로 2초 광고. 받는 쪽은 BLEManager 가 넘겨준다.
 *
 * 앱 전체에 하나만 둔다([get]). AirPodsService 가 [start]/[stop] 하고, 짝짓기 화면은 서비스가 없어도 [ensureStarted] 로 연다.
 */
class OrchardLink private constructor(context: Context) {

    /** 서비스가 알려 주는 이 폰의 상태와 '넘겨주기' 동작. */
    interface Host {
        /** 에어팟 소리가 지금 이 폰에 있는지. */
        fun audioHere(): Boolean

        /** 재생 일시정지 → (할 수 있으면) 에어팟 오디오 끊기 → 잠깐 자동 가져오기 멈춤. 메인 스레드에서 불린다. */
        fun releaseAudio(peerName: String)
    }

    enum class Reason { MUSIC, CALL }

    sealed interface Result {
        data object Released : Result
        data class Busy(val peerName: String) : Result
        data object NoAnswer : Result
    }

    private val ctx = context.applicationContext
    val store = LinkStore.get(ctx)
    private val guard = ReplayGuard()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var host: Host? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var socket: DatagramSocket? = null

    private val replies = MutableSharedFlow<Pair<LinkPeer, LinkPacket>>(extraBufferCapacity = 16)
    private val pairOks = MutableSharedFlow<LinkPeer>(extraBufferCapacity = 4)

    fun hasPeers(): Boolean = store.peers.value.isNotEmpty()

    /** 서비스가 시작할 때. 이미 열려 있으면 [host] 만 바꾼다. */
    @Synchronized
    fun start(host: Host?) {
        if (host != null) this.host = host
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch { receiveLoop() }
        s.launch { helloLoop() }
        Log.d(TAG, "시작 (기기 번호 ${"%08x".format(store.selfId)}, 짝 ${store.peers.value.size}개)")
    }

    /** 짝짓기 화면처럼 서비스 없이 쓸 때 — 이미 열려 있으면 그대로. */
    fun ensureStarted() = start(null)

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { socket?.close() }
        socket = null
        host = null
        Log.d(TAG, "멈춤")
    }

    // ───────────────────────── 가져오는 쪽 ─────────────────────────

    /**
     * 짝들에게 WANT를 보내고 최대 1.5초 동안 첫 RELEASED/BUSY 를 기다린다.
     * 짝이 없으면 바로 [Result.NoAnswer].
     */
    suspend fun requestRelease(reason: Reason): Result {
        val peers = store.peers.value
        if (peers.isEmpty()) return Result.NoAnswer
        ensureStarted()
        val flags = stateFlags() or (if (reason == Reason.CALL) LinkPacket.FLAG_FOR_CALL else 0)
        val want = LinkPacket(LinkPacket.WANT, flags, store.selfId, nowSec(), store.nextSeq())
        val wire = peers.map { it to want.encode(it.key) }
        val started = System.currentTimeMillis()

        return coroutineScope {
            // 보내기 전에 먼저 귀를 연다(답이 아주 빨리 와도 놓치지 않게)
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(WANT_WAIT_MS) {
                    replies.first { it.second.type == LinkPacket.RELEASED || it.second.type == LinkPacket.BUSY }
                }
            }
            val wifi = launch(Dispatchers.IO) {
                var last = 0L
                for (at in WANT_RESEND_MS) {
                    delay(at - last); last = at
                    wire.forEach { (peer, bytes) -> sendWifi(bytes, peer.lastAddr) }
                }
            }
            val ble = launch {
                delay(BLE_AFTER_MS)
                if (waiter.isActive) {
                    Log.d(TAG, "와이파이 답 없음 — BLE로도 WANT")
                    peers.forEach { advertise(want.withoutBody().encode(it.key)) }
                }
            }
            val got = waiter.await()
            wifi.cancel(); ble.cancel()
            val ms = System.currentTimeMillis() - started
            when {
                got == null -> { Log.d(TAG, "WANT 답 없음 (${ms}ms)"); Result.NoAnswer }
                got.second.type == LinkPacket.BUSY -> { Log.d(TAG, "${got.first.name} 통화 중 (${ms}ms)"); Result.Busy(got.first.name) }
                else -> { Log.d(TAG, "${got.first.name} 넘겨줌 (${ms}ms)"); Result.Released }
            }
        }
    }

    // ───────────────────────── 짝짓기 ─────────────────────────

    /**
     * QR로 받은 PC를 저장하고 PAIR를 3초 동안 0.5초마다 보내며 PAIR_OK를 기다린다.
     * 답이 없어도 짝은 저장해 둔다(BLE로는 될 수 있고, 나중에 HELLO가 오면 이어진다).
     */
    suspend fun pair(url: PairUrl): Boolean {
        store.addPeer(url)
        ensureStarted()
        val lock = runCatching {
            ctx.getSystemService(WifiManager::class.java)?.createMulticastLock("orchard-pair")?.apply {
                setReferenceCounted(false); acquire()
            }
        }.onFailure { Log.d(TAG, "MulticastLock 못 잡음: ${it.message}") }.getOrNull()
        try {
            return coroutineScope {
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(PAIR_WAIT_MS) { pairOks.first { it.id == url.id } }
                }
                val sender = launch(Dispatchers.IO) {
                    repeat(PAIR_SENDS) {
                        val p = LinkPacket(LinkPacket.PAIR, stateFlags(), store.selfId, nowSec(), store.nextSeq(), nameBytes())
                        val bytes = p.encode(url.key)
                        url.addrs.forEach { sendTo(bytes, it, url.port) }
                        sendWifi(bytes, null)
                        delay(PAIR_EVERY_MS)
                    }
                }
                val ok = waiter.await() != null
                sender.cancel()
                Log.d(TAG, "짝짓기 ${if (ok) "성공" else "답 없음"}: ${url.name}")
                ok
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    fun removePeer(id: Long) = store.removePeer(id)

    // ───────────────────────── 받기 ─────────────────────────

    /** BLEManager 가 회사 번호 0xFFFF·4F 01 로 시작하는 광고를 넘겨준다. */
    fun onBleData(bytes: ByteArray) {
        val s = scope ?: return
        s.launch { handle(bytes, null, LinkPeer.VIA_BLE) }
    }

    private suspend fun receiveLoop() {
        val buf = ByteArray(1024)
        while (currentScopeActive()) {
            val sock = socket ?: openSocket()
            if (sock == null) { delay(5_000); continue }
            try {
                val dp = DatagramPacket(buf, buf.size)
                sock.receive(dp)
                val bytes = dp.data.copyOfRange(dp.offset, dp.offset + dp.length)
                val addr = (dp.address as? Inet4Address)?.hostAddress ?: dp.address?.hostAddress
                handle(bytes, addr, LinkPeer.VIA_WIFI)
            } catch (e: Exception) {
                if (!currentScopeActive()) return
                Log.d(TAG, "받기 오류: ${e.message}")
                runCatching { sock.close() }
                if (socket === sock) socket = null
                delay(1_000)
            }
        }
    }

    private suspend fun currentScopeActive(): Boolean = kotlin.coroutines.coroutineContext.isActive

    @Synchronized
    private fun openSocket(): DatagramSocket? {
        socket?.let { return it }
        return try {
            DatagramSocket(null as InetSocketAddress?).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(LinkPacket.PORT))
            }.also { socket = it; Log.d(TAG, "UDP ${LinkPacket.PORT} 열림") }
        } catch (e: Exception) {
            Log.w(TAG, "UDP ${LinkPacket.PORT} 못 엶: ${e.message}")
            null
        }
    }

    private suspend fun handle(bytes: ByteArray, addr: String?, via: String) {
        val sender = LinkPacket.peekSender(bytes) ?: return
        if (sender == store.selfId) return // 내가 보낸 브로드캐스트
        val peer = store.peer(sender) ?: return
        val packet = (LinkPacket.decode(bytes, peer.key) as? LinkPacket.Decoded.Ok)?.packet ?: return
        if (!guard.accept(packet)) return
        val named = packet.type == LinkPacket.HELLO || packet.type == LinkPacket.PAIR_OK
        store.touch(peer.id, addr, via, if (named) packet.bodyText else null)
        val fresh = store.peer(peer.id) ?: peer
        when (packet.type) {
            LinkPacket.WANT -> handleWant(fresh, packet, via)
            LinkPacket.RELEASED, LinkPacket.BUSY -> replies.tryEmit(fresh to packet)
            LinkPacket.PAIR_OK -> pairOks.tryEmit(fresh)
            else -> Unit // HELLO 는 마지막 주소·때만 기억, PAIR 는 PC가 받는 신호
        }
    }

    // ───────────────────────── 넘겨주는 쪽 ─────────────────────────

    private suspend fun handleWant(peer: LinkPeer, want: LinkPacket, via: String) {
        val forCall = want.flags and LinkPacket.FLAG_FOR_CALL != 0
        Log.d(TAG, "${peer.name}에서 WANT(${if (forCall) "전화" else "음악"}, $via)")
        val type: Int
        var handedOff = false
        if (isInCall()) {
            type = LinkPacket.BUSY
        } else {
            val h = host
            if (h != null && h.audioHere()) {
                withContext(Dispatchers.Main) { h.releaseAudio(peer.name) }
                handedOff = true
            }
            type = LinkPacket.RELEASED
        }
        val reply = LinkPacket(type, stateFlags(), store.selfId, nowSec(), store.nextSeq())
        sendWifi(reply.encode(peer.key), peer.lastAddr)
        if (via == LinkPeer.VIA_BLE) advertise(reply.withoutBody().encode(peer.key))
        Log.d(TAG, "답: ${if (type == LinkPacket.BUSY) "BUSY" else "RELEASED"}")
        if (handedOff) notifyHandedOff(peer.name)
    }

    // ───────────────────────── 주기적인 HELLO ─────────────────────────

    private suspend fun helloLoop() {
        delay(1_000)
        while (currentScopeActive()) {
            if (socket == null) openSocket()
            val peers = store.peers.value
            if (peers.isNotEmpty()) {
                val hello = LinkPacket(LinkPacket.HELLO, stateFlags(), store.selfId, nowSec(), store.nextSeq(), nameBytes())
                peers.forEach { sendWifi(hello.encode(it.key), it.lastAddr) }
            }
            delay(HELLO_EVERY_MS)
        }
    }

    // ───────────────────────── 보내기 ─────────────────────────

    /** 짝의 마지막 주소 + 255.255.255.255 + 각 네트워크의 브로드캐스트 주소로. */
    private fun sendWifi(bytes: ByteArray, lastAddr: String?) {
        val targets = LinkedHashSet<String>()
        lastAddr?.let { targets += it }
        targets += "255.255.255.255"
        targets += broadcastAddrs()
        targets.forEach { sendTo(bytes, it, LinkPacket.PORT) }
    }

    private fun sendTo(bytes: ByteArray, host: String, port: Int) {
        val sock = socket ?: openSocket() ?: return
        try {
            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port))
        } catch (e: Exception) {
            Log.v(TAG, "보내기 실패 $host: ${e.message}")
        }
    }

    private fun broadcastAddrs(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast?.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    private fun advertise(bytes: ByteArray) {
        if (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "BLE 광고 권한 없음 — 건너뜀")
            return
        }
        try {
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            if (!adapter.isEnabled) return
            val advertiser = adapter.bluetoothLeAdvertiser ?: return
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(BLE_ADVERTISE_MS.toInt())
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(LinkPacket.BLE_COMPANY_ID, bytes)
                .build()
            val callback = object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    Log.d(TAG, "BLE 광고 실패: $errorCode")
                }
            }
            advertiser.startAdvertising(settings, data, callback)
            // setTimeout 이 멈추지만, 혹시 몰라 한 번 더
            main.postDelayed({ runCatching { advertiser.stopAdvertising(callback) } }, BLE_ADVERTISE_MS + 100)
        } catch (e: Exception) {
            Log.d(TAG, "BLE 광고 오류: ${e.message}")
        }
    }

    // ───────────────────────── 이 폰의 상태 ─────────────────────────

    /** 통화 중(전화·영상통화 앱 포함, 벨 울림 포함)인지. */
    @SuppressLint("MissingPermission")
    fun isInCall(): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        val mode = am?.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE) return true
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                @Suppress("DEPRECATION")
                val state = ctx.getSystemService(TelephonyManager::class.java)?.callState
                if (state != null && state != TelephonyManager.CALL_STATE_IDLE) return true
            }
        }
        return false
    }

    private fun stateFlags(): Int {
        var f = 0
        if (runCatching { host?.audioHere() == true }.getOrDefault(false)) f = f or LinkPacket.FLAG_AUDIO_HERE
        if (ctx.getSystemService(AudioManager::class.java)?.isMusicActive == true) f = f or LinkPacket.FLAG_PLAYING
        if (isInCall()) f = f or LinkPacket.FLAG_IN_CALL
        return f
    }

    private fun nameBytes(): ByteArray {
        var name = phoneName(ctx)
        while (name.toByteArray(Charsets.UTF_8).size > LinkPacket.MAX_BODY) name = name.dropLast(1)
        return name.toByteArray(Charsets.UTF_8)
    }

    // ───────────────────────── 알림 ─────────────────────────

    /** "에어팟을 ○○(으)로 넘겼어요" — 4초 뒤 저절로 사라짐. */
    private fun notifyHandedOff(peerName: String) =
        notify(NOTI_HANDED_OFF, ctx.getString(R.string.orchard_link_handed_off, peerName, KoParticle.ro(peerName)))

    /** "○○이(가) 통화 중이라 기다릴게요". */
    fun notifyPeerBusy(peerName: String) =
        notify(NOTI_PEER_BUSY, ctx.getString(R.string.orchard_link_peer_busy, peerName, KoParticle.iGa(peerName)))

    private fun notify(id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "알림 권한 없음: $text")
            return
        }
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.orchard_link_channel), NotificationManager.IMPORTANCE_LOW)
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.airpods)
                .setContentTitle(text)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTI_TIMEOUT_MS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(id, n)
        }.onFailure { Log.d(TAG, "알림 실패: ${it.message}") }
    }

    companion object {
        private const val TAG = "OrchardLink"
        private const val CHANNEL = "orchard_link"
        private const val NOTI_HANDED_OFF = 47820
        private const val NOTI_PEER_BUSY = 47821
        private const val NOTI_TIMEOUT_MS = 4_000L

        const val WANT_WAIT_MS = 1_500L
        private val WANT_RESEND_MS = listOf(0L, 150L, 400L)
        private const val BLE_AFTER_MS = 400L
        private const val BLE_ADVERTISE_MS = 2_000L
        private const val HELLO_EVERY_MS = 25_000L
        private const val PAIR_SENDS = 6 // 3초 동안 0.5초마다
        private const val PAIR_EVERY_MS = 500L
        private const val PAIR_WAIT_MS = 3_500L

        @Volatile private var instance: OrchardLink? = null

        fun get(context: Context): OrchardLink =
            instance ?: synchronized(this) { instance ?: OrchardLink(context).also { instance = it } }

        /** 이미 만들어져 있을 때만(서비스 밖에서 함부로 만들지 않게). */
        fun current(): OrchardLink? = instance

        /** 신호 본문에 넣는 이 폰의 이름. */
        fun phoneName(context: Context): String =
            runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL

        private fun nowSec(): Long = System.currentTimeMillis() / 1000
    }
}
