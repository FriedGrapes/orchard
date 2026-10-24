package me.kavishdevar.librepods.presentation.overlays

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.components.ContinuousRoundedShape
import me.kavishdevar.librepods.presentation.components.RingCell
import me.kavishdevar.librepods.presentation.theme.OrchardFont

/**
 * Orchard: 케이스 뚜껑을 열면 화면 위에서 내려오는 배터리 카드.
 * 윈도우 앱(UI/PopupWindow.xaml)과 같은 모양·수치를 쓴다 — 순검정 카드, 연속 곡률 모서리, 같은 여백.
 */
object LidCardSpec {
    val Radius = 30.dp
    val Width = 380.dp
    val PadH = 20.dp
    val PadTop = 16.dp
    val PadBottom = 22.dp
    val ArtHeight = 140.dp
    val Ring = 52.dp
    val RingStroke = 4.5.dp
    val Bg = Color(0xFF000000)
    val Fg = Color(0xFFFFFFFF)
    val Fg2 = Color(0xFF8E8E93)
    val Hairline = Color.White.copy(alpha = 0.09f)
    val Track = Color.White.copy(alpha = 0.14f)
    val CloseBg = Color(0xFF1C1C1E)
    // 움직임: 살짝 튀며 내려오고(스프링), 닫을 때는 빠르게 위로 빨려 올라간다
    const val EnterDamping = 0.8f
    const val EnterStiffness = 300f
    const val ExitMs = 280
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.2f)
}

/** 카드 내용(창과 떼어 두어 화면 시험에서도 그린다). */
@Composable
fun LidCardContent(
    name: String,
    model: AirPodsBase?,
    battery: List<Battery>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ContinuousRoundedShape(LidCardSpec.Radius)
    val isHeadphones = model?.isHeadphones == true
    fun level(c: Int) = battery.firstOrNull { it.component == c }?.takeIf { it.status != BatteryStatus.DISCONNECTED }
    fun charging(b: Battery?) = b?.status == BatteryStatus.CHARGING

    Column(
        modifier
            .widthIn(max = LidCardSpec.Width)
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(LidCardSpec.Bg)
            .border(0.5.dp, LidCardSpec.Hairline, shape)
            .padding(start = LidCardSpec.PadH, end = LidCardSpec.PadH, top = LidCardSpec.PadTop, bottom = LidCardSpec.PadBottom)
    ) {
        Box(Modifier.fillMaxWidth().height(30.dp)) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LidCardSpec.Fg)
            )
            CloseButton(onClose, Modifier.align(Alignment.CenterEnd))
        }

        // 기기 그림 — 카드가 내려온 뒤 살짝 떠오르며
        val appear = remember { Animatable(0f) }
        LaunchedEffect(model?.name) {
            appear.snapTo(0f)
            appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(LidCardSpec.ArtHeight)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s; scaleY = s
                    translationY = (1f - appear.value) * 10.dp.toPx()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(model?.budsRes ?: R.drawable.airpods_pro_2_buds),
                contentDescription = model?.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(if (isHeadphones) 1f else 1.15f)
            )
            if (!isHeadphones) {
                Image(
                    painter = painterResource(model?.caseRes ?: R.drawable.airpods_pro_2_case),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        @Composable
        fun Cell(b: Battery?, label: String, delayMs: Long) = RingCell(
            b?.level, charging(b), label,
            size = LidCardSpec.Ring, stroke = LidCardSpec.RingStroke, delayMs = delayMs,
            textColor = LidCardSpec.Fg, trackColor = LidCardSpec.Track, labelSize = 12,
        )
        if (isHeadphones) {
            val b = level(BatteryComponent.LEFT) ?: level(BatteryComponent.RIGHT)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Cell(b, stringResource(if (charging(b)) R.string.orchard_charging else R.string.orchard_battery), 160)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Cell(level(BatteryComponent.LEFT), stringResource(R.string.orchard_left), 160)
                Cell(level(BatteryComponent.RIGHT), stringResource(R.string.orchard_right), 210)
                Cell(level(BatteryComponent.CASE), stringResource(R.string.orchard_case), 260)
            }
        }
    }
}

