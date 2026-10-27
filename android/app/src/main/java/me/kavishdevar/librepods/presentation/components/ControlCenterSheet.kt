package me.kavishdevar.librepods.presentation.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.theme.OrchardFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orchard: 빠른 설정 ANC 타일로 여는 제어 센터 카드.
 * v0.2.4 시안 A(애플 카드 시트) 위에 v0.2.5 '킥' 세 가지(사용자 결정 2026-10-27):
 *  - 살아 있는 기기: 폰을 기울이면 사진이 입체로 따라 기울고 표면에 빛이 미끄러짐, 누르면 통 튀며 그 배터리 고리도 뜀
 *  - 모드가 공기를 바꾼다: 카드 뒤에 넓고 옅은 모드 색 빛, 바꾸는 순간 물결이 퍼짐, 주변음은 빛 알갱이가 들어옴
 *  - 손맛: 볼륨 고무줄·한 칸마다 눈금과 진동, 모드 원이 물방울처럼 늘어나며 건너감, 카드를 끌면 사진이 그네처럼 흔들림
 * 구조: 배터리 고리를 각 제품 바로 아래에, 볼륨·모드는 같은 높이 알약, 간격은 8의 배수.
 */
object ControlCenterSpec {
    val Radius = 42.dp
    val Margin = 10.dp
    val Width = 440.dp
    val PadH = 16.dp
    val ArtHeight = 108.dp
    val Ring = 46.dp
    val RingStroke = 4.dp
    val Control = 56.dp          // 볼륨·모드 알약 높이(둘이 같게)
    val Dot = 48.dp              // 모드 선택 원
    val Bg = Color(0xFF000000)
    val Fg = Color(0xFFFFFFFF)
    val Fg2 = Color(0xFF8E8E93)
    val Fill = Color.White.copy(alpha = 0.085f)
    val Hairline = Color.White.copy(alpha = 0.09f)
    val Track = Color.White.copy(alpha = 0.14f)
    val Blue = Color(0xFF0A84FF)
    val Gray = Color(0xFF636366)
    const val Scrim = 0.5f
    // 움직임은 뚜껑 카드와 같게: 튀지 않고 빠르게 올라오고, 닫을 때는 처음부터 속도를 내 아래로 빠진다
    const val EnterDamping = 0.88f
    const val EnterStiffness = 650f
    const val ExitMs = 200
    val ExitEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

/** 카드를 아래로 끈 거리(px)를 읽는 함수 — 사진 그네 흔들림에 쓴다(다시 그리기 없이 매 프레임 읽음). */
val LocalSheetPull = staticCompositionLocalOf<() -> Float> { { 0f } }
/** 꾸밈 움직임(기울기·빛 흐름·흔들림)을 켤지. 동작 줄이기 설정이면 끈다. */
val LocalRichMotion = staticCompositionLocalOf { true }

/** 모드마다 카드 뒤 빛의 색 — 넓게 퍼지고 옅게(채도 낮춘 색, 가장 진한 곳도 18%). */
private class AuraTone(val a: Color, val b: Color, val c: Color, val strength: Float, val spread: Float)

private fun auraOf(m: NoiseControlMode) = when (m) {
    NoiseControlMode.ADAPTIVE -> AuraTone(Color(0xFFE59AB4), Color(0xFFA79BEF), Color(0xFF8FD9C0), 1f, 1f)
    NoiseControlMode.NOISE_CANCELLATION -> AuraTone(Color(0xFF5B7BE0), Color(0xFF4462C8), Color(0xFF7B93F0), 0.9f, 1.3f)
    NoiseControlMode.TRANSPARENCY -> AuraTone(Color(0xFFEFC98E), Color(0xFF9CD3EE), Color(0xFFF3E6CF), 0.9f, 1f)
    NoiseControlMode.OFF -> AuraTone(Color(0xFF8E8E93), Color(0xFF8E8E93), Color(0xFF8E8E93), 0.3f, 1f)
}

private fun modeColor(m: NoiseControlMode) = when (m) {
    NoiseControlMode.ADAPTIVE -> Color(0xFFBF9BF2)
    NoiseControlMode.NOISE_CANCELLATION -> ControlCenterSpec.Blue
    NoiseControlMode.TRANSPARENCY -> Color(0xFFF2C66D)
    NoiseControlMode.OFF -> ControlCenterSpec.Fg2
}

@Composable
fun ControlCenterSheetContent(
    name: String,
    model: AirPodsBase?,
    battery: List<Battery>,
    volumeFraction: Float,
    onVolumeFraction: (Float) -> Unit,
    onVolumeDrag: (Boolean) -> Unit,
    modes: List<NoiseControlMode>,
    selected: NoiseControlMode,
    onSelect: (NoiseControlMode) -> Unit,
    modifier: Modifier = Modifier,
    volumeSteps: Int = 15,
) {
    val shape = ContinuousRoundedShape(ControlCenterSpec.Radius)
    val rich = LocalRichMotion.current
    val scope = rememberCoroutineScope()

    // 모드가 바뀌는 순간의 물결·빛 알갱이(카드 좌표)
    val geo = remember { Geo() }
    val ripple = remember { Animatable(1f) }
    var rippleAt by remember { mutableStateOf(Offset.Zero) }
    var rippleColor by remember { mutableStateOf(Color.White) }
    val dust = remember { Animatable(1f) }
    var lastMode by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected == lastMode) return@LaunchedEffect
        lastMode = selected
        if (!rich) return@LaunchedEffect
        val i = modes.indexOf(selected)
        geo.slots.getOrNull(i)?.let { rippleAt = it - geo.card }
        rippleColor = modeColor(selected)
        launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(1000, easing = ControlCenterSpec.ExitEasing)) }
        if (selected == NoiseControlMode.TRANSPARENCY) launch { dust.snapTo(0f); dust.animateTo(1f, tween(1600, easing = LinearEasing)) }
    }

    Box(
        modifier
            .widthIn(max = ControlCenterSpec.Width)
            .fillMaxWidth()
            .shadow(32.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(ControlCenterSpec.Bg)
            .onGloballyPositioned { geo.card = it.positionInRoot() }
    ) {
        Aura(selected, rich, Modifier.matchParentSize())
        // 물결·빛 알갱이는 내용 뒤, 빛 위
        Canvas(Modifier.matchParentSize()) {
            val p = ripple.value
            if (p < 1f) {
                val r = 24.dp.toPx() + p * size.width * 1.1f
                drawCircle(rippleColor.copy(alpha = 0.07f * (1f - p)), r, rippleAt)
                drawCircle(rippleColor.copy(alpha = 0.45f * (1f - p)), r, rippleAt, style = Stroke(1.5.dp.toPx()))
            }
            val d = dust.value
            if (d < 1f) {
                for (k in 0 until 16) {
                    val seed = (k * 7919 % 100) / 100f
                    val left = k % 2 == 0
                    val start = Offset(if (left) -4f else size.width + 4f, size.height * (0.12f + 0.76f * seed))
                    val local = ((d - k * 0.025f) / 0.75f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                    val travel = (90f + 140f * ((k * 31 % 17) / 17f)) * density / 2.6f
                    val pos = start + Offset((if (left) 1f else -1f) * travel * eased * 2.2f, (seed - 0.5f) * 40f * eased)
                    val a = sin(PI.toFloat() * local) * 0.7f
                    if (a > 0f) drawCircle(Color.White.copy(alpha = a), 1.4.dp.toPx(), pos)
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .border(0.5.dp, ControlCenterSpec.Hairline, shape)
                // 카드 안을 누르면 닫히지 않게 여기서 받는다(바깥 누름만 닫기)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = ControlCenterSpec.PadH, end = ControlCenterSpec.PadH, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(36.dp, 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
                style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = ControlCenterSpec.Fg)
            )
            StatusLine(selected)
            DeviceStage(model, battery, rich, Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(24.dp))
            VolumeBar(volumeFraction, volumeSteps, onVolumeFraction, onVolumeDrag)
            Spacer(Modifier.height(8.dp))
            ModePicker(modes, selected, onSelect, onSlotCenters = { list -> geo.slots = list })
        }
    }
}

/** 물결 시작점을 구하려고 기억해 두는 자리(화면 기준). 그리기에는 안 쓰므로 상태로 두지 않는다. */
private class Geo { var card = Offset.Zero; var slots: List<Offset> = emptyList() }

/** 이름 아래 한 줄: 모드 색 점 + 지금 상태(모드가 바뀌면 글이 겹쳐 바뀜). */
@Composable
private fun StatusLine(mode: NoiseControlMode) {
    val dot by animateColorAsState(modeColor(mode), tween(400), label = "statusDot")
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Crossfade(mode, animationSpec = tween(260), label = "status") { m ->
            Text(
                text = stringResource(statusLabel(m)),
                maxLines = 1,
                style = TextStyle(fontFamily = OrchardFont, fontSize = 12.5.sp, color = ControlCenterSpec.Fg2)
            )
        }
    }
}

