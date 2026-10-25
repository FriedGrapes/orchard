package me.kavishdevar.librepods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orchard: '음악이 꺼져 있다가 켜진 순간'을 놓치지 않는지(0.2.0 실기: PC로 넘긴 뒤 폰에서 다시 틀어도 안 가져옴). */
class MusicEdgeTest {
    @Test
    fun 처음_재생은_시작으로_본다() {
        val e = MusicEdge()
        assertTrue(e.isStart(isActive = true, hasMusic = true))
    }

    @Test
    fun 이미_켜져_있던_음악은_시작이_아니다() {
        val e = MusicEdge()
        e.onEvaluated(isActive = true, hasMusic = true)
        assertFalse(e.isStart(isActive = true, hasMusic = true))
    }

    @Test
    fun 되튐으로_건너뛴_꺼짐도_기억해서_다음_재생을_시작으로_본다() {
        val e = MusicEdge()
        e.onEvaluated(isActive = true, hasMusic = true) // 폰에서 듣는 중
        e.onSkipped(isActive = false, selfAction = false) // PC로 넘기며 멈춤 — 0.3초 안이라 건너뜀
        assertTrue(e.isStart(isActive = true, hasMusic = true)) // 다시 폰에서 재생
    }

    @Test
    fun 내가_멈춘_직후의_꺼짐도_기억한다() {
        val e = MusicEdge()
        e.onEvaluated(isActive = true, hasMusic = true)
        e.onSkipped(isActive = true, selfAction = true) // 멈춤 명령 직후 아직 켜진 것으로 보임
        e.onSkipped(isActive = false, selfAction = true) // 곧 꺼짐
        assertTrue(e.isStart(isActive = true, hasMusic = true))
    }

    @Test
    fun 되튐으로_건너뛴_켜짐은_시작을_삼키지_않는다() {
        val e = MusicEdge()
        e.onSkipped(isActive = true, selfAction = false)
        assertTrue(e.isStart(isActive = true, hasMusic = true))
    }
}
