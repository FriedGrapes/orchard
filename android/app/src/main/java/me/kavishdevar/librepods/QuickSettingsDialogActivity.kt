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


@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.components.ControlCenterSheetContent
import me.kavishdevar.librepods.presentation.components.ControlCenterSpec
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.services.AirPodsService
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 빠른 설정 ANC 타일(누르기·꾹 누르기)로 여는 에어팟 제어 센터.
 * Orchard v0.2.4: 화면 전체를 어둡게만 덮던 원본 대신, 아래에서 올라오는 검정 카드 한 장(시안 A).
 * 바깥을 누르거나 아래로 밀거나 뒤로 가기를 누르면 닫힌다.
 */
class QuickSettingsDialogActivity : ComponentActivity() {

    private var airPodsService by mutableStateOf<AirPodsService?>(null)
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AirPodsService.LocalBinder
            airPodsService = binder.getService()
            isBound = true
            Log.d("QSActivity", "Service bound")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            airPodsService = null
            Log.d("QSActivity", "Service unbound")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        // 뒤 화면을 흐리게(기기가 창 흐림을 지원할 때) — 뒤의 글자가 비쳐 어수선하지 않게
        window.attributes = window.attributes.apply { blurBehindRadius = 60 }
        window.setGravity(Gravity.BOTTOM)

        Intent(this, AirPodsService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        setContent {
            LibrePodsTheme {
                ControlCenterSheetHost(onDismiss = { finish(); overridePendingTransition(0, 0) }) {
                    airPodsService?.let { NewControlCenterDialogContent(it) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

/**
 * 어두운 바탕 + 아래 카드. 카드는 뚜껑 카드와 같은 스프링으로 올라오고, 닫을 때는 강한 ease-out으로 내려간다.
 * 카드를 아래로 밀면 손가락을 따라 내려가고(위로는 살짝만 늘어남), 1/4 넘게 밀면 닫힌다.
 */
@Composable
fun ControlCenterSheetHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(0f) }   // 0 = 화면 밖, 1 = 제자리
    val drag = remember { Animatable(0f) }       // 아래로 민 거리(px)
    var sheetH by remember { mutableIntStateOf(1800) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (reduceMotion) progress.snapTo(1f)
        else progress.animateTo(1f, spring(ControlCenterSpec.EnterDamping, ControlCenterSpec.EnterStiffness))
    }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            if (!reduceMotion) progress.animateTo(0f, tween(ControlCenterSpec.ExitMs, easing = ControlCenterSpec.ExitEasing))
            onDismiss()
        }
    }
    BackHandler { close() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pulled = (drag.value / sheetH).coerceIn(0f, 1f)
                    alpha = progress.value.coerceIn(0f, 1f) * (1f - pulled * 0.7f)
                }
                .background(Color.Black.copy(alpha = ControlCenterSpec.Scrim))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = ControlCenterSpec.Margin)
                .padding(bottom = ControlCenterSpec.Margin)
                .onSizeChanged { sheetH = it.height }
                .graphicsLayer {
                    translationY = (1f - progress.value) * (sheetH + 24.dp.toPx()) + drag.value
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag.value > sheetH * 0.25f) close()
                            else scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f)) } },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            val next = drag.value + dy
                            // 위로는 고무줄처럼 조금만
                            scope.launch { drag.snapTo(if (next < 0f) next * 0.25f else next) }
                        }
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            content()
        }
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@Composable
fun NewControlCenterDialogContent(service: AirPodsService) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val isOffModeEnabled = remember { sharedPreferences.getBoolean("off_listening_mode", true) }
    val availableModes = remember(isOffModeEnabled) {
        buildList {
            if (isOffModeEnabled) add(NoiseControlMode.OFF)
            add(NoiseControlMode.TRANSPARENCY)
            add(NoiseControlMode.ADAPTIVE)
            add(NoiseControlMode.NOISE_CANCELLATION)
        }
    }
    var currentAncMode by remember {
        val m = NoiseControlMode.entries.getOrElse(service.getANC() - 1) { NoiseControlMode.TRANSPARENCY }
        mutableStateOf(if (m in availableModes) m else NoiseControlMode.TRANSPARENCY)
    }
    var battery by remember { mutableStateOf<List<Battery>>(service.batteryNotification.getBattery()) }
    val model = remember { service.artModel() }
    val deviceName = remember { sharedPreferences.getString("name", "AirPods") ?: "AirPods" }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolumeInt by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val volumeFraction by animateFloatAsState(
        targetValue = currentVolumeInt.toFloat() / maxVolume,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "VolumeAnimation"
    )

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    AirPodsNotifications.ANC_DATA -> {
                        val m = NoiseControlMode.entries.getOrElse(intent.getIntExtra("data", 0) - 1) { NoiseControlMode.TRANSPARENCY }
                        if (m in availableModes) currentAncMode = m
                        else if (m == NoiseControlMode.OFF) currentAncMode = NoiseControlMode.TRANSPARENCY
                    }
                    AirPodsNotifications.BATTERY_DATA ->
                        intent.getParcelableArrayListExtra("data", Battery::class.java)?.let { battery = it }
                    "android.media.VOLUME_CHANGED_ACTION" ->
                        currentVolumeInt = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AirPodsNotifications.ANC_DATA)
            addAction(AirPodsNotifications.BATTERY_DATA)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    ControlCenterSheetContent(
        name = deviceName,
        model = model,
        battery = battery,
        volumeFraction = volumeFraction,
        onVolumeFraction = { f ->
            val v = Math.round(f * maxVolume)
            if (v != currentVolumeInt) {
                currentVolumeInt = v
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                } catch (e: Exception) { Log.e("QSActivity", "Failed to set volume", e) }
            }
        },
        onVolumeDrag = { },
        modes = availableModes,
        selected = currentAncMode,
        onSelect = { m ->
            service.aacpManager.sendControlCommand(
                identifier = AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value,
                value = m.ordinal + 1
            )
            currentAncMode = m
        },
    )
}