/** 카드 뒤 빛: 크고 옅은 원형 빛 세 덩이가 아주 천천히 떠다닌다. 소음 차단은 빛이 가장자리로 물러나 가운데가 고요해진다. */
@Composable
private fun Aura(mode: NoiseControlMode, rich: Boolean, modifier: Modifier) {
    val tone = auraOf(mode)
    val ca by animateColorAsState(tone.a, tween(900), label = "auraA")
    val cb by animateColorAsState(tone.b, tween(900), label = "auraB")
    val cc by animateColorAsState(tone.c, tween(900), label = "auraC")
    val strength by animateFloatAsState(tone.strength, tween(900), label = "auraS")
    val spread by animateFloatAsState(tone.spread, tween(1100, easing = ControlCenterSpec.ExitEasing), label = "auraSpread")
    // 처음엔 빛이 천천히 피어난다
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(Unit) { bloom.animateTo(1f, tween(1200, delayMillis = 150)) }
    val t = if (rich) {
        rememberInfiniteTransition(label = "aura")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "auraT").value
    } else 0f
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val ph = 2f * PI.toFloat() * t
        fun blob(color: Color, x: Float, y: Float, r: Float) {
            val raw = Offset(x, y)
            val center = c + (raw - c) * spread
            val a = 0.18f * strength * bloom.value
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = a),
                    0.45f to color.copy(alpha = a * 0.45f),
                    1f to Color.Transparent,
                    center = center, radius = r
                ),
                radius = r, center = center
            )
        }
        blob(ca, w * (0.12f + 0.06f * sin(ph)), h * (0.98f + 0.03f * cos(ph)), w * 1.05f)
        blob(cb, w * (0.92f + 0.05f * cos(ph + 1.7f)), h * (0.62f + 0.05f * sin(ph + 1.7f)), w * 0.95f)
        blob(cc, w * (0.5f + 0.12f * sin(ph + 3.4f)), h * (0.02f + 0.03f * cos(ph + 3.4f)), w * 0.8f)
    }
}

