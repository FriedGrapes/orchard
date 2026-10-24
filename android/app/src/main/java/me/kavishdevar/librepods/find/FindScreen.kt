package me.kavishdevar.librepods.find

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.theme.OrchardFont

private val Green = Color(0xFF34C759)

/** 한글이 단어 중간에서 끊기지 않게(어절 단위 줄바꿈). */
private val KoreanBreak = androidx.compose.ui.text.style.LineBreak.Paragraph.copy(
    wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase
)

/** Orchard: 에어팟 찾기 — 마지막으로 함께 있던 곳 + 근처에서 신호 세기로 찾기 + 추적 경고 안내. */
@Composable
fun FindScreen() {
    val context = LocalContext.current
    val place by LastSeen.place.collectAsState()
    val rssi by LastSeen.rssi.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var granted by remember { mutableStateOf(LastSeen.hasPermission(context)) }
    var always by remember { mutableStateOf(LastSeen.hasBackgroundPermission(context)) }

    LaunchedEffect(Unit) {
        LastSeen.load(context)
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    val askForeground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = LastSeen.hasPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1) 마지막으로 함께 있던 곳
        Card {
            Title("마지막으로 함께 있던 곳")
            val p = place
            if (p == null) {
                Body("아직 기록이 없어요. 에어팟 연결이 끊길 때마다 이 폰의 위치를 기억해 둬요.")
            } else {
                Text(
                    FindText.ago(now, p.time),
                    style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                )
                Body("${p.name} · 정확도 약 ${p.accuracy.toInt()}m")
                Spacer(Modifier.height(4.dp))
                Pill("지도에서 보기") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FindText.geoUri(p.lat, p.lon, p.name))))
                }
            }
            if (!granted || !always) {
                Spacer(Modifier.height(4.dp))
                Body(
                    if (!granted) "위치 권한이 있어야 기억할 수 있어요."
                    else "앱을 닫아 둬도 기억하려면 위치를 '항상 허용'으로 바꿔 주세요."
                )
                Pill(if (!granted) "위치 권한 허용" else "'항상 허용'으로 바꾸기") {
                    if (!granted) {
                        askForeground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        always = LastSeen.hasBackgroundPermission(context)
                    }
                }
            }
        }

        // 2) 근처에서 찾기
        Card {
            Title("근처에서 찾기")
            val fresh = rssi?.takeIf { now - it.second < 8_000 }?.first
            ProximityMeter(Proximity.of(fresh))
            Body(
                "에어팟이 케이스 밖에 있거나 뚜껑이 열려 있을 때 신호가 잡혀요. 폰을 들고 움직이며 원이 커지는 쪽으로 가 보세요.",
                center = true,
            )
        }

        // 3) 추적 경고 안내
        Card {
            Title("'모르는 추적기' 경고가 뜬다면")
            Body(
                "에어팟이 주인의 애플 기기와 오래 떨어져 있으면 주변 사람의 휴대폰에 경고가 갈 수 있어요. " +
                    "나의 찾기에서 에어팟을 빼면 경고가 멈춰요(대신 애플 나의 찾기로는 위치를 볼 수 없어요)."
            )
            Step(1, "에어팟을 케이스에 넣고 닫아요(맥스는 스마트 케이스에).")
            Step(2, "이 폰 브라우저에서 icloud.com/find 에 로그인해요.")
            Step(3, "'모든 기기'에서 에어팟을 고르고 '이 기기 제거'를 눌러요.")
            Step(4, "에어팟 맥스도 같은 방법으로 빼요.")
            Body("맥에서 다시 페어링하면 나의 찾기에 다시 등록될 수 있어요. 그때는 같은 방법으로 다시 빼 주세요.")
            Pill("icloud.com/find 열기") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.icloud.com/find")))
            }
        }
    }
}

@Composable
private fun ProximityMeter(p: Proximity) {
    val target = when (p) {
        Proximity.RightHere -> 1f
        Proximity.Close -> 0.78f
        Proximity.Near -> 0.55f
        Proximity.Far -> 0.35f
        Proximity.None -> 0.2f
    }
    val size by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "size")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "wave")
    val color = if (p == Proximity.None) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f) else Green
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val r = this.size.minDimension / 2
            if (p != Proximity.None) {
                drawCircle(color.copy(alpha = (1f - wave) * 0.35f), radius = r * size * (0.6f + 0.4f * wave), style = Stroke(3.dp.toPx()))
            }
            drawCircle(color.copy(alpha = 0.16f), radius = r * size)
            drawCircle(color, radius = r * size * 0.42f)
        }
        Text(
            when (p) {
                Proximity.RightHere -> "바로 옆"
                Proximity.Close -> "가까워요"
                Proximity.Near -> "조금 멀어요"
                Proximity.Far -> "멀어요"
                Proximity.None -> "신호 없음"
            },
            style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (p == Proximity.None) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.White),
            modifier = Modifier.scale(if (p == Proximity.None) 1f else 0.9f + 0.1f * size),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = Text(
    text, style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
)

@Composable
private fun Body(text: String, center: Boolean = false) = Text(
    text, textAlign = if (center) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth(),
    style = TextStyle(fontFamily = OrchardFont, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface,
        lineBreak = KoreanBreak)
)

@Composable
private fun Step(n: Int, text: String) = Text(
    "$n.  $text",
    style = TextStyle(fontFamily = OrchardFont, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface,
        lineBreak = KoreanBreak)
)

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    // 눌림 반응: 살짝 작아졌다가 돌아온다
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(140), label = "press")
    Box(
        Modifier
            .scale(press)
            .clip(RoundedCornerShape(50))
            .background(Green.copy(alpha = 0.14f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, style = TextStyle(fontFamily = OrchardFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF1E9E47)))
    }
}
