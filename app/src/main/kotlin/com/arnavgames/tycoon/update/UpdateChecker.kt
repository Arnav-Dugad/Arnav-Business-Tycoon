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
 * game's repository at most every [INTERVAL_MS]. Sends no personal data (generic User-Agent).
 *
 * GitHub limits that API to 60 requests an hour per internet address, and mobile networks often share one address
 * between many people. When the API refuses or fails, the checker falls back to the public release pages (the
 * "latest" redirect and the release's asset list), which have no such limit. Without the API there is no checksum,
 * but every download is still checked for this game's package, a newer version and the Arnav Games signature, and
 * Android itself refuses an update signed with another key. [lastCheckFailed] says whether the last check reached
 * GitHub at all, so the app never claims "up to date" after a failed check.
 */
class UpdateChecker(private val app: Context, private val endpoint: String = BuildConfig.UPDATE_API) {
    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** True when the last check couldn't reach GitHub by either route. */
    var lastCheckFailed = false
        private set

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checkedAt", 0) < INTERVAL_MS) return@withContext cached()
        val info = runCatching { fromApi() }.getOrNull()
            ?: if (endpoint == GITHUB_API) runCatching { fromReleasePages() }.getOrNull() else null
        lastCheckFailed = info == null
        if (info != null) {
            prefs.edit().putLong("checkedAt", now).putString("tag", info.version).putString("url", info.url).putString("notes", info.notes)
                .putString("apkUrl", info.apkUrl).putLong("apkSize", info.apkSize).putString("sha256", info.sha256).apply()
        }
        cached()
    }

    /** The releases API: version, APK, size and checksum. Throws on any refusal (rate limit, outage). */
    private fun fromApi(): UpdateInfo? {
        val c = URL(endpoint).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode != 200) error("API answered ${c.responseCode}")
            return parse(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    /** The public pages: the "latest" redirect gives the tag, the release's asset list gives the APK link. */
    private fun fromReleasePages(): UpdateInfo? {
        val repo = BuildConfig.GITHUB_REPO
        val latest = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
        latest.instanceFollowRedirects = false
        latest.connectTimeout = 8_000
        latest.readTimeout = 8_000
        latest.setRequestProperty("User-Agent", USER_AGENT)
        val tag = try { tagFromLatestRedirect(latest.getHeaderField("Location")) } finally { latest.disconnect() } ?: return null
        val assets = URL("https://github.com/$repo/releases/expanded_assets/$tag").openConnection() as HttpURLConnection
        assets.connectTimeout = 8_000
        assets.readTimeout = 8_000
        assets.setRequestProperty("User-Agent", USER_AGENT)
        val html = try {
            if (assets.responseCode != 200) error("assets page answered ${assets.responseCode}")
            assets.inputStream.bufferedReader().use { it.readText() }
        } finally { assets.disconnect() }
        return UpdateInfo(tag, "https://github.com/$repo/releases/tag/$tag", tag, apkFromAssetsHtml(html))
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
        const val GITHUB_API_PREFIX = "https://api.github.com/repos/"
        val GITHUB_API: String get() = "$GITHUB_API_PREFIX${BuildConfig.GITHUB_REPO}/releases/latest"

        /** ".../releases/tag/v1.3.0" -> "v1.3.0"; null for anything else. */
        fun tagFromLatestRedirect(location: String?): String? =
            location?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.takeIf { it.isNotBlank() && !it.contains('/') }

        /** The first APK link in a release's asset list, as an absolute URL. */
        fun apkFromAssetsHtml(html: String): String? =
            Regex("""href="(/[^"]+/releases/download/[^"]+\.apk)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let { "https://github.com$it" }

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
