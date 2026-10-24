package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.data.AirPodsMax2
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.components.DeviceHeroCard
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard: 기기 카드 등장·링 차오름을 시간대별로 찍는다(움직임 확인용 프레임 띠). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HeroFramesTest {
    @get:Rule val compose = createComposeRule()

    private fun frames(tag: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) { content() }
            }
        }
        var now = 0L
        for (t in listOf(0L, 80L, 160L, 320L, 600L, 1200L, 2500L)) {
            compose.mainClock.advanceTimeBy(t - now)
            now = t
            compose.onRoot().captureRoboImage("build/screenshots/frames/${tag}_%04d.png".format(t))
        }
    }

    @Test fun pro3() = frames("pro3") {
        DeviceHeroCard(
            name = "규민 팟프로",
            model = AirPodsModels.getModelByModelNumber("A3064"),
            battery = listOf(
                Battery(BatteryComponent.LEFT, 80, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 18, BatteryStatus.CHARGING),
                Battery(BatteryComponent.CASE, 76, BatteryStatus.NOT_CHARGING),
            ),
        )
    }

    @Test fun max2() = frames("max2") {
        DeviceHeroCard(
            name = "AirPods Max",
            model = AirPodsMax2(),
            battery = listOf(
                Battery(BatteryComponent.LEFT, 64, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 64, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED),
            ),
        )
    }
}
