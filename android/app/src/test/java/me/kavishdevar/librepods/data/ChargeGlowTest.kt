package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.presentation.components.ChargeGlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeGlowTest {
    @Test fun lightStaysInsideFilledArc() {
        for (total in listOf(10f, 90f, 223f, 360f)) {
            for (i in 0..100) {
                for ((from, len, a) in ChargeGlow.highlight(i / 100f, total)) {
                    assertTrue(from >= 0f && from + len <= total + 0.001f)
                    assertTrue(a in 0f..0.55f)
                }
            }
        }
    }

    @Test fun invisibleAtLoopSeam() {
        // 한 바퀴가 끝나고 다시 시작하는 순간 빛이 툭 끊겨 보이지 않게 둘 다 0
        assertTrue(ChargeGlow.highlight(0f, 200f).all { it.third < 0.001f })
        assertTrue(ChargeGlow.highlight(1f, 200f).all { it.third < 0.01f })
    }

    @Test fun haloBreathes() {
        assertEquals(0.08f, ChargeGlow.halo(0f), 0.001f)
        assertEquals(0.20f, ChargeGlow.halo(0.5f), 0.001f)
    }
}