/** 기울기 값(-1~1). 중력 센서로 읽고, 센서가 없으면 아주 천천히 숨 쉬듯 흔들린다. */
private class Tilt { var x by mutableFloatStateOf(0f); var y by mutableFloatStateOf(0f); var rawX = 0f; var rawY = 0f; var hasSensor = false }

@Composable
private fun rememberTilt(enabled: Boolean): Tilt {
    val tilt = remember { Tilt() }
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // 폰을 45°쯤 들고 보는 자세를 0으로
                tilt.rawX = (-e.values[0] / SensorManager.GRAVITY_EARTH * 2.4f).coerceIn(-1f, 1f)
                tilt.rawY = ((e.values[1] / SensorManager.GRAVITY_EARTH) - 0.7f).times(2.4f).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) {
            tilt.hasSensor = true
            sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    LaunchedEffect(enabled) {
        if (!enabled) { tilt.x = 0f; tilt.y = 0f; return@LaunchedEffect }
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                val sec = (now - start) / 1e9f
                val tx = if (tilt.hasSensor) tilt.rawX else sin(sec / 1.6f) * 0.22f
                val ty = if (tilt.hasSensor) tilt.rawY else 0f
                tilt.x += (tx - tilt.x) * 0.09f
                tilt.y += (ty - tilt.y) * 0.09f
            }
        }
    }
    return tilt
}

/** 카드를 끌어내릴 때 사진이 매달린 것처럼 흔들리는 각도(도). */
@Composable
private fun rememberSwing(enabled: Boolean): () -> Float {
    val pull = LocalSheetPull.current
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last = -1L; var prev = pull(); var av = 0f; var a = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.032f)
                last = now
                val p = pull(); val vel = (p - prev) / dt; prev = p
                // 끄는 속도만큼 뒤로 처지고, 놓으면 몇 번 흔들리다 멈춘다(감쇠 약 0.3)
                val acc = -vel * 0.5f - 90f * a - 6f * av
                av += acc * dt; a = (a + av * dt).coerceIn(-14f, 14f)
                angle = a
            }
        }
    }
    return { angle }
}

