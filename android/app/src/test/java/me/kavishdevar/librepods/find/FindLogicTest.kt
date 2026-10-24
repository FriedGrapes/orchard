package me.kavishdevar.librepods.find

import org.junit.Assert.assertEquals
import org.junit.Test

class FindLogicTest {
    @Test fun 신호_세기로_거리_단계() {
        assertEquals(Proximity.RightHere, Proximity.of(-45))
        assertEquals(Proximity.Close, Proximity.of(-60))
        assertEquals(Proximity.Near, Proximity.of(-72))
        assertEquals(Proximity.Far, Proximity.of(-88))
        assertEquals(Proximity.None, Proximity.of(null))
    }

    @Test fun 얼마_전인지_한국어로() {
        val now = 1_000_000_000L
        assertEquals("방금", FindText.ago(now, now - 20_000))
        assertEquals("5분 전", FindText.ago(now, now - 5 * 60_000))
        assertEquals("3시간 전", FindText.ago(now, now - 3 * 3_600_000))
        assertEquals("2일 전", FindText.ago(now, now - 2 * 86_400_000L))
    }

    @Test fun 지도_주소() {
        assertEquals("geo:37.5665,126.978?q=37.5665,126.978(AirPods)", FindText.geoUri(37.5665, 126.978, "AirPods"))
    }
}
