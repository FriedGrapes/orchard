package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.theme.OrchardFont
import me.kavishdevar.librepods.data.NoiseControlMode

/**
 * Orchard: 빠른 설정 ANC 타일을 꾹 누르면 아래에서 올라오는 제어 센터 카드(시안 A '애플 카드 시트', v0.2.4).
 * 뚜껑 카드(LidCard)와 같은 순검정·연속 곡률 모서리. 이름 → 기기 사진 → 배터리 고리 → 볼륨 → 모드 순서.
 */
object ControlCenterSpec {
    val Radius = 38.dp
    val Margin = 10.dp
    val Width = 440.dp
    val PadH = 20.dp
    val ArtHeight = 112.dp
    val Ring = 50.dp
    val RingStroke = 4.dp
    val VolumeHeight = 46.dp
    val SegHeight = 64.dp
    val SegPad = 4.dp
    val Bg = Color(0xFF000000)
    val Fg = Color(0xFFFFFFFF)
    val Fg2 = Color(0xFF8E8E93)
    val Fill = Color(0xFF1C1C1E)
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
) {
    val shape = ContinuousRoundedShape(ControlCenterSpec.Radius)
    val isHeadphones = model?.isHeadphones == true
    fun level(c: Int) = battery.firstOrNull { it.component == c }?.takeIf { it.status != BatteryStatus.DISCONNECTED }
    fun charging(b: Battery?) = b?.status == BatteryStatus.CHARGING

    Column(
        modifier
            .widthIn(max = ControlCenterSpec.Width)
            .fillMaxWidth()
            .shadow(32.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(ControlCenterSpec.Bg)
            .border(0.5.dp, ControlCenterSpec.Hairline, shape)
            // 카드 안을 누르면 닫히지 않게 여기서 받는다(바깥 누름만 닫기)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = ControlCenterSpec.PadH, end = ControlCenterSpec.PadH, bottom = 22.dp),
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
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp),
            style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = ControlCenterSpec.Fg)
        )
        Text(
            text = stringResource(R.string.orchard_connected),
            style = TextStyle(fontFamily = OrchardFont, fontSize = 12.5.sp, color = ControlCenterSpec.Fg2),
            modifier = Modifier.padding(top = 1.dp)
        )

        // 기기 사진 — 카드가 올라온 뒤 살짝 떠오르며
        val appear = remember { Animatable(0f) }
        LaunchedEffect(model?.name) {
            appear.snapTo(0f)
            appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        }
        val artMod = Modifier
            .padding(top = 8.dp)
            .graphicsLayer {
                alpha = appear.value
                val s = 0.94f + 0.06f * appear.value
                scaleX = s; scaleY = s
                translationY = (1f - appear.value) * 8.dp.toPx()
            }
        if (model != null) {
            DeviceArtRow(model = model, height = ControlCenterSpec.ArtHeight, modifier = artMod)
        } else {
            // 모델을 아직 모르면 빈 자리 대신 작은 에어팟 그림
            Box(artMod.height(64.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.airpods), null, tint = ControlCenterSpec.Fg.copy(alpha = 0.85f), modifier = Modifier.size(52.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        @Composable
        fun Cell(b: Battery?, label: String, delayMs: Long) = RingCell(
            b?.level, charging(b), label,
            size = ControlCenterSpec.Ring, stroke = ControlCenterSpec.RingStroke, delayMs = delayMs,
            textColor = ControlCenterSpec.Fg, trackColor = ControlCenterSpec.Track, labelSize = 12,
        )
        if (isHeadphones) {
            val b = level(BatteryComponent.LEFT) ?: level(BatteryComponent.RIGHT)
            Cell(b, stringResource(if (charging(b)) R.string.orchard_charging else R.string.orchard_battery), 160)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Cell(level(BatteryComponent.LEFT), stringResource(R.string.orchard_left), 160)
                Cell(level(BatteryComponent.RIGHT), stringResource(R.string.orchard_right), 210)
                Cell(level(BatteryComponent.CASE), stringResource(R.string.orchard_case), 260)
            }
        }

        Box(Modifier.padding(top = 18.dp, bottom = 16.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))

        VolumeBar(volumeFraction, onVolumeFraction, onVolumeDrag)
        Spacer(Modifier.height(16.dp))
        ModePicker(modes, selected, onSelect)
    }
}