/**
 * 기기 무대: 알맹이·케이스를 실제 비율로 놓고, 각 제품 바로 아래에 그 배터리 고리.
 * 폰 기울기에 따라 입체로 기울고(앞뒤 층이 살짝 다르게 움직여 깊이감), 표면에 빛 줄기가 지나간다. 누르면 통 튄다.
 */
@Composable
private fun DeviceStage(model: AirPodsBase?, battery: List<Battery>, rich: Boolean, modifier: Modifier) {
    fun level(c: Int) = battery.firstOrNull { it.component == c }?.takeIf { it.status != BatteryStatus.DISCONNECTED }
    fun charging(b: Battery?) = b?.status == BatteryStatus.CHARGING
    val tilt = rememberTilt(rich)
    val swing = rememberSwing(rich)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 등장: 카드가 올라온 뒤 사진이 떠오르고, 빛 줄기가 한 번 쓸고 지나간다
    val appear = remember { Animatable(0f) }
    val sweep = remember { Animatable(-1.2f) }
    LaunchedEffect(model?.name) {
        appear.snapTo(0f)
        launch { appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) }
        if (rich) { sweep.snapTo(-1.2f); sweep.animateTo(1.2f, tween(1300, delayMillis = 380, easing = ControlCenterSpec.ExitEasing)) }
        else sweep.snapTo(1.2f)
    }

    val budsPop = remember { Animatable(1f) }
    val casePop = remember { Animatable(1f) }
    val ringPulse = remember { List(3) { Animatable(1f) } }
    fun pop(item: Animatable<Float, AnimationVector1D>, rings: List<Int>) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        scope.launch { item.animateTo(0.9f, tween(90)); item.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f)) }
        rings.forEach { i ->
            scope.launch { ringPulse[i].animateTo(1.14f, tween(120)); ringPulse[i].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
        }
    }

    // 빛 줄기 위치: 등장 때 한 번 쓸고, 그 뒤로는 기울기를 따라 미끄러진다
    fun sheenPos(): Float = if (sweep.value < 1.2f) sweep.value else tilt.x * 0.9f

    fun Modifier.sheen(): Modifier = this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val x = size.width * (0.5f + sheenPos() * 0.8f)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.38f), 1f to Color.Transparent,
                    start = Offset(x - size.width * 0.35f, 0f), end = Offset(x + size.width * 0.35f, size.height)
                ),
                blendMode = BlendMode.SrcAtop
            )
        }

    @Composable
    fun Cell(b: Battery?, label: String, delayMs: Long, pulse: Animatable<Float, AnimationVector1D>) = Box(
        Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
    ) {
        RingCell(
            b?.level, charging(b), label,
            size = ControlCenterSpec.Ring, stroke = ControlCenterSpec.RingStroke, delayMs = delayMs,
            textColor = ControlCenterSpec.Fg, trackColor = ControlCenterSpec.Track, labelSize = 12,
        )
    }

    val density = LocalDensity.current
    val stageLayer = Modifier.graphicsLayer {
        alpha = appear.value
        val s = 0.94f + 0.06f * appear.value
        scaleX = s; scaleY = s
        translationY = (1f - appear.value) * 8.dp.toPx()
        rotationY = tilt.x * 12f
        rotationX = -tilt.y * 8f
        cameraDistance = 14f * density.density
    }
    fun Modifier.pressable(onTap: () -> Unit) = pointerInput(Unit) { detectTapGestures { onTap() } }

    if (model == null) {
        // 모델을 아직 모르면 작은 에어팟 그림 + 고리 세 개
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.airpods), null, tint = ControlCenterSpec.Fg.copy(alpha = 0.85f), modifier = stageLayer.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Cell(level(BatteryComponent.LEFT), stringResource(R.string.orchard_left), 160, ringPulse[0])
                Cell(level(BatteryComponent.RIGHT), stringResource(R.string.orchard_right), 210, ringPulse[1])
                Cell(level(BatteryComponent.CASE), stringResource(R.string.orchard_case), 260, ringPulse[2])
            }
        }
        return
    }

    val buds = painterResource(model.budsRes)
    if (model.isHeadphones) {
        val b = level(BatteryComponent.LEFT) ?: level(BatteryComponent.RIGHT)
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                buds, model.name, contentScale = ContentScale.Fit,
                modifier = stageLayer.height(ControlCenterSpec.ArtHeight)
                    .graphicsLayer { scaleX = budsPop.value; scaleY = budsPop.value; transformOrigin = TransformOrigin(0.5f, 0f); rotationZ = swing() * 0.5f }
                    .sheen().pressable { pop(budsPop, listOf(0)) }
            )
            Spacer(Modifier.height(12.dp))
            Cell(b, stringResource(if (charging(b)) R.string.orchard_charging else R.string.orchard_battery), 160, ringPulse[0])
        }
        return
    }

    val case = painterResource(model.caseRes)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 28.dp
        val caseAr = case.intrinsicSize.width / case.intrinsicSize.height
        val budsAr = buds.intrinsicSize.width / buds.intrinsicSize.height
        val ratio = budToCaseRatio(model)
        var caseH = ControlCenterSpec.ArtHeight
        val total = caseH * caseAr + caseH * ratio * budsAr + gap
        if (total > maxWidth * 0.86f) caseH = caseH * ((maxWidth * 0.86f - gap) / (total - gap))
        caseH = min(caseH, ControlCenterSpec.ArtHeight)
        val budsH = caseH * ratio
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // 무대 바닥의 은은한 빛(제품이 떠 있지 않고 놓여 보이게)
            Canvas(Modifier.matchParentSize()) {
                val cy = caseH.toPx() + 2.dp.toPx()
                val c = Offset(size.width / 2f, cy)
                // 둥근 빛을 납작하게 눌러 가장자리 없이 번지게(타원을 잘라 그리면 띠처럼 보였음)
                scale(scaleX = 1f, scaleY = 0.14f, pivot = c) {
                    val r = size.width * 0.44f
                    drawCircle(
                        Brush.radialGradient(0f to Color.White.copy(alpha = 0.075f * appear.value), 1f to Color.Transparent, center = c, radius = r),
                        radius = r, center = c
                    )
                }
            }
            Row(stageLayer, verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(caseH), contentAlignment = Alignment.BottomCenter) {
                        Image(
                            buds, model.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(budsH * budsAr, budsH)
                                .graphicsLayer {
                                    scaleX = budsPop.value; scaleY = budsPop.value
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                    rotationZ = swing()
                                    translationX = tilt.x * 4.dp.toPx()   // 앞 층: 조금 더 움직여 깊이감
                                }
                                .sheen()
                                .pressable { pop(budsPop, listOf(0, 1)) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Cell(level(BatteryComponent.LEFT), stringResource(R.string.orchard_left), 160, ringPulse[0])
                        Cell(level(BatteryComponent.RIGHT), stringResource(R.string.orchard_right), 210, ringPulse[1])
                    }
                }
                Spacer(Modifier.width(gap))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(caseH), contentAlignment = Alignment.BottomCenter) {
                        Image(
                            case, null, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(caseH * caseAr, caseH)
                                .graphicsLayer {
                                    scaleX = casePop.value; scaleY = casePop.value
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                    rotationZ = swing() * 0.45f
                                    translationX = -tilt.x * 2.dp.toPx()
                                }
                                .sheen()
                                .pressable { pop(casePop, listOf(2)) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Cell(level(BatteryComponent.CASE), stringResource(R.string.orchard_case), 260, ringPulse[2])
                }
            }
        }
    }
}

