package me.kavishdevar.librepods.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orchard 0.2.6 실기: 폰에서 아무것도 안 트는데 에어팟을 가져감.
 * 원인 — 멈춤 키에 반응하지 않는 '음악' 재생(소리 꺼진 피드 영상 등)이 몇 분씩 이어졌는데 재생 시작으로 봤다.
 * 사용자가 튼 재생은 멈춤 키에 곧 멈춘다 → 멈추는지 보고 가져올지 정한다.
 */
class PauseProbeTest {
    private fun seq(vararg active: Boolean): () -> Boolean {
        var i = 0
        return { active[minOf(i++, active.size - 1)] }
    }

    @Test
    fun 곧_멈추면_사용자가_튼_재생이다() = runBlocking {
        assertTrue(PauseProbe.stopped(seq(true, true, false)) {})
    }

    @Test
    fun 끝까지_안_멈추면_사용자가_튼_재생이_아니다() = runBlocking {
        assertFalse(PauseProbe.stopped(seq(true)) {})
    }

    @Test
    fun 멈추는_순간_더_기다리지_않는다() = runBlocking {
        var waited = 0L
        PauseProbe.stopped(seq(true, false)) { waited += it }
        assertEquals(2 * PauseProbe.STEP_MS, waited)
    }

    @Test
    fun 최대_기다림은_1초를_넘지_않는다() = runBlocking {
        var waited = 0L
        PauseProbe.stopped(seq(true)) { waited += it }
        assertTrue(waited <= 1_000L)
    }
}
