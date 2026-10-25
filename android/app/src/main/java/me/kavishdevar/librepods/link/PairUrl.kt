package me.kavishdevar.librepods.link

import java.net.URLDecoder
import java.util.Base64

/**
 * Orchard: PC가 QR로 보여 주는 짝짓기 주소.
 * `orchard://pair?v=1&id=<8자리 hex>&k=<base64url, = 없음>&n=<URL 인코딩 이름>&a=<IPv4,IPv4>&p=47820`
 */
data class PairUrl(
    val id: Long,
    val key: ByteArray,
    val name: String,
    val addrs: List<String>,
    val port: Int,
) {
    companion object {
        const val KEY_LEN = 32

        /** 틀린 주소면 null(QR을 잘못 찍었거나 다른 앱의 QR). */
        fun parse(text: String): PairUrl? = runCatching {
            val s = text.trim()
            val prefix = "orchard://pair?"
            if (!s.startsWith(prefix, ignoreCase = true)) return null
            val query = s.substring(prefix.length).substringBefore('#')
            val params = query.split('&').filter { it.isNotEmpty() }.associate {
                it.substringBefore('=') to it.substringAfter('=', "")
            }
            if (params["v"] != "1") return null
            val idHex = params["id"] ?: return null
            if (idHex.length != 8) return null
            val id = idHex.toLong(16)
            val key = Base64.getUrlDecoder().decode(params["k"] ?: return null)
            if (key.size != KEY_LEN) return null
            val name = URLDecoder.decode(params["n"] ?: "", Charsets.UTF_8)
            val addrs = URLDecoder.decode(params["a"] ?: "", Charsets.UTF_8)
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val port = params["p"]?.toIntOrNull() ?: LinkPacket.PORT
            PairUrl(id, key, name, addrs, port)
        }.getOrNull()
    }

    override fun equals(other: Any?): Boolean =
        other is PairUrl && id == other.id && key.contentEquals(other.key) && name == other.name &&
            addrs == other.addrs && port == other.port

    override fun hashCode(): Int = id.hashCode() * 31 + key.contentHashCode()
}

/** Orchard: 한국어 조사 고르기(이름 끝 글자 받침에 맞춰). 한글이 아닌 끝 글자는 둘 다 적는다. */
object KoParticle {
    /** "으로"/"로" — 받침 없거나 ㄹ 받침이면 '로'. */
    fun ro(word: String): String = when (jong(word)) {
        null -> "(으)로"
        0, 8 -> "로"
        else -> "으로"
    }

    /** "이"/"가". */
    fun iGa(word: String): String = when (jong(word)) {
        null -> "이(가)"
        0 -> "가"
        else -> "이"
    }

    /** "와"/"과". */
    fun waGwa(word: String): String = when (jong(word)) {
        null -> "와(과)"
        0 -> "와"
        else -> "과"
    }

    /**
     * 끝 글자의 받침(0 = 없음, 8 = ㄹ, 그 밖 = 다른 받침). 영문·숫자는 읽는 소리로 판단한다
     * (PC → 씨, L → 엘, 1 → 일 …). 모르는 글자는 null.
     */
    private fun jong(word: String): Int? {
        val c = word.trimEnd().lastOrNull() ?: return null
        if (c in '가'..'힣') return (c - '가') % 28
        return when (c.uppercaseChar()) {
            'L', 'R', '1', '7', '8' -> 8
            'M', 'N', '0', '3', '6' -> 1
            in 'A'..'Z', '2', '4', '5', '9' -> 0
            else -> null
        }
    }
}