/**
 * 가로 볼륨 막대: 누르거나 좌우로 밀어 맞춘다. 미는 동안은 손가락을 바로 따라가고, 한 칸 넘을 때마다 눈금이 톡 빛나며 진동.
 * 끝을 넘어 밀면 막대가 고무줄처럼 늘어나고, 놓으면 출렁 돌아온다.
 */
@Composable
fun VolumeBar(fraction: Float, steps: Int, onFraction: (Float) -> Unit, onDrag: (Boolean) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val rich = LocalRichMotion.current
    var live by remember { mutableFloatStateOf(fraction) }
    var raw by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    val stretch = remember { Animatable(0f) }
    val n = steps.coerceIn(1, 30)
    var lastStep by remember { mutableIntStateOf((fraction * n).roundToInt()) }
    val flash = remember { Animatable(0f) }
    var flashAt by remember { mutableIntStateOf(-1) }
    val shown = if (dragging) live else fraction

    fun userSet(f: Float) {
        live = f.coerceIn(0f, 1f)
        onFraction(live)
        val s = (live * n).roundToInt()
        if (s != lastStep) {
            lastStep = s
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (rich) { flashAt = s; scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(260)) } }
        }
    }

    val shape = RoundedCornerShape(ControlCenterSpec.Control / 2)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ControlCenterSpec.Control)
            .graphicsLayer {
                val s = stretch.value
                scaleX = 1f + abs(s)
                scaleY = 1f - abs(s) * 0.5f
                transformOrigin = if (s >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
            }
            .clip(shape)
            .background(ControlCenterSpec.Fill)
            .pointerInput(Unit) {
                detectTapGestures { o -> userSet(o.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> raw = o.x / size.width; dragging = true; onDrag(true); userSet(raw) },
                    onDragEnd = {
                        dragging = false; onDrag(false)
                        scope.launch { stretch.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
                    },
                    onDragCancel = {
                        dragging = false; onDrag(false)
                        scope.launch { stretch.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        raw += dx / size.width
                        userSet(raw)
                        // 끝을 넘은 만큼 고무줄처럼(점점 덜 늘어나게)
                        val over = when { raw > 1f -> raw - 1f; raw < 0f -> raw; else -> 0f }
                        val target = if (rich) (over * 0.22f / (1f + abs(over) * 2f)).coerceIn(-0.07f, 0.07f) else 0f
                        scope.launch { stretch.snapTo(target) }
                    }
                )
            }
    ) {
        val f = shown.coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val fillW = w * f
            drawRect(ControlCenterSpec.Fg, size = Size(fillW, h))
            // 눈금: 빈 쪽은 옅은 흰 점, 찬 쪽은 옅은 검은 점. 방금 넘은 칸은 크게 빛난다
            val pad = h / 2f
            val tickW = 2.dp.toPx()
            for (i in 1 until n) {
                val x = pad + (w - 2 * pad) * i / n
                val on = x < fillW
                val hit = i == flashAt && flash.value > 0f
                val th = (if (hit) 6.dp.toPx() + 8.dp.toPx() * flash.value else 6.dp.toPx())
                val base = if (on) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.16f)
                val c = if (hit) (if (on) Color.Black else Color.White).copy(alpha = 0.16f + 0.7f * flash.value) else base
                drawRoundRect(c, Offset(x - tickW / 2, (h - th) / 2), Size(tickW, th), CornerRadius(tickW / 2))
            }
        }
        // 스피커: 소리가 클수록 물결이 늘어난다
        val speaker = when {
            f <= 0.001f -> R.drawable.orchard_volume_0
            f < 0.5f -> R.drawable.orchard_volume_1
            else -> R.drawable.orchard_volume
        }
        Crossfade(speaker, animationSpec = tween(160), label = "speaker", modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = stringResource(R.string.orchard_volume),
                // 흰 채움이 아이콘 가운데(왼쪽 끝에서 27dp)를 넘었으면 검정, 아니면 흰색 — 볼륨이 아주 낮을 때 흰 위 흰으로 사라지던 것 고침
                tint = if (maxWidth * f > 27.dp) Color.Black else ControlCenterSpec.Fg,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 모드 고르기: 알약 칸 안에 선택 원이 딱 맞게 들어가 스프링으로 건너가고, 건너가는 동안 속도만큼 물방울처럼 늘어난다.
 * 적응형은 무지개, 끔은 회색, 나머지는 파랑 — 색은 원이 움직이는 동안 섞여 바뀐다. 이름은 알약 아래 한 줄.
 */
@Composable
fun ModePicker(
    modes: List<NoiseControlMode>,
    selected: NoiseControlMode,
    onSelect: (NoiseControlMode) -> Unit,
    onSlotCenters: (List<Offset>) -> Unit = {},
) {
    val view = LocalView.current
    val rich = LocalRichMotion.current
    val index = modes.indexOf(selected).coerceAtLeast(0)
    val count = modes.size.coerceAtLeast(1)
    val adaptive by animateFloatAsState(if (selected == NoiseControlMode.ADAPTIVE) 1f else 0f, tween(260), label = "rainbow")
    val solid by animateColorAsState(if (selected == NoiseControlMode.OFF) ControlCenterSpec.Gray else ControlCenterSpec.Blue, tween(260), label = "indColor")
    val pos = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) { pos.animateTo(index.toFloat(), spring(dampingRatio = 0.7f, stiffness = 480f)) }
    val centers = remember { arrayOfNulls<Offset>(8) }

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(ControlCenterSpec.Control)
                .clip(RoundedCornerShape(ControlCenterSpec.Control / 2))
                .background(ControlCenterSpec.Fill)
        ) {
            val inset = (ControlCenterSpec.Control - ControlCenterSpec.Dot) / 2
            Canvas(Modifier.fillMaxSize()) {
                val slot = (size.width - 2 * inset.toPx()) / count
                val d = ControlCenterSpec.Dot.toPx()
                val cx = inset.toPx() + slot * (pos.value + 0.5f)
                val cy = size.height / 2f
                // 물방울: 빠를수록 가로로 늘고 세로로 눌린다
                val st = if (rich) (abs(pos.velocity) * 0.07f).coerceAtMost(0.42f) else 0f
                val w = d * (1f + st); val h = d * (1f - st * 0.38f)
                val tl = Offset(cx - w / 2f, cy - h / 2f)
                drawRoundRect(solid.copy(alpha = 1f - adaptive), tl, Size(w, h), CornerRadius(h / 2f))
                if (adaptive > 0f) {
                    drawRoundRect(
                        Brush.sweepGradient(RainbowStops, center = Offset(cx, cy)),
                        tl, Size(w, h), CornerRadius(h / 2f), alpha = adaptive
                    )
                }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = inset)) {
                modes.forEachIndexed { i, m ->
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
                    val a by animateFloatAsState(if (i == index) 1f else 0.6f, tween(200), label = "iconAlpha")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { lc ->
                                val p = lc.positionInRoot()
                                centers[i] = Offset(p.x + lc.size.width / 2f, p.y + lc.size.height / 2f)
                                if (i == count - 1) onSlotCenters(centers.take(count).map { it ?: Offset.Zero })
                            }
                            .clickable(interactionSource = src, indication = null) {
                                if (m != selected) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onSelect(m)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(modeIcon(m)),
                            contentDescription = stringResource(modeLabel(m)),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = (ControlCenterSpec.Control - ControlCenterSpec.Dot) / 2, end = (ControlCenterSpec.Control - ControlCenterSpec.Dot) / 2)) {
            modes.forEachIndexed { i, m ->
                val on = i == index
                val c by animateColorAsState(if (on) ControlCenterSpec.Fg else ControlCenterSpec.Fg2, tween(200), label = "label")
                Text(
                    text = stringResource(modeLabel(m)),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontFamily = OrchardFont,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = c
                    )
                )
            }
        }
    }
}