/** 가로 볼륨 막대: 누르거나 좌우로 밀어 맞춘다. 미는 동안은 손가락을 바로 따라가고, 놓으면 실제 볼륨 칸에 스프링으로 맞춰진다. */
@Composable
fun VolumeBar(fraction: Float, onFraction: (Float) -> Unit, onDrag: (Boolean) -> Unit) {
    var live by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) live else fraction
    val pressScale by animateFloatAsState(if (dragging) 1.02f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "volPress")
    val shape = RoundedCornerShape(ControlCenterSpec.VolumeHeight / 2)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ControlCenterSpec.VolumeHeight)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(shape)
            .background(ControlCenterSpec.Fill)
            .pointerInput(Unit) {
                detectTapGestures { o -> onFraction((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> live = (o.x / size.width).coerceIn(0f, 1f); dragging = true; onDrag(true); onFraction(live) },
                    onDragEnd = { dragging = false; onDrag(false) },
                    onDragCancel = { dragging = false; onDrag(false) },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        live = (live + dx / size.width).coerceIn(0f, 1f)
                        onFraction(live)
                    }
                )
            }
    ) {
        Box(Modifier.fillMaxHeight().width(maxWidth * shown.coerceIn(0f, 1f)).background(ControlCenterSpec.Fg))
        Icon(
            painter = painterResource(R.drawable.orchard_volume),
            contentDescription = stringResource(R.string.orchard_volume),
            // 흰 채움 위에서는 검정, 비어 있으면 흰색
            tint = if (shown > 0.12f) Color.Black else ControlCenterSpec.Fg,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(22.dp)
        )
    }
}

/**
 * 모드 고르기: 알약 칸 안에 선택 원이 딱 맞게 들어가 스프링으로 미끄러지고, 이름은 알약 아래 한 줄.
 * 적응형은 무지개, 끔은 회색, 나머지는 파랑 — 색은 원이 움직이는 동안 섞여 바뀐다.
 */
@Composable
fun ModePicker(modes: List<NoiseControlMode>, selected: NoiseControlMode, onSelect: (NoiseControlMode) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val index = modes.indexOf(selected).coerceAtLeast(0)
    val count = modes.size.coerceAtLeast(1)
    val adaptive by animateFloatAsState(if (selected == NoiseControlMode.ADAPTIVE) 1f else 0f, tween(260), label = "rainbow")
    val solid by animateColorAsState(if (selected == NoiseControlMode.OFF) ControlCenterSpec.Gray else ControlCenterSpec.Blue, tween(260), label = "indColor")

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(ControlCenterSpec.SegHeight)
                .clip(RoundedCornerShape(ControlCenterSpec.SegHeight / 2))
                .background(ControlCenterSpec.Fill)
                .padding(ControlCenterSpec.SegPad)
        ) {
            val slot = maxWidth / count
            val dot = maxHeight
            val x by animateDpAsState(
                slot * index + (slot - dot) / 2,
                spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "indX"
            )
            Canvas(Modifier.offset(x = x).size(dot)) {
                drawCircle(solid.copy(alpha = 1f - adaptive))
                if (adaptive > 0f) drawCircle(AdaptiveRainbowBrush, alpha = adaptive)
            }
            Row(Modifier.fillMaxSize()) {
                modes.forEachIndexed { i, m ->
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
                    val a by animateFloatAsState(if (i == index) 1f else 0.62f, tween(200), label = "iconAlpha")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = src, indication = null) {
                                if (m != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(m)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(modeIcon(m)),
                            contentDescription = stringResource(modeLabel(m)),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = ControlCenterSpec.SegPad, end = ControlCenterSpec.SegPad)) {
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
