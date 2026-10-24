package me.kavishdevar.librepods.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.data.AirPodsMax2
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.overlays.LidCardContent
import me.kavishdevar.librepods.presentation.overlays.LidCardSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard: 뚜껑 카드가 위에서 내려오고 올라가는 모습을 시간대별로 찍는다(배경은 밝은 홈 화면 흉내). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LidCardTest {
    @get:Rule val compose = createComposeRule()

    private val pods = listOf(
        Battery(BatteryComponent.LEFT, 85, BatteryStatus.NOT_CHARGING),
        Battery(BatteryComponent.RIGHT, 85, BatteryStatus.NOT_CHARGING),
        Battery(BatteryComponent.CASE, 62, BatteryStatus.CHARGING),
    )

    private fun frames(tag: String, card: @Composable (Modifier) -> Unit) {
        compose.mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        compose.setContent {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(visible) {
                if (visible) progress.animateTo(1f, spring(LidCardSpec.EnterDamping, LidCardSpec.EnterStiffness))
                else progress.animateTo(0f, tween(LidCardSpec.ExitMs, easing = LidCardSpec.ExitEasing))
            }
            Box(Modifier.fillMaxWidth().height(420.dp).background(Color(0xFFDCE3EA)), contentAlignment = Alignment.TopCenter) {
                card(
                    Modifier.padding(top = 28.dp, start = 10.dp, end = 10.dp).graphicsLayer {
                        val p = progress.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = (1f - p) * -(300.dp.toPx())
                        val s = 0.9f + 0.1f * p
                        scaleX = s; scaleY = s
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                    }
                )
            }
        }
        var now = 0L
        for (t in listOf(0L, 60L, 120L, 200L, 320L, 480L, 1400L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/lid/${tag}_in_%04d.png".format(t))
        }
        visible = false
        now = 0L
        for (t in listOf(60L, 140L, 220L, 300L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/lid/${tag}_out_%04d.png".format(t))
        }
    }

    @Test fun pods5() = frames("pods5") { m ->
        LidCardContent("규민의 AirPods", AirPodsModels.getModelByModelNumber("A3440"), pods, {}, m)
    }

    @Test fun max2() = frames("max2") { m ->
        LidCardContent(
            "AirPods Max", AirPodsMax2(),
            listOf(Battery(BatteryComponent.LEFT, 64, BatteryStatus.NOT_CHARGING), Battery(BatteryComponent.RIGHT, 64, BatteryStatus.NOT_CHARGING)),
            {}, m
        )
    }
}
