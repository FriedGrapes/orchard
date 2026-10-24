package me.kavishdevar.librepods.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Orchard 자동 업데이트.
 * 12시간마다 공개 저장소의 최신 릴리스를 보고, 새 APK가 있으면 내려받아 PackageInstaller 로 설치한다.
 * 안드로이드 12+ 에서는 두 번째 업데이트부터 확인 창 없이 설치되고, 확인이 필요하면 알림을 띄운다.
 */
object AppUpdater {
    private const val TAG = "OrchardUpdater"
    private const val LATEST = "https://api.github.com/repos/FriedGrapes/orchard/releases/latest"
    private const val CHANNEL = "orchard_updates"
    const val PREF_AUTO = "orchard_auto_update"
    private const val PREF_LAST = "orchard_update_last_check"
    private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

    @Volatile private var running = false

    /** '지금 업데이트 확인' 결과. */
    sealed interface CheckResult {
        data class UpToDate(val current: String) : CheckResult
        data class Installing(val version: String) : CheckResult
        data object Busy : CheckResult
        data object Failed : CheckResult
    }

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    fun schedule(context: Context, scope: CoroutineScope) {
        if (BuildConfig.PLAY_BUILD) return
        scope.launch {
            delay(30_000) // 앱 시작과 블루투스 연결을 먼저 하게 둔다
            while (true) {
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val due = System.currentTimeMillis() - prefs.getLong(PREF_LAST, 0) > INTERVAL_MS
                if (prefs.getBoolean(PREF_AUTO, true) && due) {
                    checkNow(context)
                    prefs.edit { putLong(PREF_LAST, System.currentTimeMillis()) }
                }
                delay(60 * 60 * 1000L)
            }
        }
    }

    /** 확인하고, 새 버전이면 내려받아 설치를 시작한다(네트워크를 쓰니 IO 스레드에서 부른다). */
    fun checkNow(context: Context): CheckResult {
        if (running) return CheckResult.Busy
        running = true
        try {
            val json = fetch(LATEST) ?: return CheckResult.Failed
            val release = UpdateLogic.parseRelease(json) ?: return CheckResult.UpToDate(currentVersion)
            if (!UpdateLogic.isNewer(release.version, BuildConfig.VERSION_NAME)) return CheckResult.UpToDate(currentVersion)
            Log.i(TAG, "New version ${release.version}, downloading")
            val apk = download(context, release) ?: return CheckResult.Failed
            install(context, apk, release.version)
            return CheckResult.Installing(release.version)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return CheckResult.Failed
        } finally {
            running = false
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Orchard-Android/${BuildConfig.VERSION_NAME}")
        }

    private fun fetch(url: String): String? {
        val c = open(url)
        return try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun download(context: Context, r: UpdateLogic.Release): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Orchard-${r.version}.apk")
        val tmp = File(dir, out.name + ".part")
        val c = open(r.apkUrl)
        try {
            if (c.responseCode != 200) return null
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        if (r.size > 0 && tmp.length() != r.size) {
            Log.w(TAG, "Size mismatch: ${tmp.length()} != ${r.size}")
            tmp.delete()
            return null
        }
        tmp.renameTo(out)
        return out
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun install(context: Context, apk: File, version: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { input ->
                s.openWrite("orchard.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    s.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setPackage(context.packageName)
                .putExtra("version", version)
            val pi = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            s.commit(pi.intentSender)
        }
    }

    internal fun notify(context: Context, title: String, text: String, tap: PendingIntent?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        nm.notify(7301, n)
    }
}

/** 설치 결과. 사용자 확인이 필요하면(첫 업데이트 등) 알림을 눌러 설치 창을 연다. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val version = intent.getStringExtra("version") ?: ""
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    context, 7301, confirm,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                AppUpdater.notify(context, "Orchard v$version", "Tap to install the update", pi)
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("OrchardUpdater", "Installed v$version")
            else -> Log.w(
                "OrchardUpdater",
                "Install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
            )
        }
    }
}
