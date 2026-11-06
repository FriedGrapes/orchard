package me.kavishdevar.librepods.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orchard 0.2.7 실기: 안드로이드 17 폰에서 와이파이 신호가 매번 EPERM — '로컬 네트워크' 권한이 없었다. */
class LocalNetworkPermissionTest {
    @Test
    fun 안드로이드_17부터_필요하다() {
        assertFalse(LocalNetworkPermission.needed(36))
        assertTrue(LocalNetworkPermission.needed(37))
    }

    @Test
    fun 권한_이름() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkPermission.NAME)
    }

    @Test
    fun 옛_안드로이드에서는_요청_목록에_넣지_않는다() {
        // 없는 권한을 요청하면 늘 '거절'로 돌아와 처음 설정의 블루투스 권한이 영영 안 끝난 것처럼 보인다
        assertEquals(listOf("a"), LocalNetworkPermission.withIfNeeded(listOf("a"), sdk = 36))
        assertEquals(listOf("a", LocalNetworkPermission.NAME), LocalNetworkPermission.withIfNeeded(listOf("a"), sdk = 37))
    }
}
