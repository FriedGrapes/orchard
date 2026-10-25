package me.kavishdevar.librepods.link

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Orchard: 폰과 PC가 주고받는 전환 신호 한 개(shared/link.md v1).
 * 안드로이드 기능에 기대지 않는 순수 Kotlin — JVM 시험에서 그대로 돌린다.
 *
 * 모양: 4F 01 | 종류 | 상태 | 보낸 기기 번호(4) | 시각(4, big-endian) | 순번 | 본문 | 확인값(HMAC-SHA256 앞 8바이트)
 */
class LinkPacket(
    val type: Int,
    val flags: Int,
    /** 보낸 기기 번호(부호 없는 32비트를 Long 으로). */
    val sender: Long,
    /** 보낸 시각(유닉스 초, 부호 없는 32비트를 Long 으로). */
    val time: Long,
    /** 순번 0~255. */
    val seq: Int,
    val body: ByteArray = ByteArray(0),
) {
    val bodyText: String get() = String(body, Charsets.UTF_8)

    /** 열쇠로 확인값까지 붙인 바이트. */
    fun encode(key: ByteArray): ByteArray {
        require(body.size <= MAX_BODY) { "본문이 너무 김: ${body.size}" }
        val head = ByteArray(HEADER + body.size)
        head[0] = MAGIC.toByte()
        head[1] = VERSION.toByte()
        head[2] = type.toByte()
        head[3] = flags.toByte()
        putU32(head, 4, sender)
        putU32(head, 8, time)
        head[12] = seq.toByte()
        body.copyInto(head, HEADER)
        return head + tag(key, head, head.size)
    }

    /** BLE로 보낼 때는 본문을 뺀 21바이트만. */
    fun withoutBody(): LinkPacket = LinkPacket(type, flags, sender, time, seq)

    companion object {
        const val MAGIC = 0x4F
        const val VERSION = 0x01
        const val HEADER = 13
        const val TAG_LEN = 8
        const val MIN_LEN = HEADER + TAG_LEN // 21
        const val MAX_BODY = 200
        const val PORT = 47820
        /** BLE 제조사 데이터에 쓰는 회사 번호. */
        const val BLE_COMPANY_ID = 0xFFFF
        /** 시각 허용 범위(초, 경계 포함). */
        const val TIME_WINDOW_S = 60L

        // 종류
        const val HELLO = 1
        const val WANT = 2
        const val RELEASED = 3
        const val BUSY = 4
        const val PAIR = 5
        const val PAIR_OK = 6

        // 상태 표시 비트
        const val FLAG_AUDIO_HERE = 0x01
        const val FLAG_PLAYING = 0x02
        const val FLAG_IN_CALL = 0x04
        const val FLAG_FOR_CALL = 0x10

        /** 모양만 검사(길이·0x4F·판). 통과하면 null, 아니면 버리는 까닭. */
        fun checkShape(bytes: ByteArray): Reject? = when {
            bytes.size < MIN_LEN || bytes.size > MIN_LEN + MAX_BODY -> Reject.LENGTH
            (bytes[0].toInt() and 0xFF) != MAGIC -> Reject.MAGIC
            (bytes[1].toInt() and 0xFF) != VERSION -> Reject.VERSION
            else -> null
        }

        /** 모양이 맞을 때 보낸 기기 번호(짝 찾기용). 확인값 검사 전이라 믿으면 안 된다. */
        fun peekSender(bytes: ByteArray): Long? = if (checkShape(bytes) == null) u32(bytes, 4) else null

        /** 모양 + 확인값까지 검사해 푼다. 시각·중복 검사는 [ReplayGuard] 가 한다. */
        fun decode(bytes: ByteArray, key: ByteArray): Decoded {
            checkShape(bytes)?.let { return Decoded.Bad(it) }
            val n = bytes.size - TAG_LEN
            val expected = tag(key, bytes, n)
            val got = bytes.copyOfRange(n, bytes.size)
            // 고정 시간 비교
            if (!MessageDigest.isEqual(expected, got)) return Decoded.Bad(Reject.TAG)
            return Decoded.Ok(
                LinkPacket(
                    type = bytes[2].toInt() and 0xFF,
                    flags = bytes[3].toInt() and 0xFF,
                    sender = u32(bytes, 4),
                    time = u32(bytes, 8),
                    seq = bytes[12].toInt() and 0xFF,
                    body = bytes.copyOfRange(HEADER, n),
                )
            )
        }

        /** 보낸 시각이 내 시각과 60초 안인지(경계 포함). */
        fun timeOk(time: Long, nowSec: Long): Boolean = kotlin.math.abs(time - nowSec) <= TIME_WINDOW_S

        private fun tag(key: ByteArray, data: ByteArray, len: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.update(data, 0, len)
            return mac.doFinal().copyOf(TAG_LEN)
        }

        private fun putU32(b: ByteArray, at: Int, v: Long) {
            b[at] = (v ushr 24).toByte()
            b[at + 1] = (v ushr 16).toByte()
            b[at + 2] = (v ushr 8).toByte()
            b[at + 3] = v.toByte()
        }

        private fun u32(b: ByteArray, at: Int): Long =
            ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
                ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
    }

    enum class Reject { LENGTH, MAGIC, VERSION, TAG }

    sealed interface Decoded {
        data class Ok(val packet: LinkPacket) : Decoded
        data class Bad(val reason: Reject) : Decoded
    }
}

/** 바이트를 hex 글자로(기록·시험용). */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val s = trim()
    require(s.length % 2 == 0) { "hex 길이가 홀수" }
    return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
