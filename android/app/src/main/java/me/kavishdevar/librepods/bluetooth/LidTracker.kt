package me.kavishdevar.librepods.bluetooth

/**
 * Orchard: 케이스 뚜껑 열림·닫힘을 애플처럼 판정한다.
 *
 * - '열림'은 뚜껑 비트가 열림이고 케이스 안에 알맹이가 있을 때만(귀에 꽂거나 책상에 둔 알맹이는 뚜껑 비트가 늘 열림으로 읽힘).
 * - 한 번 띄운 뒤에는 닫힘을 실제로 보거나 오래(REARM_GAP_MS) 안 보이다 다시 나타나야 또 띄운다.
 *   (예전에는 신호가 15초 끊겼다 다시 잡히면 '새 기기'로 보고 또 띄워서 시도 때도 없이 떴다.)
 */
class LidTracker {
    enum class Event { OPEN, CLOSE }

    private var open = false
    private var armed = true
    private var lastSeen = Long.MIN_VALUE

    /** 광고를 하나 받을 때마다. 띄우거나 닫을 일이 생기면 그 신호를 돌려준다. */
    fun onBroadcast(caseOpen: Boolean, now: Long): Event? {
        if (lastSeen != Long.MIN_VALUE && now - lastSeen >= REARM_GAP_MS) armed = true
        lastSeen = now
        if (!caseOpen) {
            armed = true
            if (open) { open = false; return Event.CLOSE }
            return null
        }
        if (open) return null
        open = true
        if (!armed) return null
        armed = false
        return Event.OPEN
    }

    /** 주기적으로. 열린 채 신호가 끊기면 카드를 닫는다(다시 띄우는 권리는 돌려주지 않는다). */
    fun onTick(now: Long): Event? {
        if (open && now - lastSeen > CLOSE_TIMEOUT_MS) {
            open = false
            return Event.CLOSE
        }
        return null
    }

    companion object {
        const val CLOSE_TIMEOUT_MS = 2_500L
        const val REARM_GAP_MS = 30_000L

        fun caseOpen(lidBitOpen: Boolean, broadcasterInCase: Boolean, podCharging: Boolean, headphones: Boolean): Boolean =
            lidBitOpen && (headphones || broadcasterInCase || podCharging)
    }
}
