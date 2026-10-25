package me.kavishdevar.librepods.link

/**
 * Orchard: 받은 신호의 시각·중복 검사(shared/link.md 받는 쪽 검사 3·4번).
 * 같은 신호가 와이파이(0·150·400ms 세 번)와 BLE로 여러 번 와도 한 번만 처리하게 한다.
 */
class ReplayGuard(
    private val rememberMs: Long = 120_000L,
) {
    private data class Key(val sender: Long, val time: Long, val seq: Int)

    /** 본 신호 → 본 때(ms). 넣은 차례대로 들어 있다. */
    private val seen = LinkedHashMap<Key, Long>()

    /**
     * 처음 보는 신호이고 시각이 60초 안이면 true(그리고 기억), 아니면 false.
     * [nowMs] 는 시험에서 시각을 넣으려고 받는다.
     */
    @Synchronized
    fun accept(packet: LinkPacket, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!LinkPacket.timeOk(packet.time, nowMs / 1000)) return false
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (nowMs - it.next().value > rememberMs) it.remove() else break
        }
        val key = Key(packet.sender, packet.time, packet.seq)
        if (seen.containsKey(key)) return false
        seen[key] = nowMs
        return true
    }
}
