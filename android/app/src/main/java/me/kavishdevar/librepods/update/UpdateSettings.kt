package me.kavishdevar.librepods.update

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledToggle

/** 화면에 보이는 확인 상태. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Done(val result: AppUpdater.CheckResult) : UpdateStatus
}

/**
 * Orchard: 설정 화면의 '앱 업데이트' 묶음 — 자동 업데이트 스위치 + '지금 업데이트 확인'.
 * [check] 는 시험·미리보기에서 네트워크 없이 결과를 넣으려고 바꿀 수 있게 둔다.
 */
@Composable
fun UpdateSettingsSection(
    initialStatus: UpdateStatus = UpdateStatus.Idle,
    check: suspend (Context) -> AppUpdater.CheckResult = { ctx -> withContext(Dispatchers.IO) { AppUpdater.checkNow(ctx) } },
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean(AppUpdater.PREF_AUTO, true)) }
    var status by remember { mutableStateOf(initialStatus) }
    val scope = rememberCoroutineScope()

    StyledToggle(
        title = stringResource(R.string.orchard_update_title),
        label = stringResource(R.string.orchard_update_auto),
        description = stringResource(R.string.orchard_update_auto_desc),
        checked = auto,
        onCheckedChange = {
            auto = it
            prefs.edit { putBoolean(AppUpdater.PREF_AUTO, it) }
        },
    )

    val description = when (val s = status) {
        UpdateStatus.Idle -> stringResource(R.string.orchard_update_current, AppUpdater.currentVersion)
        UpdateStatus.Checking -> stringResource(R.string.orchard_update_checking)
        is UpdateStatus.Done -> when (val r = s.result) {
            is AppUpdater.CheckResult.UpToDate -> stringResource(R.string.orchard_update_latest, r.current)
            is AppUpdater.CheckResult.Installing -> stringResource(R.string.orchard_update_installing, r.version)
            AppUpdater.CheckResult.Busy -> stringResource(R.string.orchard_update_checking)
            AppUpdater.CheckResult.Failed -> stringResource(R.string.orchard_update_failed)
        }
    }
    StyledListItem(
        name = stringResource(R.string.orchard_update_check_now),
        description = description,
        enabled = status != UpdateStatus.Checking,
        onClick = {
            if (status == UpdateStatus.Checking) return@StyledListItem
            status = UpdateStatus.Checking
            scope.launch { status = UpdateStatus.Done(check(context)) }
        },
        trailingContent = {
            // 결과 글자가 바뀔 때 부드럽게 바뀐다
            AnimatedContent(
                targetState = status is UpdateStatus.Checking,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "check",
            ) { checking ->
                if (checking) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = androidx.compose.ui.Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                } else {
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        },
    )
}
