package me.kavishdevar.librepods.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase

/**
 * Orchard: 알맹이 높이 ÷ 케이스 높이 — 실제 제품 치수(mm)대로. 윈도우 앱(UI/DeviceArt.cs)도 같은 값.
 * AirPods 1·2 40.5/53.5, AirPods 3·4·5 30.2/46.2, Pro 1·2 30.9/45.2, Pro 3 33.4/47.6
 */
fun budToCaseRatio(model: AirPodsBase?): Float = when (model?.budsRes) {
    R.drawable.airpods_1_buds, R.drawable.airpods_2_buds -> 0.76f
    R.drawable.airpods_pro_1_buds, R.drawable.airpods_pro_2_buds -> 0.68f
    R.drawable.airpods_pro_3_buds -> 0.70f
    else -> 0.65f
}

/** 기기 그림 한 줄: 알맹이와 케이스를 실제 크기 비율로, 바닥을 맞춰 나란히 놓는다. 헤드폰은 한 장. */
@Composable
fun DeviceArtRow(model: AirPodsBase?, height: Dp, modifier: Modifier = Modifier) {
    val buds = painterResource(model?.budsRes ?: R.drawable.airpods_pro_2_buds)
    if (model?.isHeadphones == true) {
        Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(buds, model.name, contentScale = ContentScale.Fit, modifier = Modifier.height(height))
        }
        return
    }
    val case = painterResource(model?.caseRes ?: R.drawable.airpods_pro_2_case)
    val gap = 18.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
        val caseAr = case.intrinsicSize.width / case.intrinsicSize.height
        val budsAr = buds.intrinsicSize.width / buds.intrinsicSize.height
        val ratio = budToCaseRatio(model)
        // 높이 기준으로 놓고, 폭이 넘치면 둘을 같은 배율로 줄인다
        var caseH = height
        val total = caseH * caseAr + caseH * ratio * budsAr + gap
        if (total > maxWidth) caseH = caseH * ((maxWidth - gap) / (total - gap))
        caseH = min(caseH, height)
        val budsH = caseH * ratio
        Row(verticalAlignment = Alignment.Bottom) {
            Image(buds, model?.name, contentScale = ContentScale.Fit, modifier = Modifier.size(budsH * budsAr, budsH))
            Spacer(Modifier.width(gap))
            Image(case, null, contentScale = ContentScale.Fit, modifier = Modifier.size(caseH * caseAr, caseH))
        }
    }
}
