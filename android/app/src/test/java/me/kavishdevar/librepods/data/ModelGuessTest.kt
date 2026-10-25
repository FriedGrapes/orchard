package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 연결 직후(기기 정보가 오기 전) 어떤 모델 그림을 보여 줄지. 엉뚱한 기본값(프로 2)을 쓰지 않는다. */
class ModelGuessTest {
    @Test fun bleNameMapsToModel() {
        assertEquals("AirPods 5", AirPodsModels.fromBleName("AirPods 5")?.name)
        assertEquals("AirPods Max 2", AirPodsModels.fromBleName("AirPods Max 2")?.name)
        assertEquals("AirPods Pro 3", AirPodsModels.fromBleName("AirPods Pro 3")?.name)
        assertEquals("AirPods 4 (ANC)", AirPodsModels.fromBleName("AirPods 4 (ANC)")?.name)
        assertNull(AirPodsModels.fromBleName("Unknown (1234)"))
    }

    @Test fun guessPrefersRealThenNearbyAndNeverDefaultsToPro2() {
        val pro3 = AirPodsModels.fromBleName("AirPods Pro 3")
        // 진짜 기기 정보가 있으면 그것
        assertEquals("AirPods Pro 3", AirPodsModels.guess(pro3, "AirPods 5")?.name)
        // 없으면 지금 근처에서 보이는 광고의 모델
        assertEquals("AirPods 5", AirPodsModels.guess(null, "AirPods 5")?.name)
        // 둘 다 없으면 모른다(그림 자리를 비워 둔다)
        assertNull(AirPodsModels.guess(null, null))
        assertNull(AirPodsModels.guess(null, "Unknown (99)"))
    }
}
