package me.kavishdevar.librepods.link

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem

/** Orchard Link: 설정 화면에 보이는 짝짓기 진행 상태. */
sealed interface PairStatus {
    data object Idle : PairStatus
    data class Pairing(val name: String) : PairStatus
    data class Paired(val name: String) : PairStatus
    data object NotFound : PairStatus
    data object BadQr : PairStatus
}

/**
 * Orchard Link: QR(앱 안 스캐너 또는 카메라 앱의 orchard://pair 링크)로 PC와 짝짓기.
 * 화면이 닫혀도 끝까지 진행하도록 앱 전체 범위에서 돌린다.
 */
object LinkPairing {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<PairStatus>(PairStatus.Idle)
    val status: StateFlow<PairStatus> get() = _status

    /** [toast] — 카메라 앱에서 열었을 때처럼 설정 화면이 안 보일 수 있으면 결과를 토스트로도 알린다. */
    fun start(context: Context, text: String?, toast: Boolean = false) {
        val app = context.applicationContext
        val url = text?.let { PairUrl.parse(it) }
        if (url == null) {
            _status.value = PairStatus.BadQr
            if (toast) toast(app, app.getString(R.string.orchard_link_bad_qr))
            return
        }
        if (_status.value is PairStatus.Pairing) return
        _status.value = PairStatus.Pairing(url.name)
        scope.launch {
            val ok = runCatching { OrchardLink.get(app).pair(url) }.getOrDefault(false)
            val name = LinkStore.get(app).peer(url.id)?.name ?: url.name
            _status.value = if (ok) PairStatus.Paired(name) else PairStatus.NotFound
            if (toast) toast(app, statusText(app, _status.value) ?: return@launch)
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    fun statusText(context: Context, s: PairStatus): String? = when (s) {
        PairStatus.Idle -> null
        is PairStatus.Pairing -> context.getString(R.string.orchard_link_pairing, s.name, KoParticle.waGwa(s.name))
        is PairStatus.Paired -> context.getString(R.string.orchard_link_paired, s.name, KoParticle.waGwa(s.name))
        PairStatus.NotFound -> context.getString(R.string.orchard_link_not_found)
        PairStatus.BadQr -> context.getString(R.string.orchard_link_bad_qr)
    }
}

/** 설정 화면용 — QR 스캐너를 띄우고, 짝 목록·진행 상태를 앱 상태에서 읽는다. */
@Composable
fun LinkSettingsSection() {
    val context = LocalContext.current
    val store = remember { LinkStore.get(context) }
    val peers by store.peers.collectAsState()
    val status by LinkPairing.status.collectAsState()
    val prompt = stringResource(R.string.orchard_link_scan_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        // 취소하면 contents 가 null — 아무것도 바꾸지 않는다
        result.contents?.let { LinkPairing.start(context, it) }
    }
    LinkSettingsContent(
        peers = peers,
        status = status,
        onScan = {
            scanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(prompt)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        },
        onRemove = { OrchardLink.get(context).removePeer(it.id) },
    )
}

/** 상태를 밖에서 받는 본체(화면 시험에서 그대로 그린다). */
@Composable
fun LinkSettingsContent(
    peers: List<LinkPeer>,
    status: PairStatus,
    nowMs: Long = System.currentTimeMillis(),
    onScan: () -> Unit = {},
    onRemove: (LinkPeer) -> Unit = {},
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<LinkPeer?>(null) }
    val pairing = status is PairStatus.Pairing

    StyledList(
        title = stringResource(R.string.orchard_link_title),
        description = stringResource(R.string.orchard_link_section_desc),
    ) {
        StyledListItem(
            modifier = Modifier.pressScale(),
            name = stringResource(R.string.orchard_link_scan),
            description = LinkPairing.statusText(context, status) ?: stringResource(R.string.orchard_link_scan_desc),
            onClick = { if (!pairing) onScan() },
            // 진행·결과 글이 길어도 제목 옆에서 찌그러지지 않게 아래 줄로
            orientation = me.kavishdevar.librepods.presentation.components.ListItemOrientation.Vertical,
            trailingContent = {
                AnimatedContent(
                    targetState = pairing,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "pair",
                ) { busy ->
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    } else {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            },
        )
        peers.forEachIndexed { i, peer ->
            StyledListItem(
                modifier = Modifier.springEntrance(i),
                name = peer.name,
                description = lastSeenText(context, peer, nowMs),
                onClick = { confirm = peer },
                trailingContent = {
                    TextButton(onClick = { confirm = peer }, modifier = Modifier.pressScale()) {
                        Text(stringResource(R.string.orchard_link_remove))
                    }
                },
            )
        }
    }

    confirm?.let { peer ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.orchard_link_remove_title, peer.name)) },
            text = { Text(stringResource(R.string.orchard_link_remove_desc)) },
            confirmButton = {
                TextButton(onClick = { onRemove(peer); confirm = null }) {
                    Text(stringResource(R.string.orchard_link_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.orchard_link_cancel)) }
            },
        )
    }
}

/** "방금 · 와이파이", "3분 전 · 블루투스", "아직 신호 없음". */
fun lastSeenText(context: Context, peer: LinkPeer, nowMs: Long): String {
    if (peer.lastSeenMs <= 0L) return context.getString(R.string.orchard_link_never)
    val sec = ((nowMs - peer.lastSeenMs) / 1000).coerceAtLeast(0)
    val ago = when {
        sec < 60 -> context.getString(R.string.orchard_link_seen_now)
        sec < 3600 -> context.getString(R.string.orchard_link_seen_min, (sec / 60).toInt())
        sec < 86_400 -> context.getString(R.string.orchard_link_seen_hour, (sec / 3600).toInt())
        else -> context.getString(R.string.orchard_link_seen_day, (sec / 86_400).toInt())
    }
    val via = when (peer.lastVia) {
        LinkPeer.VIA_WIFI -> context.getString(R.string.orchard_link_via_wifi)
        LinkPeer.VIA_BLE -> context.getString(R.string.orchard_link_via_ble)
        else -> null
    }
    return if (via != null) "$ago · $via" else ago
}

/** HANDOFF 모션 토큰: 누르면 0.97로 살짝 작아진다(눌린 동안만). */
private fun Modifier.pressScale(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        scale.animateTo(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
    }
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                // 손을 떼거나 취소될 때까지(안쪽 클릭 처리는 그대로 두고 지켜보기만)
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                } while (e.changes.any { it.pressed })
                pressed = false
            }
        }
}

/** HANDOFF 모션 토큰: spring(damping 0.9, StiffnessMediumLow), alpha 0→1, scale 0.94→1, 위로 12dp. 줄마다 40ms 늦게. */
private fun Modifier.springEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index)
        progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.94f + 0.06f * p
        scaleX = s; scaleY = s
        translationY = (1f - p) * rise
    }
}
