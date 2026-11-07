package com.arnavgames.tycoon.update

import android.content.Context
import com.arnavgames.tycoon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * A newer GitHub release than the installed version. [apkUrl] is the release's APK asset (null if the release has
 * none), [sha256] the digest GitHub computed for it (lower-case hex, null if GitHub didn't report one).
 */
data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String,
    val apkUrl: String? = null,
    val apkSize: Long = 0,
    val sha256: String? = null,
)

/**
 * In-app update check (docs/ARCHITECTURE.md §9): asks GitHub's public releases API for the latest release of the
 * game's repository at most every [INTERVAL_MS]. Sends no personal data (generic User-Agent); a failure (offline,
 * rate limit) is silently ignored and the last answer is reused.
 */
class UpdateChecker(private val app: Context, private val endpoint: String = BuildConfig.UPDATE_API) {
    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checkedAt", 0) < INTERVAL_MS) return@withContext cached()
        runCatching {
            val c = URL(endpoint).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", USER_AGENT)
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val info = parse(body) ?: return@runCatching null
            prefs.edit().putLong("checkedAt", now).putString("tag", info.version).putString("url", info.url).putString("notes", info.notes)
                .putString("apkUrl", info.apkUrl).putLong("apkSize", info.apkSize).putString("sha256", info.sha256).apply()
        }
        cached()
    }

    private fun cached(): UpdateInfo? {
        val tag = prefs.getString("tag", null) ?: return null
        if (!isNewer(tag.removePrefix("v"), BuildConfig.VERSION_NAME.substringBefore('-'))) return null
        return UpdateInfo(
            tag, prefs.getString("url", "") ?: "", prefs.getString("notes", tag) ?: tag,
            prefs.getString("apkUrl", null), prefs.getLong("apkSize", 0), prefs.getString("sha256", null),
        )
    }

    companion object {
        /** How often the game asks GitHub (the API allows 60 unauthenticated requests an hour per IP). */
        const val INTERVAL_MS = 6 * 3_600_000L
        const val USER_AGENT = "ArnavBusinessTycoon-Android"

        /** Parses a GitHub "latest release" response. Pure, so it is unit-tested. */
        fun parse(body: String): UpdateInfo? {
            val o = Json.parseToJsonElement(body).jsonObject
            val tag = o.str("tag_name") ?: return null
            val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it.str("name")?.endsWith(".apk", ignoreCase = true) == true }
            return UpdateInfo(
                version = tag,
                url = o.str("html_url") ?: "https://github.com/${BuildConfig.GITHUB_REPO}/releases",
                notes = o.str("name") ?: tag,
                apkUrl = apk?.str("browser_download_url"),
                apkSize = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0,
                sha256 = apk?.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
            )
        }

        private fun JsonObject.str(key: String): String? = this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it != "null" }

        /** Semantic-version comparison: "1.2.0" > "1.1.9". */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = installed.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
