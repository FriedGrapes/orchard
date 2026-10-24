package me.kavishdevar.librepods.update

import org.json.JSONObject

/** Orchard: 자동 업데이트의 순수 로직(버전 비교, 릴리스에서 APK 고르기). */
object UpdateLogic {
    data class Release(val version: String, val apkUrl: String, val size: Long, val notes: String)

    private fun parts(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").substringBefore('-')
        val p = core.split('.')
        if (p.isEmpty() || p.size > 4) return null
        return p.map { it.toIntOrNull() ?: return null }
    }

    /** remote 가 current 보다 새 버전이면 true. 읽을 수 없는 버전은 false. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote) ?: return false
        val c = parts(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun parseRelease(json: String): Release? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.startsWith("Orchard-android") && name.endsWith(".apk")) {
                return Release(
                    version = o.getString("tag_name").removePrefix("v"),
                    apkUrl = a.getString("browser_download_url"),
                    size = a.optLong("size"),
                    notes = o.optString("body"),
                )
            }
        }
        null
    }.getOrNull()
}
