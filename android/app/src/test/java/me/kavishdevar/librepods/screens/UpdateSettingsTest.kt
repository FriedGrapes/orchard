package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.update.AppUpdater
import me.kavishdevar.librepods.update.UpdateSettingsSection
import me.kavishdevar.librepods.update.UpdateStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w411dp-h900dp-xxhdpi")
class UpdateSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 네_가지_상태() {
        compose.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                Column(
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateSettingsSection(UpdateStatus.Idle)
                    UpdateSettingsSection(UpdateStatus.Checking)
                    UpdateSettingsSection(UpdateStatus.Done(AppUpdater.CheckResult.UpToDate("0.1.2")))
                    UpdateSettingsSection(UpdateStatus.Done(AppUpdater.CheckResult.Installing("0.1.3")))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/update_settings.png")
    }

    @Test fun 누르면_결과가_바뀐다() {
        compose.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                UpdateSettingsSection(check = { AppUpdater.CheckResult.UpToDate("0.1.2") })
            }
        }
        compose.onNodeWithText("지금 업데이트 확인").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("최신 버전이에요 (v0.1.2)").assertExists()
    }
}
