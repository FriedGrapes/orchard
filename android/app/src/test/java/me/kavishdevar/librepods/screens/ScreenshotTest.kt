package me.kavishdevar.librepods.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.data.AirPodsMax2
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.screens.AirPodsSettingsScreen
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState
import me.kavishdevar.librepods.presentation.viewmodel.demoInstance
import me.kavishdevar.librepods.presentation.viewmodel.demoState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Orchard: 화면을 PC에서 그림으로 그려 build/screenshots 에 저장한다(폰 없이 화면 확인용).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private fun shot(name: String, state: AirPodsUiState) {
        captureRoboImage("build/screenshots/$name.png") {
            LibrePodsTheme(m3eEnabled = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    AirPodsSettingsScreen(
                        state = state,
                        setControlCommandInt = { _, _ -> }, setControlCommandBoolean = { _, _ -> },
                        setControlCommandByte = { _, _ -> }, setATTCharacteristicValue = { _, _ -> },
                        onAutomaticEarDetectionChanged = {}, onAutomaticConnectionChanged = {},
                        setDynamicEndOfCharge = {}, setOffListeningMode = {}, disconnect = {},
                        navigateToRename = {}, navigateToHearingProtection = {}, navigateToHearingAid = {},
                        navigateToLeftLongPress = {}, navigateToRightLongPress = {}, navigateToPurchase = {},
                        navigateToAdaptiveStrength = {}, navigateToEqualizer = {}, navigateToHeadTracking = {},
                        navigateToAccessibility = {}, navigateToVersion = {}, navigateToTroubleshooting = {},
                        navigateToCallControlScreen = {}, navigateToMicrophoneSettings = {},
                        activateDemoMode = {}, reconnectFromSavedMac = {},
                    )
                }
            }
        }
    }

    private val pro3 = demoState.copy(deviceName = "규민 팟프로", instance = demoInstance)

    @Test fun pro3_light() = shot("pro3_light", pro3)

    @Config(qualifiers = "+night")
    @Test fun pro3_dark() = shot("pro3_dark", pro3)

    @Test fun max2_light() {
        val model = AirPodsMax2()
        shot(
            "max2_light", demoState.copy(
                deviceName = "AirPods Max",
                instance = demoInstance.copy(name = "AirPods Max", model = model),
                capabilities = model.capabilities,
                battery = listOf(
                    Battery(BatteryComponent.LEFT, 64, BatteryStatus.NOT_CHARGING),
                    Battery(BatteryComponent.RIGHT, 64, BatteryStatus.NOT_CHARGING),
                    Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED),
                ),
            )
        )
    }

    @Test fun airpods5_light() {
        val model = AirPodsModels.getModelByModelNumber("A3440")!!
        shot(
            "airpods5_light", demoState.copy(
                deviceName = "AirPods",
                instance = demoInstance.copy(name = "AirPods", model = model),
                capabilities = model.capabilities,
                battery = listOf(
                    Battery(BatteryComponent.LEFT, 100, BatteryStatus.NOT_CHARGING),
                    Battery(BatteryComponent.RIGHT, 9, BatteryStatus.NOT_CHARGING),
                    Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED),
                ),
            )
        )
    }
}
