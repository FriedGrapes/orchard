package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.presentation.components.RingCell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard: 충전 중 링의 빛 흐름을 시간대별로 찍는다(검정 카드 위). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChargeGlowFramesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun frames() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color.Black).padding(16.dp)) {
                Row {
                    RingCell(62, true, "케이스", size = 104.dp, stroke = 9.dp, textColor = Color.White, trackColor = Color.White.copy(alpha = 0.14f))
                    RingCell(85, false, "왼쪽", size = 104.dp, stroke = 9.dp, textColor = Color.White, trackColor = Color.White.copy(alpha = 0.14f))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500) // 링이 다 차오른 뒤
        var now = 0L
        for (t in listOf(0L, 300L, 600L, 900L, 1200L, 1500L, 1800L, 2100L)) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            compose.onRoot().captureRoboImage("build/screenshots/glow/glow_%04d.png".format(t))
        }
    }
}
