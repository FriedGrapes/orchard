package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.theme.OrchardFont
import kotlin.math.roundToInt

/**
 * Orchard: 메인 화면 맨 위 기기 카드. 아이폰 에어팟 팝업처럼 큰 그림과 링 배터리를 보여 준다.
 * 헤드폰(맥스)은 배터리가 하나라 큰 링 하나만 그린다.
 */
@Composable
fun DeviceHeroCard(
    name: String,
    model: AirPodsBase?,
    battery: List<Battery>,
    connected: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isHeadphones = model?.isHeadphones == true
    fun level(c: Int) = battery.firstOrNull { it.component == c }
        ?.takeIf { it.status != BatteryStatus.DISCONNECTED }
    fun charging(b: Battery?) = b?.status == BatteryStatus.CHARGING

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)
    ) {
        // 머리말: 이름 + 상태
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = OrchardFont, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (model != null && model.name != name) {
                    Text(
                        text = model.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = OrchardFont, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    )
                }
            }
            StatusChip(connected)
        }

        // 기기 그림 — 처음 보일 때 살짝 떠오르며 나타난다
        val appear = remember { Animatable(0f) }
        LaunchedEffect(model?.name) {
            appear.snapTo(0f)
            appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isHeadphones) 176.dp else 156.dp)
                .padding(vertical = 10.dp)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s; scaleY = s
                    translationY = (1f - appear.value) * 12.dp.toPx()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val buds = model?.budsRes ?: R.drawable.airpods_pro_2_buds
            Image(
                painter = painterResource(buds),
                contentDescription = model?.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(if (isHeadphones) 1f else 1.15f)
            )
            if (!isHeadphones) {
                Spacer(Modifier.width(8.dp))
                Image(
                    painter = painterResource(model?.caseRes ?: R.drawable.airpods_pro_2_case),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                )
            }
        }

        // 배터리 링
        if (isHeadphones) {
            val b = level(BatteryComponent.LEFT) ?: level(BatteryComponent.RIGHT)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingCell(
                    level = b?.level,
                    charging = charging(b),
                    label = stringResource(if (charging(b)) R.string.orchard_charging else R.string.orchard_battery),
                    size = 76.dp,
                    stroke = 7.dp,
                    delayMs = 120,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val l = level(BatteryComponent.LEFT)
                val r = level(BatteryComponent.RIGHT)
                val c = level(BatteryComponent.CASE)
                RingCell(l?.level, charging(l), stringResource(R.string.orchard_left), delayMs = 120)
                RingCell(r?.level, charging(r), stringResource(R.string.orchard_right), delayMs = 180)
                RingCell(c?.level, charging(c), stringResource(R.string.orchard_case), delayMs = 240)
            }
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    val green = Color(0xFF34C759)
    val fg = if (connected) green else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (connected) R.string.orchard_connected else R.string.orchard_nearby),
            style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = fg)
        )
    }
}

/** 링 하나 + 가운데 % + 아래 이름. 값이 바뀌면 스프링으로 차오른다. */
@Composable
fun RingCell(
    level: Int?,
    charging: Boolean,
    label: String,
    size: Dp = 60.dp,
    stroke: Dp = 5.5.dp,
    delayMs: Long = 0,
) {
    val dark = isSystemInDarkTheme()
    val target = (level ?: 0).coerceIn(0, 100) / 100f
    // 처음 보일 때는 0에서 차오르고(링마다 조금씩 늦게), 그 뒤로는 값이 바뀔 때마다 부드럽게 따라간다
    val ring = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        if (first) { delay(delayMs); first = false }
        ring.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow))
    }
    val sweep = ring.value
    val known by animateFloatAsState(if (level != null) 1f else 0f, tween(250), label = "known")
    val color = when {
        charging || target > 0.2f -> Color(0xFF34C759)
        target > 0.1f -> Color(0xFFFFCC00)
        else -> Color(0xFFFF3B30)
    }
    val track = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = stroke.toPx()
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
                if (sweep > 0.001f) {
                    drawArc(
                        color.copy(alpha = known), -90f, 360f * sweep, false, topLeft, arcSize,
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
            }
            // 링이 차오르는 동안 숫자도 함께 굴러간다
            val shown = (sweep * 100).roundToInt()
            Text(
                text = when {
                    level == null -> "–"
                    charging -> "⚡$shown"
                    else -> "$shown"
                },
                style = TextStyle(
                    fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold,
                    fontSize = if (size > 70.dp) 18.sp else 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = OrchardFont, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
    }
}
