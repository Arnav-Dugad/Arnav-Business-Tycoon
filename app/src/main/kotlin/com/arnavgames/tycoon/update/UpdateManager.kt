package com.arnavgames.tycoon.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.arnavgames.tycoon.BuildConfig
import com.arnavgames.tycoon.data.settings.SettingsStore
import com.arnavgames.tycoon.game.GameSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the updater is doing, for the update card. */
sealed interface UpdateState {
    data object Idle : UpdateState
    /** A newer release exists but isn't downloaded (automatic updates off, mobile data, or no APK asset). */
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Downloaded and verified. [needsPermission]: Android's "install unknown apps" switch is off for this game. */
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/**
 * Automatic updates for the GitHub-distributed APK (docs/ARCHITECTURE.md §9).
 *
 * 1. Checks GitHub every 6 hours ([UpdateChecker]).
 * 2. With automatic updates on, downloads the APK in the background on an unmetered network.
 * 3. Verifies it before anything is installed: the size and SHA-256 GitHub reported, the package name, a higher
 *    version code and the same signing certificate as the installed game (Android also refuses a different key).
 * 4. Installs it with [PackageInstaller] after the game is saved: silently when the player leaves the game (Android
 *    12+, once the player has allowed this game to install apps), otherwise when they tap "Install" and confirm.
 *
 * Nothing is installed without the player's one-time "install unknown apps" permission, and the setting can be off.
 */
class UpdateManager(
    private val app: Context,
    private val session: GameSession,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    val checker = UpdateChecker(app)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The version the game was updated from on this launch ("1.0.0"), shown once. */
    private val _updatedFrom = MutableStateFlow<String?>(null)
    val updatedFrom: StateFlow<String?> = _updatedFrom.asStateFlow()

    private val dir get() = File(app.noBackupFilesDir, "updates").apply { mkdirs() }
    private val prefs by lazy { app.getSharedPreferences("update", Context.MODE_PRIVATE) }
    private var job: Job? = null
    /** The system's confirmation screen for a pending install, kept until the player taps Install. */
    @Volatile private var confirmIntent: Intent? = null

    init {
        // Off the main thread: the first SharedPreferences read touches the disk.
        scope.launch(Dispatchers.IO) {
            val last = prefs.getString("lastRunVersion", null)
            if (last != null && last != BuildConfig.VERSION_NAME) _updatedFrom.value = last
            prefs.edit().putString("lastRunVersion", BuildConfig.VERSION_NAME).apply()
        }
    }

    fun consumeUpdatedFrom() { _updatedFrom.value = null }

    private fun apkFile(info: UpdateInfo) = File(dir, "${info.version.removePrefix("v")}.apk")

    /** On every return to the game: check (at most every 6 h unless [force]) and download if allowed. */
    fun onForeground(force: Boolean = false) {
        scope.launch(Dispatchers.IO) { check(force) }
    }

    suspend fun check(force: Boolean) {
        val cur = _state.value
        if (cur is UpdateState.Downloading || cur is UpdateState.Installing) return
        cleanup()
        val info = checker.check(force) ?: run { _state.value = UpdateState.Idle; return }
        val file = apkFile(info)
        if (file.exists() && verify(info, file) == null) { _state.value = UpdateState.Ready(info, !canInstall()); return }
        if (cur is UpdateState.Failed && cur.info == info && !force) return
        _state.value = UpdateState.Available(info)
        if (info.apkUrl != null && settings.settings.first().autoUpdate && (force || unmetered())) download(info)
    }

    /** Starts (or joins) the download of [info]'s APK. */
    fun download(info: UpdateInfo) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val url = info.apkUrl ?: return@launch
            val out = apkFile(info)
            val tmp = File(dir, out.name + ".part")
            _state.value = UpdateState.Downloading(info, 0f)
            val error = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", UpdateChecker.USER_AGENT)
                c.setRequestProperty("Accept", "application/octet-stream")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
                if (total > MAX_APK_BYTES) error("The update is unexpectedly large.")
                var done = 0L
                var lastShown = 0f
                c.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done > MAX_APK_BYTES) error("The update is unexpectedly large.")
                            val p = if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else 0f
                            if (p - lastShown >= 0.02f) { lastShown = p; _state.value = UpdateState.Downloading(info, p) }
                        }
                    }
                }
                c.disconnect()
                verify(info, tmp)?.let { error(it) }
                out.delete()
                if (!tmp.renameTo(out)) error("Could not store the update.")
            }.exceptionOrNull()
            if (error != null) {
                tmp.delete()
                Log.w(TAG, "update download failed", error)
                _state.value = UpdateState.Failed(info, error.message ?: "Download failed. It will be tried again later.")
            } else {
                _state.value = UpdateState.Ready(info, !canInstall())
            }
        }
    }

    /**
     * Checks a downloaded APK before it may be installed; returns why it was rejected, or null if it is safe.
     * Size and SHA-256 must match GitHub's numbers; package, version and signer must match this game.
     */
    fun verify(info: UpdateInfo, file: File): String? {
        if (info.apkSize > 0 && file.length() != info.apkSize) return "The download was incomplete."
        info.sha256?.let { expected -> if (sha256(file) != expected) return "The download didn't match the release checksum." }
        val pm = app.packageManager
        val archive = archiveInfo(pm, file) ?: return "The download isn't a valid app."
        if (archive.packageName != app.packageName) return "The download is a different app."
        val installed = pm.getPackageInfo(app.packageName, 0)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return "The download isn't newer than this version."
        // Some Android versions don't report an archive's signer; the system installer still rejects a different key.
        val theirs = signers(archive) ?: return null
        val ours = signers(pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)) ?: return null
        if (theirs != ours) return "The download isn't signed by Arnav Games."
        return null
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)

    private fun signers(p: PackageInfo): Set<String>? {
        val si = p.signingInfo ?: return null
        val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return certs?.map { sha256(it.toByteArray()) }?.toSet()?.takeIf { it.isNotEmpty() }
    }

    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    /** Opens Android's "install unknown apps" switch for this game. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Re-reads the permission when the player comes back from Settings. */
    fun refreshPermission() {
        val s = _state.value
        if (s is UpdateState.Ready) _state.value = s.copy(needsPermission = !canInstall())
    }

    /** When the game goes to the background: install a verified update silently if everything allows it. */
    fun onBackground() {
        val s = _state.value as? UpdateState.Ready ?: return
        scope.launch {
            if (!settings.settings.first().autoUpdate || !canInstall() || Build.VERSION.SDK_INT < 31) return@launch
            install(s.info, fromUser = false)
        }
    }

    /** "Install now". Shows the system confirmation if Android asks for one (always on Android 11 and older). */
    fun installNow() {
        confirmIntent?.let { intent ->
            confirmIntent = null
            runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onSuccess { return }
        }
        val s = _state.value as? UpdateState.Ready ?: return
        scope.launch { install(s.info, fromUser = true) }
    }

    private suspend fun install(info: UpdateInfo, fromUser: Boolean) = withContext(Dispatchers.IO) {
        val file = apkFile(info)
        val problem = if (!file.exists()) "The update file is missing." else verify(info, file)
        if (problem != null) { file.delete(); _state.value = UpdateState.Failed(info, problem); return@withContext }
        // The running game is replaced, so its progress is written first.
        session.saveNow()
        _state.value = UpdateState.Installing(info)
        runCatching {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(app, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION).putExtra(UpdateReceiver.EXTRA_FROM_USER, fromUser)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(app, id, status, flags).intentSender)
            }
        }.onFailure { e ->
            Log.w(TAG, "install failed", e)
            _state.value = UpdateState.Failed(info, "Couldn't start the installer: ${e.message}")
        }
    }

    /** From [UpdateReceiver]: Android wants the player to confirm. */
    internal fun onNeedsConfirmation(intent: Intent?, fromUser: Boolean, foreground: Boolean) {
        val info = (_state.value as? UpdateState.Installing)?.info ?: return
        if (intent != null && fromUser && foreground) {
            runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { confirmIntent = intent }
        } else confirmIntent = intent
        _state.value = UpdateState.Ready(info, needsPermission = false)
    }

    internal fun onInstallFailed(message: String?) {
        val info = (_state.value as? UpdateState.Installing)?.info ?: return
        // The player closing the confirmation is not an error: keep the update ready.
        _state.value = if (message == null) UpdateState.Ready(info, !canInstall()) else UpdateState.Failed(info, message)
    }

    private fun unmetered(): Boolean = runCatching { !app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(false)

    /** Deletes downloads that are not newer than the running version (and stale partial files). */
    private fun cleanup() {
        dir.listFiles()?.forEach { f ->
            val v = f.name.removeSuffix(".part").removeSuffix(".apk")
            if (f.name.endsWith(".part") && job?.isActive != true) f.delete()
            else if (!UpdateChecker.isNewer(v, BuildConfig.VERSION_NAME.substringBefore('-'))) f.delete()
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val MAX_APK_BYTES = 150L * 1024 * 1024

        fun sha256(file: File): String = file.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
