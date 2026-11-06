package me.kavishdevar.librepods.utils

import kotlinx.coroutines.delay

/**
 * Orchard: 멈춤 키를 보낸 뒤 재생이 실제로 멈추는지 본다.
 * 사용자가 튼 재생(유튜브·음악 앱 등)은 미디어 세션이 있어 곧 멈춘다.
 * 소리 꺼진 피드 영상처럼 멈춤 키를 받지 않는 재생은 그대로 이어진다 — 이걸로 에어팟을 가져가면 안 된다
 * (0.2.5 실기: 폰에서 아무것도 안 트는데 다른 기기의 에어팟을 가져감, 그 재생은 몇 분씩 이어짐).
 */
object PauseProbe {
    const val STEP_MS = 100L
    const val TRIES = 8

    /** 멈췄으면 true(멈추는 순간 바로), 0.8초 동안 안 멈추면 false. */
    suspend fun stopped(isActive: () -> Boolean, wait: suspend (Long) -> Unit = { delay(it) }): Boolean {
        repeat(TRIES) {
            wait(STEP_MS)
            if (!isActive()) return true
        }
        return false
    }
}
