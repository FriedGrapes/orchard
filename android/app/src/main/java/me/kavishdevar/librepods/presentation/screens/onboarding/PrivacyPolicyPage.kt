package me.kavishdevar.librepods.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R

/** Orchard 개인정보 안내. 원본(LibrePods)의 결제·후원·문의 안내는 Orchard 에 맞지 않아 새로 썼다. */
@Composable
fun PrivacyPolicyPage(
    onForward: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(42.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "고친 날: 2026년 10월 25일",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Section(
                "모으는 정보",
                "Orchard는 개인정보를 모으거나 밖으로 보내지 않아요. 광고·분석·추적·오류 보고 기능도 없어요."
            )
            Section(
                "인터넷을 쓰는 곳",
                "새 버전이 있는지 GitHub(github.com/FriedGrapes/orchard)에서 확인하고 받을 때만 인터넷을 써요. " +
                    "설정 › 앱 업데이트에서 끌 수 있어요."
            )
            Section(
                "위치",
                "'에어팟 찾기'를 켜면 에어팟 연결이 끊긴 순간의 위치를 이 폰 안에만 저장해요. 어디로도 보내지 않아요."
            )
            Section(
                "에어팟 정보",
                "배터리·착용 상태·기기 정보는 에어팟과 이 폰 사이에서만 오가요."
            )
            Section(
                "원본 프로젝트",
                "Orchard는 LibrePods(GPL-3.0)를 바탕으로 만든 앱이에요. 소스는 모두 공개돼 있어요."
            )

            Button(
                onClick = onForward,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.i_agree),
                    style = MaterialTheme.typography.labelMediumEmphasized
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge)
    Text(text = body, style = MaterialTheme.typography.bodyMedium)
}
