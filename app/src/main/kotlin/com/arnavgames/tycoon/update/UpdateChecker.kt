package com.arnavgames.tycoon.update

import android.content.Context
import com.arnavgames.tycoon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** A newer GitHub release than the installed version. */
data class UpdateInfo(val version: String, val url: String, val notes: String)

/**
 * In-app update check (docs/ARCHITECTURE.md §9): asks GitHub's public releases API for the latest release of the
 * game's repository at most once a day. Sends no personal data; a failure (offline, rate limit) is silently ignored.
 */
class UpdateChecker(private val app: Context) {
    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checkedAt", 0) < 24 * 3_600_000L) return@withContext cached()
        runCatching {
            val c = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "ArnavBusinessTycoon-Android")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val o = Json.parseToJsonElement(body).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
            val url = o["html_url"]?.jsonPrimitive?.content ?: "https://github.com/${BuildConfig.GITHUB_REPO}/releases"
            val notes = o["name"]?.jsonPrimitive?.content ?: tag
            prefs.edit().putLong("checkedAt", now).putString("tag", tag).putString("url", url).putString("notes", notes).apply()
            cached()
        }.getOrNull()
    }

    private fun cached(): UpdateInfo? {
        val tag = prefs.getString("tag", null) ?: return null
        return if (isNewer(tag.removePrefix("v"), BuildConfig.VERSION_NAME.substringBefore('-'))) UpdateInfo(tag, prefs.getString("url", "") ?: "", prefs.getString("notes", tag) ?: tag) else null
    }

    companion object {
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
