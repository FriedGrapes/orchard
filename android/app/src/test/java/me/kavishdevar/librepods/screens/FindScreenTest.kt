package me.kavishdevar.librepods.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.find.FindScreen
import me.kavishdevar.librepods.find.LastSeen
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w411dp-h1400dp-xxhdpi")
class FindScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun find() {
        LastSeen.setForPreview(
            LastSeen.Place(37.5665, 126.978, 18f, System.currentTimeMillis() - 12 * 60_000, "규민 팟프로")
        )
        LastSeen.onRssi(-58)
        compose.mainClock.autoAdvance = false
        compose.setContent { LibrePodsTheme(m3eEnabled = false) { FindScreen() } }
        compose.mainClock.advanceTimeBy(900)
        compose.onRoot().captureRoboImage("build/screenshots/find.png")
    }
}
