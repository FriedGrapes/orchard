package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.components.ControlCenterNoiseControlSegmentedButton
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard: ANC 타일을 꾹 누르면 펼쳐지는 모드 고르기(제어 센터 모양)를 찍는다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
class TileModeSliderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modes() {
        compose.mainClock.autoAdvance = false
        var mode by mutableStateOf(NoiseControlMode.TRANSPARENCY)
        compose.setContent {
            LibrePodsTheme {
                Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2A2F36)), contentAlignment = Alignment.Center) {
                    ControlCenterNoiseControlSegmentedButton(
                        availableModes = remember { listOf(NoiseControlMode.OFF, NoiseControlMode.TRANSPARENCY, NoiseControlMode.ADAPTIVE, NoiseControlMode.NOISE_CANCELLATION) },
                        selectedMode = mode,
                        onModeSelected = { mode = it },
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("build/screenshots/tile/modes_0.png")
        mode = NoiseControlMode.NOISE_CANCELLATION
        var now = 0L
        for (t in listOf(60L, 140L, 600L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/tile/modes_anc_%04d.png".format(t))
        }
    }
}
