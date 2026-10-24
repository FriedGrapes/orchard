package me.kavishdevar.librepods.presentation.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Orchard: 애플식 '연속 곡률' 둥근 사각형. 보통 둥근 모서리(원호)는 직선과 만나는 곳에서 곡률이 툭 끊기는데,
 * 모서리를 초타원(n=4) 조각으로 그려 직선에서 곡선으로 매끄럽게 넘어간다.
 * 윈도우 앱(UI/ContinuousCorners.cs)도 같은 식을 쓴다 — 두 OS 팝업의 모서리가 똑같다.
 */
class ContinuousRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(continuousPath(size.width, size.height, with(density) { radius.toPx() }))
}

/** 눈에 보이는 반지름이 [r] 쯤 되도록, 곡선이 시작되는 거리를 r×1.6 으로 잡는다. */
fun continuousPath(w: Float, h: Float, r: Float, steps: Int = 24): Path {
    val e = min(r * 1.6f, min(w, h) / 2f)
    val k = 2f / 4f // 초타원 지수 n=4
    fun c(t: Double) = cos(t).toFloat().coerceAtLeast(0f).pow(k)
    fun s(t: Double) = sin(t).toFloat().coerceAtLeast(0f).pow(k)
    val half = Math.PI / 2
    val p = Path()
    p.moveTo(e, 0f)
    p.lineTo(w - e, 0f)
    for (i in 1..steps) { val t = half * (1 - i.toDouble() / steps); p.lineTo(w - e + e * c(t), e - e * s(t)) }
    p.lineTo(w, h - e)
    for (i in 1..steps) { val t = half * (i.toDouble() / steps); p.lineTo(w - e + e * c(t), h - e + e * s(t)) }
    p.lineTo(e, h)
    for (i in 1..steps) { val t = half * (1 - i.toDouble() / steps); p.lineTo(e - e * c(t), h - e + e * s(t)) }
    p.lineTo(0f, e)
    for (i in 1..steps) { val t = half * (i.toDouble() / steps); p.lineTo(e - e * c(t), e - e * s(t)) }
    p.close()
    return p
}
