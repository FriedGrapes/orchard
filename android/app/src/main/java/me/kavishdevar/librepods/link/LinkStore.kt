package me.kavishdevar.librepods.link

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

/** Orchard: 짝지은 PC 하나. */
data class LinkPeer(
    val id: Long,
    val name: String,
    val keyB64: String,
    val lastAddr: String? = null,
    val lastSeenMs: Long = 0L,
    /** "wifi" 또는 "ble", 아직 신호가 없으면 null. */
    val lastVia: String? = null,
) {
    val key: ByteArray get() = Base64.getUrlDecoder().decode(keyB64)
    val idHex: String get() = "%08x".format(id)

    companion object {
        const val VIA_WIFI = "wifi"
        const val VIA_BLE = "ble"
    }
}

/**
 * Orchard: 이 폰의 기기 번호·순번·짝 목록을 SharedPreferences 에 둔다.
 * 화면이 바로 바뀌도록 짝 목록은 [peers] 흐름으로도 내보낸다.
 */
class LinkStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("orchard_link", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    /** 이 폰의 4바이트 기기 번호(처음 한 번 만들고 그대로). */
    val selfId: Long

    private var seq: Int

    private val _peers = MutableStateFlow(load())
    val peers: StateFlow<List<LinkPeer>> get() = _peers

    init {
        var id = prefs.getLong(KEY_SELF_ID, -1L)
        if (id < 0) {
            val b = ByteArray(4).also { random.nextBytes(it) }
            id = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
                ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
            prefs.edit { putLong(KEY_SELF_ID, id) }
        }
        selfId = id
        seq = prefs.getInt(KEY_SEQ, random.nextInt(256))
    }

    /** 보낼 때마다 +1(넘치면 0). */
    @Synchronized
    fun nextSeq(): Int {
        seq = (seq + 1) and 0xFF
        prefs.edit { putInt(KEY_SEQ, seq) }
        return seq
    }

    fun peer(id: Long): LinkPeer? = _peers.value.firstOrNull { it.id == id }

    @Synchronized
    fun addPeer(url: PairUrl) {
        val keyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(url.key)
        val old = peer(url.id)
        val peer = LinkPeer(
            id = url.id,
            name = url.name.ifBlank { old?.name ?: "PC" },
            keyB64 = keyB64,
            lastAddr = url.addrs.firstOrNull() ?: old?.lastAddr,
            lastSeenMs = old?.lastSeenMs ?: 0L,
            lastVia = old?.lastVia,
        )
        save(_peers.value.filterNot { it.id == url.id } + peer)
    }

    @Synchronized
    fun removePeer(id: Long) = save(_peers.value.filterNot { it.id == id })

    /** 믿을 수 있는 신호를 받을 때마다 마지막 주소·때·길을 기억한다. */
    @Synchronized
    fun touch(id: Long, addr: String?, via: String, name: String? = null) {
        val list = _peers.value
        val p = list.firstOrNull { it.id == id } ?: return
        val updated = p.copy(
            lastAddr = addr ?: p.lastAddr,
            lastSeenMs = System.currentTimeMillis(),
            lastVia = via,
            name = name?.takeIf { it.isNotBlank() } ?: p.name,
        )
        save(list.map { if (it.id == id) updated else it })
    }

    private fun save(list: List<LinkPeer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("keyB64", it.keyB64)
                put("lastAddr", it.lastAddr ?: JSONObject.NULL)
                put("lastSeenMs", it.lastSeenMs)
                put("lastVia", it.lastVia ?: JSONObject.NULL)
            })
        }
        prefs.edit { putString(KEY_PEERS, arr.toString()) }
        _peers.value = list
    }

    private fun load(): List<LinkPeer> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_PEERS, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LinkPeer(
                id = o.getLong("id"),
                name = o.optString("name", "PC"),
                keyB64 = o.getString("keyB64"),
                lastAddr = o.optString("lastAddr").takeIf { !o.isNull("lastAddr") && it.isNotEmpty() },
                lastSeenMs = o.optLong("lastSeenMs", 0L),
                lastVia = o.optString("lastVia").takeIf { !o.isNull("lastVia") && it.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_SELF_ID = "self_id"
        private const val KEY_SEQ = "seq"
        private const val KEY_PEERS = "peers"

        @Volatile private var instance: LinkStore? = null

        fun get(context: Context): LinkStore =
            instance ?: synchronized(this) { instance ?: LinkStore(context).also { instance = it } }
    }
}
