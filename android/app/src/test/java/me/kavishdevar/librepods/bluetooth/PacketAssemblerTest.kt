package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketAssemblerTest {
    // 머리 통로 패킷: 04 00 04 00 17 00 00 00 10 00 <길이 LE 2바이트> <본문>
    private fun headPacket(bodyLen: Int): ByteArray {
        val h = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00, 0x10, 0x00, (bodyLen and 0xFF).toByte(), (bodyLen shr 8).toByte())
        return h + ByteArray(bodyLen) { (it % 251).toByte() }
    }

    @Test fun 쪼개진_머리_통로_패킷을_이어_붙인다() {
        val whole = headPacket(508)
        val a = PacketAssembler()
        assertNull(a.feed(whole.copyOfRange(0, 208)))
        assertArrayEquals(whole, a.feed(whole.copyOfRange(208, whole.size)))
    }

    @Test fun 세_조각도() {
        val whole = headPacket(852)
        val a = PacketAssembler()
        assertNull(a.feed(whole.copyOfRange(0, 300)))
        assertNull(a.feed(whole.copyOfRange(300, 600)))
        assertArrayEquals(whole, a.feed(whole.copyOfRange(600, whole.size)))
    }

    @Test fun 온전한_패킷과_다른_종류는_그대로() {
        val a = PacketAssembler()
        val whole = headPacket(8)
        assertArrayEquals(whole, a.feed(whole))
        val battery = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x04, 0x00, 0x01, 0x04, 0x01, 0x55, 0x02, 0x01)
        assertArrayEquals(battery, a.feed(battery))
    }

    @Test fun 기다리던_중_새_패킷이_오면_버리고_새것을() {
        val a = PacketAssembler()
        assertNull(a.feed(headPacket(508).copyOfRange(0, 100)))
        val battery = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x04, 0x00, 0x01, 0x04, 0x01, 0x55, 0x02, 0x01)
        assertArrayEquals(battery, a.feed(battery))
    }
}
