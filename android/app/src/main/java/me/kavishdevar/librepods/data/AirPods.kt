/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.R
import java.util.Collections.emptySet

open class AirPodsBase(
    val modelNumber: List<String>,
    val name: String,
    val displayName: String = "AirPods",
    val manufacturer: String = "Apple Inc.",
    val budsRes: Int,
    val leftBudsRes: Int,
    val rightBudsRes: Int,
    val caseRes: Int,
    val capabilities: Set<Capability>,
    val isHeadphones: Boolean = false
)
enum class Capability {
    LISTENING_MODE,
    CONVERSATION_AWARENESS,
    STEM_CONFIG,
    HEAD_GESTURES,
    LOUD_SOUND_REDUCTION,
    PPE,
    SLEEP_DETECTION,
    HEARING_AID,
    ADAPTIVE_AUDIO,
    ADAPTIVE_VOLUME,
    SWIPE_FOR_VOLUME,
    HRM
}

class AirPods: AirPodsBase(
    modelNumber = listOf("A1523", "A1722"),
    name = "AirPods 1",
    budsRes = R.drawable.airpods_1_buds,
    leftBudsRes = R.drawable.airpods_1_left,
    rightBudsRes = R.drawable.airpods_1_right,
    caseRes = R.drawable.airpods_1_case,
    capabilities = emptySet()
)

class AirPods2: AirPodsBase(
    modelNumber = listOf("A2032", "A2031"),
    name = "AirPods 2",
    budsRes = R.drawable.airpods_2_buds,
    leftBudsRes = R.drawable.airpods_2_left,
    rightBudsRes = R.drawable.airpods_2_right,
    caseRes = R.drawable.airpods_2_case,
    capabilities = emptySet()
)

class AirPods3: AirPodsBase(
    modelNumber = listOf("A2565", "A2564"),
    name = "AirPods 3",
    budsRes = R.drawable.airpods_3_buds,
    leftBudsRes = R.drawable.airpods_3_left,
    rightBudsRes = R.drawable.airpods_3_right,
    caseRes = R.drawable.airpods_3_case,
    capabilities = setOf(
        Capability.HEAD_GESTURES
    )
)

class AirPods4: AirPodsBase(
    modelNumber = listOf("A3053", "A3050", "A3054"),
    name = "AirPods 4",
    budsRes = R.drawable.airpods_4_buds,
    leftBudsRes = R.drawable.airpods_4_left,
    rightBudsRes = R.drawable.airpods_4_right,
    caseRes = R.drawable.airpods_4_case,
    capabilities = setOf(
        Capability.HEAD_GESTURES,
        Capability.SLEEP_DETECTION,
        Capability.ADAPTIVE_VOLUME
    )
)

class AirPods4ANC: AirPodsBase(
    modelNumber = listOf("A3056", "A3055", "A3057"),
    name = "AirPods 4 (ANC)",
    budsRes = R.drawable.airpods_4_anc_buds,
    leftBudsRes = R.drawable.airpods_4_anc_left,
    rightBudsRes = R.drawable.airpods_4_anc_right,
    caseRes = R.drawable.airpods_4_anc_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.HEAD_GESTURES,
        Capability.ADAPTIVE_AUDIO,
        Capability.SLEEP_DETECTION,
        Capability.ADAPTIVE_VOLUME,
        Capability.STEM_CONFIG
    )
)

class AirPods5: AirPodsBase(
    modelNumber = listOf("A3531", "A3532", "A3533"),
    name = "AirPods 5",
    budsRes = R.drawable.airpods_5_buds,
    leftBudsRes = R.drawable.airpods_5_left,
    rightBudsRes = R.drawable.airpods_5_right,
    caseRes = R.drawable.airpods_5_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.HEAD_GESTURES,
        Capability.ADAPTIVE_AUDIO,
        Capability.SLEEP_DETECTION,
        Capability.ADAPTIVE_VOLUME,
        Capability.STEM_CONFIG
    )
)

