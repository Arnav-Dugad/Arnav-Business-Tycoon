package com.arnavgames.tycoon.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import android.os.Build
import com.arnavgames.tycoon.BuildConfig
import com.arnavgames.tycoon.game.GameSession
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** The signed-in player as the UI sees them. */
data class CloudUser(val uid: String, val email: String?, val verified: Boolean, val guest: Boolean, val google: Boolean)

/**
 * Where cloud calls go: the local emulator (debug builds, so tests never touch real players' data), the real project
 * (release builds made with google-services.json) or nowhere (release builds without it: the offline edition).
 */
enum class CloudMode { Live, Emulator, Offline }

sealed interface SyncResult {
    data class Pushed(val rev: Long) : SyncResult
    data class Pulled(val rev: Long) : SyncResult
    data object UpToDate : SyncResult
    /** Both this device and the cloud changed since the last sync: the player must choose (nothing is overwritten). */
    data class Conflict(val local: SaveSummary, val remote: SaveSummary) : SyncResult
    data class Failed(val reason: String) : SyncResult
}

data class SaveSummary(val rev: Long, val nwLog10: Double, val generation: Int, val playSeconds: Long, val device: String, val savedAt: Long)

/**
 * Firebase on the Spark plan (docs/ARCHITECTURE.md §7): Authentication (email, guest; Google once the project exists)
 * and Firestore cloud saves with revision lineage. The game never needs an account and works fully offline; this only
 * adds backup, sync and the social features.
 */
class CloudService(private val app: Context, private val session: GameSession) {
    val mode: CloudMode = when {
        (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 -> CloudMode.Emulator
        BuildConfig.HAS_FIREBASE -> CloudMode.Live
        else -> CloudMode.Offline
    }

    private val firebase: FirebaseApp? by lazy {
        runCatching {
            when (mode) {
                CloudMode.Live -> FirebaseApp.getApps(app).firstOrNull() ?: FirebaseApp.initializeApp(app)
                CloudMode.Emulator -> FirebaseApp.getApps(app).firstOrNull { it.name == EMULATOR_APP } ?: FirebaseApp.initializeApp(
                    app,
                    // A "demo-" project only talks to the local emulators: no real project, no keys.
                    FirebaseOptions.Builder().setProjectId(DEMO_PROJECT).setApplicationId("1:000000000000:android:0000000000000000").setApiKey("demo-api-key").build(),
                    EMULATOR_APP,
                ).also { fa ->
                    FirebaseAuth.getInstance(fa).useEmulator(EMULATOR_HOST, 9099)
                    FirebaseFirestore.getInstance(fa).useEmulator(EMULATOR_HOST, 8080)
                }
                CloudMode.Offline -> null
            }
        }.getOrNull()
    }

    val available get() = firebase != null
    private val auth get() = FirebaseAuth.getInstance(firebase!!)
    private val db get() = FirebaseFirestore.getInstance(firebase!!)
    private val prefs = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow<CloudUser?>(null)
    val user: StateFlow<CloudUser?> = _user.asStateFlow()

    /** Daily usage counters (docs/ARCHITECTURE.md §7.5): the app backs off well before the Spark quota. */
    private val _usage = MutableStateFlow(readUsage())
    val usage: StateFlow<Pair<Long, Long>> = _usage.asStateFlow()

    init {
        if (available) {
            auth.addAuthStateListener { a -> _user.value = a.currentUser?.let(::toUser) }
            _user.value = auth.currentUser?.let(::toUser)
        }
    }

    private fun toUser(u: com.google.firebase.auth.FirebaseUser) = CloudUser(
        u.uid, u.email, u.isEmailVerified, u.isAnonymous, u.providerData.any { it.providerId == "google.com" },
    )

    private fun today() = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
    private fun readUsage(): Pair<Long, Long> = if (prefs.getString("usageDay", "") == today()) prefs.getLong("reads", 0) to prefs.getLong("writes", 0) else 0L to 0L
    private fun count(reads: Long = 0, writes: Long = 0) {
        val (r, w) = readUsage()
        prefs.edit().putString("usageDay", today()).putLong("reads", r + reads).putLong("writes", w + writes).apply()
        _usage.value = readUsage()
    }

    /** Per-player daily budget (about 87 reads / 60 writes expected; hard stop well above that). */
    fun overBudget(): Boolean = readUsage().let { (r, w) -> r > 400 || w > 200 }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        if (!available) return@withContext Result.failure(IllegalStateException(offlineReason()))
        runCatching { block() }.recoverCatching { e ->
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) error("The cloud is busy today. Your game is safe on this device; try again tomorrow.")
            // The project exists but this sign-in method hasn't been switched on in the Firebase console yet.
            val msg = e.message.orEmpty()
            if (msg.contains("CONFIGURATION_NOT_FOUND") || (e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED"))
                error("Cloud sign-in isn't switched on yet. Your game is saved on this device.")
            throw e
        }
    }

    fun offlineReason() = when (mode) {
        CloudMode.Offline -> "This build has no cloud project yet. Your game is saved on this device."
        else -> "The cloud is not reachable right now."
    }

    // ---------------------------------------------------------------- accounts

    suspend fun signInGuest() = call { auth.signInAnonymously().await(); Unit }

    suspend fun signUp(email: String, password: String) = call {
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            // Upgrading a guest keeps the same account (and its cloud save).
            current.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        } else auth.createUserWithEmailAndPassword(email, password).await()
        auth.currentUser?.sendEmailVerification()?.await()
        refresh()
    }

