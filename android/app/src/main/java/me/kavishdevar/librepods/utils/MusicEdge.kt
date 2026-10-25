package me.kavishdevar.librepods.utils

/**
 * Orchard: 재생 알림을 보고 '음악이 꺼져 있다가 켜진 순간'(= 이 폰에서 재생 시작 → 에어팟 가져오기)을 가린다.
 * 원본은 되튐(0.3초)으로 건너뛴 알림에서 직전 상태를 고치지 않아, 멈춤 알림을 건너뛰면 '켜짐'으로 남았다.
 * 그러면 다음에 폰에서 재생해도 이미 켜져 있던 음악으로 보고 가져오지 않았다(0.2.0 실기: PC로 넘긴 뒤).
 * 꺼짐은 언제 봐도 기억한다 — 꺼짐을 기억해서 놓치는 시작은 없다.
 */
class MusicEdge {
    var lastActive: Boolean? = null
        private set

    /** 되튐이나 '내가 한 동작' 때문에 건너뛴 알림. */
    fun onSkipped(isActive: Boolean, selfAction: Boolean) {
        if (!isActive) lastActive = false
        else if (selfAction) lastActive = true // 원본과 같게: 내가 튼 재생은 시작으로 보지 않는다
    }

    fun isStart(isActive: Boolean, hasMusic: Boolean): Boolean = isActive && hasMusic && lastActive != true

    fun onEvaluated(isActive: Boolean, hasMusic: Boolean) {
        lastActive = hasMusic && isActive
    }

    /** 다른 기기 때문에 멈춘 상태에서 본 알림. */
    fun set(isActive: Boolean) {
        lastActive = isActive
    }
}
