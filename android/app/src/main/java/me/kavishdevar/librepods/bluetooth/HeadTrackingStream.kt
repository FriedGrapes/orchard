package me.kavishdevar.librepods.bluetooth

/**
 * Orchard: 머리 센서 통로 번호를 에어팟에게서 배운다.
 *
 * 에어팟은 연결될 때 HEADTRACKING(0x17) 통로로 '장치 설명서'(프로토콜 버퍼)를 보낸다.
 * 그중 "HTransformation"이 든 설명서의 통로 번호가 머리 움직임 센서다.
 * 원본은 이 번호를 16(0x10, AirPods 4·Pro)이나 14(0x0E)로 고정해서, 15번을 쓰는 AirPods 5 에서는 데이터가 오지 않았다.
 */
object HeadTrackingStream {
    private val MOTION_MARKER = "HTransformation".toByteArray()

    /**
     * 머리 센서 통로 번호를 알 수 있는 패킷이면 그 번호, 아니면 null. 세 가지 모양에서 배운다.
     *  - 장치 설명서(필드 5): 본문에 "HTransformation" 이 있으면 그 통로
     *  - 연결 때 통로 목록(필드 12가 하나뿐인 메시지): 그 통로(일반 장치 통로들은 둘씩 묶여 온다)
     *  - 흐르고 있는 움직임 데이터(필드 7: 통로 + 40바이트 넘는 보고서)
     */
    fun motionStreamId(packet: ByteArray): Int? {
        if (packet.size < 16 || packet[4] != 0x17.toByte()) return null
        val listed = mutableListOf<Int>()
        var i = 12 // 04 00 04 00 17 00 00 00 10 00 <길이 2바이트> 다음부터 프로토콜 버퍼
        while (i < packet.size) {
            val (tag, afterTag) = varint(packet, i) ?: break
            i = afterTag
            val field = (tag shr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> i = (varint(packet, i) ?: break).second
                2 -> {
                    val (len, start) = varint(packet, i) ?: break
                    val end = (start + len.toInt()).coerceAtMost(packet.size)
                    val inner = packet.copyOfRange(start, end)
                    val id = firstVarintField1(inner)
                    when (field) {
                        5 -> return if (id != null && indexOf(inner, MOTION_MARKER) >= 0) id else null
                        12 -> if (id != null) listed += id
                        7 -> if (id != null && reportSize(inner) > 40) return id
                    }
                    i = end
                }
                else -> break
            }
        }
        return listed.singleOrNull()
    }

    /** 안쪽 메시지의 첫 칸(08 <번호>). */
    private fun firstVarintField1(inner: ByteArray): Int? =
        if (inner.isNotEmpty() && inner[0] == 0x08.toByte()) varint(inner, 1)?.first?.toInt() else null

    /** 움직임 데이터 안쪽 메시지의 보고서(필드 3) 길이. */
    private fun reportSize(inner: ByteArray): Int {
        var i = 0
        while (i < inner.size) {
            val (tag, afterTag) = varint(inner, i) ?: return 0
            i = afterTag
            when ((tag and 7).toInt()) {
                0 -> i = (varint(inner, i) ?: return 0).second
                2 -> {
                    val (len, start) = varint(inner, i) ?: return 0
                    if ((tag shr 3).toInt() == 3) return len.toInt()
                    i = start + len.toInt()
                }
                else -> return 0
            }
        }
        return 0
    }

    /** 머리 센서 데이터 시작 요청(데이터 패킷 본문). AirPods 5 실기기에서 확인한 모양. */
    fun startPacket(streamId: Int): ByteArray = request(0x73, streamId, byteArrayOf(0x40, 0x9C.toByte(), 0x00, 0x00))

    /** 머리 센서 데이터 중지 요청. */
    fun stopPacket(streamId: Int): ByteArray = request(0x75, streamId, byteArrayOf(0x00, 0x00, 0x00, 0x00))

    private fun request(transaction: Int, streamId: Int, interval: ByteArray): ByteArray {
        require(streamId in 0..127) { "streamId must fit one varint byte" }
        val body = byteArrayOf(0x08, transaction.toByte(), 0x42, 0x0B, 0x08, streamId.toByte(), 0x10, 0x02, 0x1A, 0x05, 0x01) + interval
        return byteArrayOf(0x17, 0x00, 0x00, 0x00, 0x10, 0x00, body.size.toByte(), 0x00) + body
    }

    private fun varint(b: ByteArray, from: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = from
        while (i < b.size && shift < 64) {
            val v = b[i].toInt() and 0xFF
            result = result or ((v and 0x7F).toLong() shl shift)
            i++
            if (v and 0x80 == 0) return result to i
            shift += 7
        }
        return null
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
