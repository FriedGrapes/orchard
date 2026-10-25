package me.kavishdevar.librepods.link

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Orchard: 윈도우와 같은 시험 벡터(shared/link-vectors.json, tools/link_vectors.py가 만듦)를 통과해야 한다.
 * gradle 시험의 user.dir 은 android/app 이라 ../../shared 에 있다.
 */
class LinkPacketTest {
    private val vectors: JSONObject by lazy {
        val base = File(System.getProperty("user.dir") ?: ".")
        val file = listOf(
            File(base, "../../shared/link-vectors.json"),
            File(base, "../shared/link-vectors.json"),
            File(base, "shared/link-vectors.json"),
        ).firstOrNull { it.isFile } ?: error("link-vectors.json 을 찾지 못함 (user.dir=$base)")
        JSONObject(file.readText(Charsets.UTF_8))
    }
    private val key by lazy { vectors.getString("key_hex").hexToBytes() }

    @Test fun 좋은_신호는_똑같은_바이트로_만들고_다시_푼다() {
        val good = vectors.getJSONArray("good")
        assertTrue(good.length() > 0)
        for (i in 0 until good.length()) {
            val v = good.getJSONObject(i)
            val name = v.getString("name")
            val packet = LinkPacket(
                type = v.getInt("type"),
                flags = v.getInt("flags"),
                sender = v.getLong("sender"),
                time = v.getLong("time"),
                seq = v.getInt("seq"),
                body = v.getString("body").toByteArray(Charsets.UTF_8),
            )
            val hex = v.getString("hex")
            assertEquals(name, hex, packet.encode(key).toHex())

            val decoded = LinkPacket.decode(hex.hexToBytes(), key)
            if (decoded !is LinkPacket.Decoded.Ok) fail("$name: 풀리지 않음 $decoded")
            val p = (decoded as LinkPacket.Decoded.Ok).packet
            assertEquals(name, v.getInt("type"), p.type)
            assertEquals(name, v.getInt("flags"), p.flags)
            assertEquals(name, v.getLong("sender"), p.sender)
            assertEquals(name, v.getLong("time"), p.time)
            assertEquals(name, v.getInt("seq"), p.seq)
            assertEquals(name, v.getString("body"), p.bodyText)
            assertEquals(name, v.getLong("sender"), LinkPacket.peekSender(hex.hexToBytes()))
        }
    }

    @Test fun 나쁜_신호는_까닭대로_버린다() {
        val bad = vectors.getJSONArray("bad")
        assertTrue(bad.length() > 0)
        for (i in 0 until bad.length()) {
            val v = bad.getJSONObject(i)
            val decoded = LinkPacket.decode(v.getString("hex").hexToBytes(), key)
            if (decoded !is LinkPacket.Decoded.Bad) fail("${v.getString("name")}: 받아들여짐")
            assertEquals(
                v.getString("name"),
                v.getString("reason").uppercase(),
                (decoded as LinkPacket.Decoded.Bad).reason.name,
            )
        }
    }

    @Test fun 시각은_60초_안만() {
        val now = vectors.getLong("now")
        val cases = vectors.getJSONArray("time_cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals("time=${c.getLong("time")}", c.getBoolean("ok"), LinkPacket.timeOk(c.getLong("time"), now))
        }
    }

    @Test fun 짝짓기_주소를_푼다() {
        val parsed = PairUrl.parse(vectors.getString("pair_url"))
        assertNotNull(parsed)
        val want = vectors.getJSONObject("pair_parsed")
        assertEquals(want.getLong("id"), parsed!!.id)
        assertArrayEquals(want.getString("key_hex").hexToBytes(), parsed.key)
        assertEquals(want.getString("name"), parsed.name)
        val addrs = want.getJSONArray("addrs")
        assertEquals((0 until addrs.length()).map { addrs.getString(it) }, parsed.addrs)
        assertEquals(want.getInt("port"), parsed.port)
    }

    @Test fun 틀린_짝짓기_주소는_null() {
        assertNull(PairUrl.parse("https://example.com"))
        assertNull(PairUrl.parse("orchard://pair?v=2&id=0badf00d&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"))
        assertNull(PairUrl.parse("orchard://pair?v=1&id=0badf00d&k=AAEC")) // 열쇠가 짧음
        assertNull(PairUrl.parse("orchard://pair?v=1&id=xyz&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"))
    }

    @Test fun BLE용은_본문_없는_21바이트() {
        val p = LinkPacket(LinkPacket.WANT, 0, 1, 1793000000, 3, "이름".toByteArray())
        assertEquals(LinkPacket.MIN_LEN, p.withoutBody().encode(key).size)
    }

    @Test fun 같은_신호는_한_번만_처리한다() {
        val guard = ReplayGuard()
        val nowMs = 1793000000_000L
        val a = LinkPacket(LinkPacket.WANT, 0, 7, 1793000000, 1)
        assertTrue(guard.accept(a, nowMs))
        // 와이파이로 두 번째, BLE로 세 번째 와도 버린다
        assertFalse(guard.accept(LinkPacket(LinkPacket.WANT, 0, 7, 1793000000, 1), nowMs + 150))
        assertFalse(guard.accept(a, nowMs + 400))
        // 순번이 다르면 새 신호
        assertTrue(guard.accept(LinkPacket(LinkPacket.WANT, 0, 7, 1793000000, 2), nowMs + 500))
        // 다른 기기의 같은 순번도 새 신호
        assertTrue(guard.accept(LinkPacket(LinkPacket.WANT, 0, 8, 1793000000, 1), nowMs + 500))
        // 시각이 60초 넘게 벗어나면 버린다
        assertFalse(guard.accept(LinkPacket(LinkPacket.WANT, 0, 7, 1793000000 - 61, 9), nowMs))
        assertFalse(guard.accept(LinkPacket(LinkPacket.WANT, 0, 7, 1793000000 + 61, 9), nowMs))
    }

    @Test fun 조사() {
        assertEquals("으로", KoParticle.ro("노트북"))   // ㄱ 받침
        assertEquals("로", KoParticle.ro("서재 컴퓨터"))  // 받침 없음
        assertEquals("로", KoParticle.ro("거실"))       // ㄹ 받침은 '로'
        assertEquals("로", KoParticle.ro("H1-PC"))        // 씨
        assertEquals("가", KoParticle.iGa("H1-PC"))
        assertEquals("으로", KoParticle.ro("Surface 3"))  // 삼
        assertEquals("(으)로", KoParticle.ro("내 PC ★"))
        assertEquals("가", KoParticle.iGa("서재 컴퓨터"))
        assertEquals("이", KoParticle.iGa("노트북"))
    }
}
