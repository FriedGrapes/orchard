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
import me.kavishdevar.librepods.link.LinkPeer
import me.kavishdevar.librepods.link.LinkSettingsContent
import me.kavishdevar.librepods.link.PairStatus
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Orchard Link: 설정 화면 'PC와 연결' 묶음 — 연결됨·연결 중·못 찾음 상태를 그림으로 남긴다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w411dp-h900dp-xxhdpi")
class LinkSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_793_000_000_000L
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val desk = LinkPeer(0x0badf00d, "서재 컴퓨터", key, "192.168.0.10", now - 5_000, LinkPeer.VIA_WIFI)
    private val laptop = LinkPeer(0x1a2b3c4d, "H1-PC", key, null, now - 3 * 60_000, LinkPeer.VIA_BLE)
    private val fresh = LinkPeer(0x00000001, "노트북", key)

    @Test fun 세_가지_상태() {
        compose.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                Column(
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkSettingsContent(listOf(desk, laptop), PairStatus.Paired("서재 컴퓨터"), now)
                    LinkSettingsContent(emptyList(), PairStatus.Pairing("H1-PC"), now)
                    LinkSettingsContent(listOf(fresh), PairStatus.NotFound, now)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("방금 · 와이파이").assertExists()
        compose.onNodeWithText("3분 전 · 블루투스").assertExists()
        compose.onNodeWithText("아직 신호 없음").assertExists()
        compose.onNodeWithText("✓ 서재 컴퓨터와 연결됐어요").assertExists()
        compose.onNodeWithText("H1-PC와 연결하는 중…").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/link_settings.png")
    }

    @Test fun 끊기는_한_번_더_묻는다() {
        var removed: LinkPeer? = null
        compose.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxWidth().padding(16.dp)) {
                    LinkSettingsContent(listOf(desk), PairStatus.Idle, now, onRemove = { removed = it })
                }
            }
        }
        compose.onNodeWithText("끊기").performClick()
        compose.onNodeWithText("서재 컴퓨터 연결을 끊을까요?").assertExists()
        // 대화상자는 다른 창이라 화면 전체로 찍는다
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screenshots/link_settings_remove.png")
        assertEquals(null, removed)
        // 대화상자의 '끊기'(두 번째)를 누르면 지운다
        compose.onAllNodes(androidx.compose.ui.test.hasText("끊기"))[1].performClick()
        compose.waitForIdle()
        assertEquals(desk, removed)
    }
}
