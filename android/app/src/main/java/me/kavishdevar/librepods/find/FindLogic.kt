package me.kavishdevar.librepods.find

/** Orchard: 근접 신호 세기(dBm)를 사람이 알아듣는 거리 단계로. */
enum class Proximity {
    RightHere, Close, Near, Far, None;

    companion object {
        fun of(rssi: Int?): Proximity = when {
            rssi == null -> None
            rssi >= -50 -> RightHere
            rssi >= -65 -> Close
            rssi >= -80 -> Near
            else -> Far
        }
    }
}

object FindText {
    fun ago(now: Long, then: Long): String {
        val s = (now - then).coerceAtLeast(0) / 1000
        return when {
            s < 60 -> "방금"
            s < 3600 -> "${s / 60}분 전"
            s < 86400 -> "${s / 3600}시간 전"
            else -> "${s / 86400}일 전"
        }
    }

    fun geoUri(lat: Double, lon: Double, label: String): String =
        "geo:$lat,$lon?q=$lat,$lon(${label.replace(Regex("[()]"), "")})"
}
