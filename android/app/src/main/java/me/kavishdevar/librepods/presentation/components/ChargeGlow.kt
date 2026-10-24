package me.kavishdevar.librepods.presentation.components

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Orchard: 충전 중인 배터리 링의 빛. 윈도우 앱(UI/BatteryRing.cs)도 같은 수치를 쓴다.
 * 한 바퀴 2.4초 — 링 시작에서 끝까지 짧은 빛(최대 50°)이 흐르고, 둘레는 같은 주기로 숨 쉬듯 밝아졌다 어두워진다.
 */
object ChargeGlow {
    const val PERIOD_MS = 2400
    const val HALO_WIDTH = 2.4f
    private const val LEN = 50f
    private const val PEAK = 0.55f
    private const val PIECES = 8

    /** 둘레 빛의 진하기(0.08~0.20). */
    fun halo(phase: Float): Float = 0.08f + 0.12f * (0.5f - 0.5f * cos(2 * PI * phase).toFloat())

    /**
     * 흐르는 빛을 조각(시작 각도, 길이, 진하기)으로 나눈다. 가운데가 가장 밝고 양 끝은 흐리다.
     * 각도는 링 맨 위(12시)에서 시계 방향으로, 채워진 부분([total]°) 안에서만.
     */
    fun highlight(phase: Float, total: Float): List<Triple<Float, Float, Float>> {
        val len = min(LEN, total)
        val head = (total + len) * phase - len           // 시작 앞에서 나타나 끝 너머로 사라진다
        val envelope = sin(PI * phase).toFloat()          // 처음·끝은 흐리게
        val piece = len / PIECES
        val out = ArrayList<Triple<Float, Float, Float>>(PIECES)
        for (i in 0 until PIECES) {
            val a0 = max(head + i * piece, 0f)
            val a1 = min(head + (i + 1) * piece, total)
            if (a1 <= a0) continue
            val mid = (i + 0.5f) / PIECES
            val shape = 1f - kotlin.math.abs(mid * 2 - 1)  // 가운데 1, 끝 0
            out += Triple(a0, a1 - a0, PEAK * shape * envelope)
        }
        return out
    }
}

/** 충전 빛을 켤지. 정지 화면을 찍는 시험에서는 끈다(끝없이 도는 움직임 때문에 화면이 '멈춤'에 이르지 않음). */
val LocalChargeGlow = staticCompositionLocalOf { true }
