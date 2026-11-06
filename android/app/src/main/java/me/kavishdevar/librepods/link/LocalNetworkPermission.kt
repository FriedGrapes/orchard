package me.kavishdevar.librepods.link

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Orchard: 안드로이드 17(API 37)부터 같은 와이파이의 기기(PC)와 주고받으려면 '로컬 네트워크 기기에 액세스' 권한이 필요하다.
 * 없으면 보내기가 EPERM으로 막혀 Orchard Link가 블루투스로만 갔다(0.2.6 실기, 갤럭시 안드로이드 17).
 */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val SINCE_SDK = 37

    fun needed(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= SINCE_SDK

    fun granted(context: Context): Boolean =
        !needed() || context.checkSelfPermission(NAME) == PackageManager.PERMISSION_GRANTED

    /** 권한 요청 목록에 — 옛 안드로이드에는 없는 권한이라 넣지 않는다(넣으면 늘 거절로 돌아온다). */
    fun withIfNeeded(perms: List<String>, sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (needed(sdk)) perms + NAME else perms
}
