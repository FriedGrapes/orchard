package me.kavishdevar.librepods.bluetooth

/**
 * Orchard: 큰 머리 통로(0x17) 패킷이 블루투스에서 여러 조각으로 나뉘어 오면 이어 붙인다.
 * 머리 통로 패킷은 10~11번째 바이트에 본문 길이(작은 쪽 먼저)를 담고 있다.
 * 조각난 채 처리하면 머리 센서 통로 설명서를 못 읽었다(AirPods 5 에서 실제로 208+144 바이트로 나뉨).
 */
class PacketAssembler {
    private var pending: ByteArray? = null
    private var expected = 0

    /** 온전한 패킷이 되면 돌려주고, 아직 조각이면 null. */
    fun feed(chunk: ByteArray): ByteArray? {
        if (isHeader(chunk)) {
            pending = null
            val need = headLength(chunk)
            if (need != null && chunk.size < need) {
                pending = chunk
                expected = need
                return null
            }
            return chunk
        }
        val held = pending ?: return chunk // 머리말 없는 조각인데 기다리는 게 없으면 그대로(기존 처리에 맡김)
        val joined = held + chunk
        if (joined.size < expected) {
            pending = joined
            return null
        }
        pending = null
        return joined
    }

    private fun isHeader(b: ByteArray) =
        b.size >= 4 && b[0] == 0x04.toByte() && b[1] == 0.toByte() && b[2] == 0x04.toByte() && b[3] == 0.toByte()

    private fun headLength(b: ByteArray): Int? {
        if (b.size < 12 || b[4] != 0x17.toByte()) return null
        return 12 + ((b[10].toInt() and 0xFF) or ((b[11].toInt() and 0xFF) shl 8))
    }
}
