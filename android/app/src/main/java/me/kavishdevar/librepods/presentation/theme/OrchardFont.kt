package me.kavishdevar.librepods.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import me.kavishdevar.librepods.R

/** Orchard: 원본의 SF Pro(애플 글꼴, 재배포 불가) 대신 Pretendard(OFL, 한글이 고움). */
val OrchardFont = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)
