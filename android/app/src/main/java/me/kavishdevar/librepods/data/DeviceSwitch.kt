package me.kavishdevar.librepods.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Orchard: 기기 간 전환을 '자동 / 수동' 하나로 묶는다.
 * 원본의 가져오기 스위치 6개(에어팟 상태 4 + 폰 상태 2)를 한 번에 맞춘다.
 *  - 자동: 아이폰처럼 이 폰에서 재생하거나 전화가 오면 에어팟을 가져온다(다른 기기가 통화 중일 때만 빼고).
 *  - 수동: 저절로 가져오지 않는다. 빠른 설정 타일의 '이 폰으로 가져오기'로만.
 */
object DeviceSwitch {
    const val PREF = "orchard_switch_mode"
    const val AUTO = "auto"
    const val MANUAL = "manual"

    fun isAuto(prefs: SharedPreferences) = prefs.getString(PREF, AUTO) == AUTO

    fun apply(prefs: SharedPreferences, auto: Boolean) {
        prefs.edit {
            putString(PREF, if (auto) AUTO else MANUAL)
            putBoolean("takeover_when_disconnected", auto)
            putBoolean("takeover_when_idle", auto)
            putBoolean("takeover_when_music", auto)
            putBoolean("takeover_when_call", false)
            putBoolean("takeover_when_ringing_call", auto)
            putBoolean("takeover_when_media_start", auto)
        }
    }

    /** 처음 실행이면 자동으로 맞춘다. */
    fun ensureDefault(prefs: SharedPreferences) {
        if (!prefs.contains(PREF)) apply(prefs, true)
    }
}
