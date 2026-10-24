package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPacketTest {
    private fun packet(vararg entries: IntArray): ByteArray {
        val head = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x04, 0x00, entries.size.toByte())
        val body = entries.flatMap { (c, l, s) -> listOf(c.toByte(), 0x01, l.toByte(), s.toByte(), 0x01) }
        return head + body.toByteArray()
    }

    @Test fun 세_칸_에어팟() {
        val r = BatteryPacket.parse(packet(intArrayOf(4, 80, 2), intArrayOf(2, 70, 1), intArrayOf(8, 50, 2)))!!
        assertEquals(listOf(4 to 80, 2 to 70, 8 to 50), r.map { it.component to it.level })
    }

    @Test fun 한_칸_맥스는_좌우_같은_값_케이스는_끊김() {
        val r = BatteryPacket.parse(packet(intArrayOf(1, 65, 2)))!!
        assertEquals(65, r.first { it.component == BatteryComponent.LEFT }.level)
        assertEquals(65, r.first { it.component == BatteryComponent.RIGHT }.level)
        assertEquals(BatteryStatus.DISCONNECTED, r.first { it.component == BatteryComponent.CASE }.status)
    }

    @Test fun 값_127은_측정불가로_끊김_처리() {
        val r = BatteryPacket.parse(packet(intArrayOf(4, 80, 2), intArrayOf(2, 127, 2), intArrayOf(8, 50, 2)))!!
        val right = r.first { it.component == BatteryComponent.RIGHT }
        assertEquals(BatteryStatus.DISCONNECTED, right.status)
        assertEquals(0, right.level)
    }

    @Test fun 배터리_패킷이_아니거나_잘린_것() {
        assertNull(BatteryPacket.parse(byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x01)))
        assertNull(BatteryPacket.parse(packet(intArrayOf(4, 80, 2), intArrayOf(2, 70, 1)).copyOf(12)))
    }
}