/** 적응형 무지개(원래 것보다 맑게) — 물방울 모양 위치를 따라가도록 중심을 매번 정한다. */
private val RainbowStops = listOf(
    Color(0xFFFF5E7E), Color(0xFFFFA24C), Color(0xFFFFD84D), Color(0xFF4FD67A),
    Color(0xFF52C8F5), Color(0xFF6E6BF0), Color(0xFFC06BF0), Color(0xFFFF5E7E)
)

fun modeIcon(mode: NoiseControlMode): Int = when (mode) {
    NoiseControlMode.OFF -> R.drawable.orchard_mode_off
    NoiseControlMode.TRANSPARENCY -> R.drawable.transparency
    NoiseControlMode.ADAPTIVE -> R.drawable.adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.drawable.noise_cancellation
}

fun modeLabel(mode: NoiseControlMode): Int = when (mode) {
    NoiseControlMode.OFF -> R.string.orchard_mode_off
    NoiseControlMode.TRANSPARENCY -> R.string.orchard_mode_transparency
    NoiseControlMode.ADAPTIVE -> R.string.orchard_mode_adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.string.orchard_mode_anc
}

fun statusLabel(mode: NoiseControlMode): Int = when (mode) {
    NoiseControlMode.OFF -> R.string.orchard_connected
    NoiseControlMode.TRANSPARENCY -> R.string.orchard_cc_status_transparency
    NoiseControlMode.ADAPTIVE -> R.string.orchard_cc_status_adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.string.orchard_cc_status_anc
}