    suspend fun signIn(email: String, password: String) = call { auth.signInWithEmailAndPassword(email, password).await(); refresh() }

    /**
     * Google's web client id from google-services.json (generated as a string resource once Google sign-in is enabled
     * in the Firebase console), or null: then the Google button is hidden.
     */
    @SuppressLint("DiscouragedApi")
    fun googleClientId(): String? {
        if (mode != CloudMode.Live) return null
        val id = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        return if (id != 0) app.getString(id) else null
    }

    /** Google sign-in through Credential Manager. A guest who signs in with Google keeps their account and cloud save. */
    suspend fun signInGoogle(activity: Context) = call {
        val clientId = googleClientId() ?: error("Google sign-in isn't available in this version.")
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            withContext(Dispatchers.Main) { CredentialManager.create(activity).getCredential(activity, request) }.credential
        } catch (e: GetCredentialCancellationException) {
            error("Google sign-in was cancelled.")
        } catch (e: NoCredentialException) {
            error("No Google account on this device. Add one in Android settings, or use email.")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) error("Unexpected sign-in response.")
        val firebaseCredential = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(firebaseCredential).await()
            } catch (e: FirebaseAuthUserCollisionException) {
                // This Google account already has a game account: sign into that one (the guest's local save stays here).
                auth.signInWithCredential(firebaseCredential).await()
                prefs.edit().remove("lastSyncedRev").apply()
            }
        } else auth.signInWithCredential(firebaseCredential).await()
        refresh()
    }

    suspend fun sendVerification(): Result<Unit> {
        val last = prefs.getLong("verifySentAt", 0)
        if (System.currentTimeMillis() - last < 60_000) return Result.failure(IllegalStateException("Please wait a minute before sending another email."))
        return call { auth.currentUser?.sendEmailVerification()?.await(); prefs.edit().putLong("verifySentAt", System.currentTimeMillis()).apply() }
    }

    suspend fun refresh() = call {
        auth.currentUser?.reload()?.await()
        // The security rules read email_verified from the ID token, so mint a fresh one after verifying.
        auth.currentUser?.getIdToken(true)?.await()
        _user.value = auth.currentUser?.let(::toUser)
    }

    suspend fun resetPassword(email: String) = call { auth.sendPasswordResetEmail(email).await(); Unit }

    fun signOut() { if (available) auth.signOut(); prefs.edit().remove("lastSyncedRev").apply() }

    /** Deletes this player's cloud data, then the account. The save on this device is kept. */
    suspend fun deleteAccount() = call {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        listOf("saves/$uid", "profiles/$uid", "social/$uid").forEach { runCatching { db.document(it).delete().await() } }
        count(writes = 3)
        auth.currentUser?.delete()?.await()
        prefs.edit().remove("lastSyncedRev").apply()
        Unit
    }

    // ---------------------------------------------------------------- cloud save

    private fun localSummary(rev: Long): SaveSummary {
        val s = session.state.value
        val nw = s?.let { session.engine.economy.netWorth(it).toDollarsDouble() } ?: 0.0
        return SaveSummary(rev, if (nw > 1) kotlin.math.log10(nw) else 0.0, s?.dynasty?.generation ?: 1, (s?.simTimeMs ?: 0) / 1000, Build.MODEL, System.currentTimeMillis())
    }

    private fun remoteSummary(d: com.google.firebase.firestore.DocumentSnapshot): SaveSummary {
        @Suppress("UNCHECKED_CAST") val sum = d.get("summary") as? Map<String, Any?> ?: emptyMap()
        return SaveSummary(
            d.getLong("rev") ?: 0, (sum["nwLog10"] as? Number)?.toDouble() ?: 0.0, (sum["gen"] as? Number)?.toInt() ?: 1,
            (sum["playSec"] as? Number)?.toLong() ?: 0, d.getString("device") ?: "", d.getTimestamp("savedAt")?.toDate()?.time ?: 0,
        )
    }

    /** True if the local game changed since the last sync (the session marks it dirty on every command). */
    fun localDirty(): Boolean = session.state.value?.let { (it.simTimeMs / 60_000) != prefs.getLong("syncedAtMinute", -1) } ?: false

    /**
     * Sync (docs/ARCHITECTURE.md §7.4): remote == last synced and local dirty → push; local clean and remote newer →
     * pull; both changed → conflict (the player chooses; nothing is overwritten silently).
     */
    suspend fun sync(): SyncResult {
        if (overBudget()) return SyncResult.Failed("Daily cloud budget reached; trying again tomorrow.")
        val uid = _user.value?.uid ?: return SyncResult.Failed("Sign in to use cloud saves.")
        return call {
            val ref = db.document("saves/$uid")
            val snap = ref.get().await().also { count(reads = 1) }
            val remoteRev = if (snap.exists()) snap.getLong("rev") ?: 0 else 0
            val lastSynced = prefs.getLong("lastSyncedRev", 0)
            val dirty = localDirty()
            when {
                remoteRev == lastSynced && (dirty || !snap.exists()) -> push(ref, remoteRev)
                remoteRev > lastSynced && !dirty -> pull(snap)
                remoteRev > lastSynced && dirty -> SyncResult.Conflict(localSummary(lastSynced), remoteSummary(snap))
                else -> SyncResult.UpToDate
            }
        }.getOrElse { SyncResult.Failed(it.message ?: "Cloud error") }
    }

    /** Conflict choice: keep this device (overwrite the cloud with a new revision) or use the cloud copy. */
    suspend fun resolve(keepLocal: Boolean): SyncResult {
        val uid = _user.value?.uid ?: return SyncResult.Failed("Not signed in")
        return call {
            val ref = db.document("saves/$uid")
            val snap = ref.get().await().also { count(reads = 1) }
            // The losing copy is kept on this device as a backup snapshot before anything changes.
            session.exportBytes()?.let { prefs.edit().putString("conflictBackup", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)).apply() }
            if (keepLocal) push(ref, snap.getLong("rev") ?: 0) else pull(snap)
        }.getOrElse { SyncResult.Failed(it.message ?: "Cloud error") }
    }

    private suspend fun push(ref: com.google.firebase.firestore.DocumentReference, remoteRev: Long): SyncResult {
        val bytes = session.exportBytes() ?: return SyncResult.Failed("Nothing to save yet")
        if (bytes.size > 900 * 1024) return SyncResult.Failed("Save too large for the cloud")
        val rev = remoteRev + 1
        val sum = localSummary(rev)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val data = mapOf(
            "rev" to rev, "schema" to 1L, "contentHash" to (session.state.value?.contentHash ?: ""), "device" to Build.MODEL.take(64),
            "savedAt" to FieldValue.serverTimestamp(), "summary" to mapOf("nwLog10" to sum.nwLog10, "gen" to sum.generation.toLong(), "playSec" to sum.playSeconds),
            "sha" to sha, "blob" to Blob.fromBytes(bytes),
        )
        ref.set(data).await()
        count(writes = 1)
        markSynced(rev)
        return SyncResult.Pushed(rev)
    }

    private suspend fun pull(snap: com.google.firebase.firestore.DocumentSnapshot): SyncResult {
        val blob = snap.getBlob("blob") ?: return SyncResult.Failed("Cloud save is empty")
        session.importBytes(blob.toBytes())
        val rev = snap.getLong("rev") ?: 0
        markSynced(rev)
        return SyncResult.Pulled(rev)
    }

    private fun markSynced(rev: Long) {
        prefs.edit().putLong("lastSyncedRev", rev).putLong("syncedAtMinute", (session.state.value?.simTimeMs ?: 0) / 60_000).apply()
    }

    fun lastSyncedRev() = prefs.getLong("lastSyncedRev", 0)

    // ---------------------------------------------------------------- shared helpers for social
    internal val firestore get() = db
    internal fun countReads(n: Long) = count(reads = n)
    internal fun countWrites(n: Long) = count(writes = n)
    internal suspend fun <T> cloud(block: suspend () -> T) = call(block)
    internal fun now(): Timestamp = Timestamp.now()
    internal fun merge() = SetOptions.merge()

    companion object {
        const val DEMO_PROJECT = "demo-arnav-business-tycoon"
        const val EMULATOR_APP = "abt-emulator"
        /** The host machine as seen from the Android emulator. */
        const val EMULATOR_HOST = "10.0.2.2"
    }
}
