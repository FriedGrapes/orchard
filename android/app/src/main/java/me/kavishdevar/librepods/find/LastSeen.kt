package me.kavishdevar.librepods.find

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * Orchard: 에어팟과 연결이 끊긴 순간의 폰 위치를 기억한다(에어팟을 두고 온 곳 찾기).
 * 에어팟 자체가 위치를 알리는 것이 아니라, '이 폰이 마지막으로 에어팟과 함께 있던 곳'이다.
 */
object LastSeen {
    data class Place(val lat: Double, val lon: Double, val accuracy: Float, val time: Long, val name: String)

    private const val PREFS = "orchard_last_seen"
    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place

    /** 근처 찾기용: 내 에어팟(IRK로 확인된 주소)의 가장 최근 신호 세기와 시각. */
    private val _rssi = MutableStateFlow<Pair<Int, Long>?>(null)
    val rssi: StateFlow<Pair<Int, Long>?> = _rssi

    /** 화면 시험·미리보기용. */
    internal fun setForPreview(place: Place?) {
        _place.value = place
    }

    fun onRssi(value: Int) {
        _rssi.value = value to System.currentTimeMillis()
    }

    fun load(context: Context): Place? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("time")) return null
        return Place(
            Double.fromBits(p.getLong("lat_bits", 0L)),
            Double.fromBits(p.getLong("lon_bits", 0L)),
            p.getFloat("accuracy", 0f),
            p.getLong("time", 0L),
            p.getString("name", "AirPods") ?: "AirPods",
        ).also { _place.value = it }
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val executor = Executors.newSingleThreadExecutor()

    /** 지금 폰 위치를 구해 저장한다. 권한이 없으면 조용히 넘어간다. */
    @SuppressLint("MissingPermission")
    fun record(context: Context, deviceName: String) {
        if (!hasPermission(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val provider = when {
            lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        val save = { loc: Location? ->
            val l = loc ?: provider?.let { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            if (l != null) save(context, Place(l.latitude, l.longitude, l.accuracy, System.currentTimeMillis(), deviceName))
        }
        try {
            if (provider != null) lm.getCurrentLocation(provider, null, executor) { save(it) } else save(null)
        } catch (e: Exception) {
            Log.w("OrchardLastSeen", "Location failed", e)
            save(null)
        }
    }

    private fun save(context: Context, place: Place) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("lat_bits", place.lat.toRawBits())
            putLong("lon_bits", place.lon.toRawBits())
            putFloat("accuracy", place.accuracy)
            putLong("time", place.time)
            putString("name", place.name)
        }
        _place.value = place
        Log.d("OrchardLastSeen", "Saved last seen place (±${place.accuracy}m)")
    }
}
