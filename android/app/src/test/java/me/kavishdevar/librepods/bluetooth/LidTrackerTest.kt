package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidTrackerTest {
    // ── 열림 판정: 애플처럼 '케이스 안에 알맹이가 있고 뚜껑이 열렸을 때'만 ──
    @Test fun 귀에_꽂은_채로는_뚜껑비트가_열림이어도_닫힘() {
        assertFalse(LidTracker.caseOpen(lidBitOpen = true, broadcasterInCase = false, podCharging = false, headphones = false))
    }

    @Test fun 케이스_안_알맹이_뚜껑_열림() {
        assertTrue(LidTracker.caseOpen(lidBitOpen = true, broadcasterInCase = true, podCharging = false, headphones = false))
    }

    @Test fun 충전_중인_알맹이가_있으면_케이스_안으로_봄() {
        assertTrue(LidTracker.caseOpen(lidBitOpen = true, broadcasterInCase = false, podCharging = true, headphones = false))
    }

    @Test fun 뚜껑_닫힘은_언제나_닫힘() {
        assertFalse(LidTracker.caseOpen(lidBitOpen = false, broadcasterInCase = true, podCharging = true, headphones = false))
    }

    @Test fun 맥스는_뚜껑비트만_봄() {
        assertTrue(LidTracker.caseOpen(lidBitOpen = true, broadcasterInCase = false, podCharging = false, headphones = true))
    }

    // ── 열기·닫기 신호 ──
    @Test fun 처음_열림을_보면_한번_띄움() {
        val t = LidTracker()
        assertEquals(LidTracker.Event.OPEN, t.onBroadcast(true, 0))
        assertNull(t.onBroadcast(true, 500))
        assertNull(t.onBroadcast(true, 1_000))
    }

    @Test fun 닫힘을_보면_닫고_다시_열면_또_띄움() {
        val t = LidTracker()
        t.onBroadcast(true, 0)
        assertEquals(LidTracker.Event.CLOSE, t.onBroadcast(false, 1_000))
        assertEquals(LidTracker.Event.OPEN, t.onBroadcast(true, 2_000))
    }

    @Test fun 신호가_잠깐_끊기면_카드는_닫되_다시_잡혀도_또_띄우지_않음() {
        val t = LidTracker()
        t.onBroadcast(true, 0)
        assertEquals(LidTracker.Event.CLOSE, t.onTick(4_000))
        assertNull(t.onBroadcast(true, 6_000))
    }

    @Test fun 오래_안_보이다_다시_열린_케이스는_새로_띄움() {
        val t = LidTracker()
        t.onBroadcast(true, 0)
        t.onTick(4_000)
        assertEquals(LidTracker.Event.OPEN, t.onBroadcast(true, 40_000))
    }

    @Test fun 귀에_꽂고_다니는_동안은_한번도_안_띄움() {
        val t = LidTracker()
        var now = 0L
        repeat(50) {
            val open = LidTracker.caseOpen(lidBitOpen = true, broadcasterInCase = false, podCharging = false, headphones = false)
            assertNull(t.onBroadcast(open, now))
            now += if (it % 7 == 0) 20_000 else 500  // 가끔 신호가 오래 끊겨도
            t.onTick(now)
        }
    }

    @Test fun 닫힌_상태에서는_시간이_지나도_닫기신호_없음() {
        val t = LidTracker()
        t.onBroadcast(false, 0)
        assertNull(t.onTick(10_000))
    }
}