class AirPods5WCC: AirPodsBase(
    modelNumber = listOf("A3439", "A3440", "A3441"),
    name = "AirPods 5 with Wireless Charging Case",
    budsRes = R.drawable.airpods_5_wireless_buds,
    leftBudsRes = R.drawable.airpods_5_wireless_left,
    rightBudsRes = R.drawable.airpods_5_wireless_right,
    caseRes = R.drawable.airpods_5_wireless_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.HEAD_GESTURES,
        Capability.ADAPTIVE_AUDIO,
        Capability.SLEEP_DETECTION,
        Capability.ADAPTIVE_VOLUME,
        Capability.STEM_CONFIG,
        Capability.SWIPE_FOR_VOLUME
    )
)

class AirPodsPro1: AirPodsBase(
    modelNumber = listOf("A2084", "A2083"),
    name = "AirPods Pro 1",
    displayName = "AirPods Pro",
    budsRes = R.drawable.airpods_pro_1_buds,
    leftBudsRes = R.drawable.airpods_pro_1_left,
    rightBudsRes = R.drawable.airpods_pro_1_right,
    caseRes = R.drawable.airpods_pro_1_case,
    capabilities = setOf(
        Capability.LISTENING_MODE
    )
)

class AirPodsPro2Lightning: AirPodsBase(
    modelNumber = listOf("A2931", "A2699", "A2698"),
    name = "AirPods Pro 2 with Magsafe Charging Case (Lightning)",
    displayName = "AirPods Pro",
    budsRes = R.drawable.airpods_pro_2_buds,
    leftBudsRes = R.drawable.airpods_pro_2_left,
    rightBudsRes = R.drawable.airpods_pro_2_right,
    caseRes = R.drawable.airpods_pro_2_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.STEM_CONFIG,
        Capability.LOUD_SOUND_REDUCTION,
        Capability.SLEEP_DETECTION,
        Capability.HEARING_AID,
        Capability.ADAPTIVE_AUDIO,
        Capability.ADAPTIVE_VOLUME,
        Capability.SWIPE_FOR_VOLUME,
        Capability.HEAD_GESTURES
    )
)

class AirPodsPro2USBC: AirPodsBase(
    modelNumber = listOf("A3047", "A3048", "A3049"),
    name = "AirPods Pro 2 with Magsafe Charging Case (USB-C)",
    displayName = "AirPods Pro",
    budsRes = R.drawable.airpods_pro_2_buds,
    leftBudsRes = R.drawable.airpods_pro_2_left,
    rightBudsRes = R.drawable.airpods_pro_2_right,
    caseRes = R.drawable.airpods_pro_2_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.STEM_CONFIG,
        Capability.LOUD_SOUND_REDUCTION,
        Capability.SLEEP_DETECTION,
        Capability.HEARING_AID,
        Capability.ADAPTIVE_AUDIO,
        Capability.ADAPTIVE_VOLUME,
        Capability.SWIPE_FOR_VOLUME,
        Capability.HEAD_GESTURES
    )
)

class AirPodsPro3: AirPodsBase(
    modelNumber = listOf("A3063", "A3064", "A3065"),
    name = "AirPods Pro 3",
    displayName = "AirPods Pro",
    budsRes = R.drawable.airpods_pro_3_buds,
    leftBudsRes = R.drawable.airpods_pro_3_left,
    rightBudsRes = R.drawable.airpods_pro_3_right,
    caseRes = R.drawable.airpods_pro_3_case,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.HEAD_GESTURES,
        Capability.STEM_CONFIG,
        Capability.LOUD_SOUND_REDUCTION,
        Capability.PPE,
        Capability.SLEEP_DETECTION,
        Capability.HEARING_AID,
        Capability.ADAPTIVE_AUDIO,
        Capability.ADAPTIVE_VOLUME,
        Capability.SWIPE_FOR_VOLUME,
        Capability.HRM
    )
)

