package me.kavishdevar.librepods.data

/**
 * Orchard: AACP 배터리 패킷 해석.
 * 원본은 길이 22(3칸)만 받아서 배터리가 1칸인 AirPods Max는 배터리가 안 나왔고(#783),
 * 측정 불가 값 127이 그대로 "127%"로 보였다(#737, #614).
 *
 * 형식: 04 00 04 00 04 00 <칸 수> 다음에 칸마다 [구성요소, 01, 잔량, 상태, 01].
 */
object BatteryPacket {
    private val PREFIX = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x04, 0x00)
    private const val ENTRY = 5

    fun isBattery(data: ByteArray): Boolean =
        data.size >= PREFIX.size + 1 && PREFIX.indices.all { data[it] == PREFIX[it] }

    /** 늘 [LEFT, RIGHT, CASE] 세 칸을 돌려준다. 배터리 패킷이 아니거나 잘렸으면 null. */
    fun parse(data: ByteArray): List<Battery>? {
        if (!isBattery(data)) return null
        val count = data[PREFIX.size].toInt() and 0xFF
        if (count == 0 || data.size < PREFIX.size + 1 + count * ENTRY) return null

        val entries = (0 until count).map { i ->
            val o = PREFIX.size + 1 + i * ENTRY
            val level = data[o + 2].toInt() and 0xFF
            val status = data[o + 3].toInt() and 0xFF
            if (level > 100) Battery(data[o].toInt() and 0xFF, 0, BatteryStatus.DISCONNECTED)
            else Battery(data[o].toInt() and 0xFF, level, status)
        }

        fun find(c: Int) = entries.firstOrNull { it.component == c }
        // 헤드폰처럼 좌우 구분이 없는 한 칸은 왼쪽·오른쪽에 같은 값을 넣어 기존 화면(최솟값 표시)이 그대로 동작하게 한다.
        val single = if (count == 1) entries[0] else null
        val left = find(BatteryComponent.LEFT) ?: single?.copy(component = BatteryComponent.LEFT)
            ?: Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED)
        val right = find(BatteryComponent.RIGHT) ?: single?.copy(component = BatteryComponent.RIGHT)
            ?: Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED)
        val case = find(BatteryComponent.CASE) ?: Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)
        return listOf(left, right, case)
    }
}