/** 애플 카드의 동그란 닫기 단추. 누르면 살짝 작아진다. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) 0.9f else 1f, tween(140)) }
    Box(
        modifier
            .size(30.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(LidCardSpec.CloseBg)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(10.dp)) {
            val w = 1.8.dp.toPx()
            drawLine(LidCardSpec.Fg2, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(LidCardSpec.Fg2, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
}

/**
 * 화면 맨 위에 뜨는 창. 스프링으로 내려오고, 닫을 때는 위로 빠르게 빨려 올라간다.
 * 위로 밀어도 닫히고, 누르면 앱이 열린다. 뚜껑을 닫거나 10초가 지나면 저절로 닫힌다.
 */
@SuppressLint("ClickableViewAccessibility")
class LidCardWindow(private val context: Context, private val onOpenApp: () -> Unit = {}) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { dismiss() }
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var receiver: BroadcastReceiver? = null

    private var name by mutableStateOf("AirPods")
    private var model by mutableStateOf<AirPodsBase?>(null)
    private var battery by mutableStateOf<List<Battery>>(emptyList())
    private var visible by mutableStateOf(false)

    val isShowing get() = view != null

    fun show(name: String, model: AirPodsBase?, battery: List<Battery>) {
        this.name = name
        this.model = model
        this.battery = battery
        restartTimer()
        if (view != null) { visible = true; return }
        if (!Settings.canDrawOverlays(context)) return

        val topInset = wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { Animated(topInset) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            wm.addView(v, params)
        } catch (e: Exception) {
            Log.e("LidCard", "창을 띄우지 못함: ${e.message}")
            o.stop()
            return
        }
        view = v
        owner = o
        visible = true
        listenBattery()
    }

    /** 부드럽게 닫는다(애니메이션이 끝나면 창을 뗀다). */
    fun dismiss() {
        handler.removeCallbacks(autoClose)
        visible = false
    }

    private fun restartTimer() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, 10_000)
    }

    private fun remove() {
        val v = view ?: return
        view = null
        try { wm.removeView(v) } catch (_: Exception) {}
        owner?.stop(); owner = null
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun listenBattery() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.getParcelableArrayListExtra("data", Battery::class.java)?.let { battery = it }
            }
        }
        context.registerReceiver(r, IntentFilter(AirPodsNotifications.BATTERY_DATA), Context.RECEIVER_EXPORTED)
        receiver = r
    }

    @Composable
    private fun Animated(topInset: Int) {
        val density = LocalDensity.current
        val reduceMotion = remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        val progress = remember { Animatable(0f) }  // 0 = 화면 밖, 1 = 제자리
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val travel = with(density) { (topInset + 260.dp.roundToPx()).toFloat() }

        LaunchedEffect(visible) {
            if (visible) {
                drag.snapTo(0f)
                // 애플 알림처럼: 살짝 튀며 내려와 자리 잡는다
                progress.animateTo(1f, if (reduceMotion) tween(200) else spring(dampingRatio = LidCardSpec.EnterDamping, stiffness = LidCardSpec.EnterStiffness))
            } else {
                progress.animateTo(0f, tween(if (reduceMotion) 160 else LidCardSpec.ExitMs, easing = LidCardSpec.ExitEasing))
                remove()
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = with(density) { topInset.toDp() } + 8.dp, start = 10.dp, end = 10.dp, bottom = 28.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            LidCardContent(
                name = name, model = model, battery = battery,
                onClose = { dismiss() },
                modifier = Modifier
                    .graphicsLayer {
                        val p = progress.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        if (reduceMotion) {
                            alpha = p.coerceIn(0f, 1f)
                        } else {
                            translationY = (1f - p) * -travel + drag.value
                            val s = 0.9f + 0.1f * p
                            scaleX = s; scaleY = s
                            alpha = (p * 1.6f).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { handler.removeCallbacks(autoClose) },
                            onDragEnd = {
                                if (drag.value < -40.dp.toPx()) dismiss()
                                else {
                                    scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                                    restartTimer()
                                }
                            },
                        ) { change, dy ->
                            change.consume()
                            // 위로는 손가락을 그대로, 아래로는 고무줄처럼 조금만
                            val next = drag.value + if (drag.value + dy > 0) dy * 0.25f else dy
                            scope.launch { drag.snapTo(next) }
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        dismiss(); onOpenApp()
                    }
            )
        }
    }

    /** WindowManager 로 띄운 ComposeView 에 필요한 생명주기. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