// Orchard: 원본에는 맥스가 없어서 일반 에어팟으로 보였다.
class AirPodsMax: AirPodsBase(
    modelNumber = listOf("A2096"),
    name = "AirPods Max",
    displayName = "AirPods Max",
    budsRes = R.drawable.airpods_max_photo,
    leftBudsRes = R.drawable.airpods_max_photo,
    rightBudsRes = R.drawable.airpods_max_photo,
    caseRes = R.drawable.airpods_max_photo,
    capabilities = setOf(Capability.LISTENING_MODE),
    isHeadphones = true
)

class AirPodsMaxUSBC: AirPodsBase(
    modelNumber = listOf("A3184"),
    name = "AirPods Max (USB-C)",
    displayName = "AirPods Max",
    budsRes = R.drawable.airpods_max_photo,
    leftBudsRes = R.drawable.airpods_max_photo,
    rightBudsRes = R.drawable.airpods_max_photo,
    caseRes = R.drawable.airpods_max_photo,
    capabilities = setOf(Capability.LISTENING_MODE, Capability.CONVERSATION_AWARENESS),
    isHeadphones = true
)

/** 모델 번호(A…)를 아직 모르는 맥스 2. 이름에 "Max"가 들어 있으면 이것으로 본다. */
class AirPodsMax2: AirPodsBase(
    modelNumber = listOf(),
    name = "AirPods Max 2",
    displayName = "AirPods Max",
    budsRes = R.drawable.airpods_max_photo,
    leftBudsRes = R.drawable.airpods_max_photo,
    rightBudsRes = R.drawable.airpods_max_photo,
    caseRes = R.drawable.airpods_max_photo,
    capabilities = setOf(
        Capability.LISTENING_MODE,
        Capability.CONVERSATION_AWARENESS,
        Capability.ADAPTIVE_AUDIO
    ),
    isHeadphones = true
)

/** 표에 없는 새 에어팟 — 기본 기능만 켠다. */
class AirPodsGeneric(modelNumber: String): AirPodsBase(
    modelNumber = listOf(modelNumber),
    name = "AirPods ($modelNumber)",
    budsRes = R.drawable.airpods_4_anc_buds,
    leftBudsRes = R.drawable.airpods_4_anc_left,
    rightBudsRes = R.drawable.airpods_4_anc_right,
    caseRes = R.drawable.airpods_4_anc_case,
    capabilities = setOf(Capability.LISTENING_MODE, Capability.CONVERSATION_AWARENESS)
)

data class AirPodsInstance(
    val name: String,
    val model: AirPodsBase,
    val actualModelNumber: String,
    val serialNumber: String?,
    val leftSerialNumber: String?,
    val rightSerialNumber: String?,
    val version1: String?,
    val version2: String?,
    val version3: String?,
)

object AirPodsModels {
    val models: List<AirPodsBase> = listOf(
        AirPods(),
        AirPods2(),
        AirPods3(),
        AirPods4(),
        AirPods4ANC(),
        AirPods5(),
        AirPods5WCC(),
        AirPodsPro1(),
        AirPodsPro2Lightning(),
        AirPodsPro2USBC(),
        AirPodsPro3(),
        AirPodsMax(),
        AirPodsMaxUSBC(),
        AirPodsMax2()
    )

    fun getModelByModelNumber(modelNumber: String): AirPodsBase? {
        return models.find { modelNumber in it.modelNumber }
    }

    /**
     * 모델 번호로 못 찾아도 null을 돌려주지 않는다(null이면 화면이 일반 에어팟 기본값으로 떨어졌다).
     * 모르는 번호는 이름으로 맥스인지 가리고, 그것도 아니면 기본 기능만 가진 모델로 만든다.
     */
    fun resolve(modelNumber: String, deviceName: String?): AirPodsBase? {
        if (modelNumber.isBlank()) return null
        getModelByModelNumber(modelNumber)?.let { return it }
        android.util.Log.w("AirPodsModels", "모르는 모델 번호: $modelNumber ($deviceName)")
        return if (deviceName?.contains("Max", ignoreCase = true) == true) AirPodsMax2()
        else AirPodsGeneric(modelNumber)
    }
}
