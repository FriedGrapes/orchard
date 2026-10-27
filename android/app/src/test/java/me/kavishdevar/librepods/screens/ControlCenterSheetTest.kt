package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.ControlCenterSheetHost
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.components.ControlCenterSheetContent
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard v0.2.4~0.2.5: ANC 타일 제어 센터 카드(시안 A + 킥) — 올라오기·모드 바꾸기(물결·빛 알갱이·모드 빛)·배터리 바뀜을 시간대별로 찍는다(뒤는 일부러 글자 많은 앱). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
class ControlCenterSheetTest {
    @get:Rule val compose = createComposeRule()

    private val modes = listOf(NoiseControlMode.OFF, NoiseControlMode.TRANSPARENCY, NoiseControlMode.ADAPTIVE, NoiseControlMode.NOISE_CANCELLATION)

    private fun run(tag: String, qualifiersNote: String = "") {
        compose.mainClock.autoAdvance = false
        var mode by mutableStateOf(NoiseControlMode.ADAPTIVE)
        var vol by mutableFloatStateOf(0.46f)
        var battery by mutableStateOf(
            listOf(
                Battery(BatteryComponent.LEFT, 82, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 79, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 64, BatteryStatus.CHARGING),
            )
        )
        compose.setContent {
            LibrePodsTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(12) {
                            Box(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF1B1B1E), RoundedCornerShape(18.dp)).padding(16.dp)) {
                                Text("Orchard 0.2.3 배포를 마쳤어요 · Hearth 쉬는 중", color = Color(0xFF9A9AA0), fontSize = 15.sp)
                            }
                        }
                    }
                    ControlCenterSheetHost(onDismiss = {}) {
                        ControlCenterSheetContent(
                            name = "AirPods Pro",
                            model = AirPodsModels.getModelByModelNumber("A3048"),
                            battery = battery,
                            volumeFraction = vol,
                            volumeSteps = 15,
                            onVolumeFraction = { vol = it },
                            onVolumeDrag = {},
                            modes = modes,
                            selected = mode,
                            onSelect = { mode = it },
                        )
                    }
                }
            }
        }
        var now = 0L
        for (t in listOf(0L, 60L, 120L, 200L, 320L, 1500L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/cc/${tag}_in_%04d.png".format(t))
        }
        mode = NoiseControlMode.NOISE_CANCELLATION
        now = 0L
        for (t in listOf(60L, 140L, 260L, 800L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/cc/${tag}_anc_%04d.png".format(t))
        }
        mode = NoiseControlMode.TRANSPARENCY
        now = 0L
        for (t in listOf(200L, 500L, 900L, 1800L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/cc/${tag}_tra_%04d.png".format(t))
        }
        mode = NoiseControlMode.OFF
        battery = listOf(
            Battery(BatteryComponent.LEFT, 15, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.RIGHT, 79, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 64, BatteryStatus.NOT_CHARGING),
        )
        vol = 0.08f
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/screenshots/cc/${tag}_off_low.png")
    }

    @Test fun pro2() = run("pro2")
}
