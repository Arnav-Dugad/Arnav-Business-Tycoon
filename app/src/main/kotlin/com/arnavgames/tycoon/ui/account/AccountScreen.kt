package com.arnavgames.tycoon.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.cloud.CloudMode
import com.arnavgames.tycoon.cloud.SyncResult
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Account and cloud save (docs/ARCHITECTURE.md §7.1, §7.4). Everything here is optional: the game works offline. */
@Composable
fun AccountScreen(vm: GameViewModel, onBack: () -> Unit) {
    val cloud = vm.cloud
    val user by cloud.user.collectAsStateWithLifecycle()
    val usage by cloud.usage.collectAsStateWithLifecycle()
    val c = Aurum.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<SyncResult.Conflict?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun run(label: String, block: suspend () -> Result<*>) {
        busy = true
        scope.launch {
            val r = block()
            busy = false
            vm.say(if (r.isSuccess) label else (r.exceptionOrNull()?.message ?: "Something went wrong"))
        }
    }

    fun sync() {
        busy = true
        scope.launch {
            val r = cloud.sync()
            busy = false
            when (r) {
                is SyncResult.Pushed -> vm.say("Backed up to the cloud (revision ${r.rev}).")
                is SyncResult.Pulled -> vm.say("Loaded the newer cloud save (revision ${r.rev}).")
                SyncResult.UpToDate -> vm.say("Everything is up to date.")
                is SyncResult.Conflict -> conflict = r
                is SyncResult.Failed -> vm.say(r.reason)
            }
        }
    }

    LaunchedEffect(user?.uid) { if (user != null) cloud.refresh() }
    // A background backup found that both this device and the cloud changed: ask now.
    val backgroundConflict by vm.cloudConflict.collectAsStateWithLifecycle()
    LaunchedEffect(backgroundConflict) { if (backgroundConflict) { vm.clearCloudConflict(); sync() } }

    Box(Modifier.fillMaxSize()) {
        GlassScaffold(title = "Account", onBack = onBack) {
            item(key = "mode") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Cloud, null, tint = c.goldText, size = 24.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text("CLOUD", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
                        AurumChip(when (cloud.mode) { CloudMode.Live -> "Online"; CloudMode.Emulator -> "Test server"; CloudMode.Offline -> "Offline edition" }, tint = c.textSecondary)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        when (cloud.mode) {
                            CloudMode.Offline -> "This build has no cloud project yet, so accounts, cloud saves and friends are switched off. Your game is saved on this device and works fully offline."
                            CloudMode.Emulator -> "Debug build: accounts and cloud saves use the local Firebase test server on this computer."
                            CloudMode.Live -> "An account is optional. It backs up your game, syncs it between devices and lets you add friends."
                        },
                        style = Aurum.type.bodySmall, color = c.textSecondary,
                    )
                }
            }
            if (!cloud.available) return@GlassScaffold
            val u = user
            if (u == null) {
                item(key = "signin") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Text("Sign in or create an account", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Spacer(Modifier.height(Space.sm))
                        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("email"))
                        Spacer(Modifier.height(Space.xs))
                        OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("password"))
                        Spacer(Modifier.height(Space.sm))
                        val valid = email.contains('@') && password.length >= 8
                        GoldButton("Create account", enabled = valid && !busy, onClick = { run("Account created. Check your email to verify it.") { cloud.signUp(email, password) } }, modifier = Modifier.fillMaxWidth())
                        GhostButton("Sign in", enabled = valid && !busy, onClick = { run("Signed in.") { cloud.signIn(email, password) } }, modifier = Modifier.fillMaxWidth())
                        GhostButton("Forgot password", enabled = email.contains('@') && !busy, onClick = { run("Password reset email sent.") { cloud.resetPassword(email) } }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Space.xs))
                        GhostButton("Continue as guest", enabled = !busy, onClick = { run("Signed in as a guest. You can upgrade to an email account any time.") { cloud.signInGuest() } }, modifier = Modifier.fillMaxWidth())
                        if (cloud.googleClientId() != null) {
                            GhostButton("Continue with Google", enabled = !busy, onClick = { run("Signed in with Google.") { cloud.signInGoogle(ctx) } }, modifier = Modifier.fillMaxWidth().testTag("google-signin"))
                        }
                    }
                }
            } else {
                item(key = "profile") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Text(if (u.guest) "Guest account" else (u.email ?: "Signed in"), style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text(if (u.guest) "Cloud saves work. Friends and leaderboards need an email account." else if (u.verified || u.google) "Verified" else "Not verified yet",
                            style = Aurum.type.bodySmall, color = if (!u.guest && !u.verified && !u.google) c.loss else c.textSecondary)
                        if (!u.guest && !u.verified && !u.google) {
                            Spacer(Modifier.height(Space.sm))
                            AurumCard(Modifier.fillMaxWidth().testTag("verify-banner"), highlighted = true) {
                                Text("Verify your email to use friends, gifts, leaderboards and Sealed Lots.", style = Aurum.type.bodySmall, color = c.textPrimary)
                                Spacer(Modifier.height(Space.xs))
                                GhostButton("I've verified", enabled = !busy, onClick = { run("Checked.") { cloud.refresh() } }, modifier = Modifier.fillMaxWidth().testTag("verified-check"))
                                GhostButton("Resend email", enabled = !busy, onClick = { run("Verification email sent.") { cloud.sendVerification() } }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (u.guest) {
                            Spacer(Modifier.height(Space.sm))
                            Text("Upgrade to an email account (keeps this cloud save)", style = Aurum.type.label, color = c.goldText)
                            OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                            GoldButton("Upgrade account", enabled = email.contains('@') && password.length >= 8 && !busy, onClick = { run("Account upgraded. Check your email to verify it.") { cloud.signUp(email, password) } }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item(key = "save-h") { SectionHeader("Cloud save") }
                item(key = "save") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Text(if (cloud.lastSyncedRev() > 0) "Synced revision ${cloud.lastSyncedRev()}" else "Not backed up yet", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("Your game backs up automatically when you leave it. Syncing never overwrites anything silently: if both this device and the cloud changed, you choose.", style = Aurum.type.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(Space.sm))
                        GoldButton("Sync now", enabled = !busy, onClick = { sync() }, modifier = Modifier.fillMaxWidth().testTag("sync-now"))
                        Text("Today: ${usage.first} reads, ${usage.second} writes", style = Aurum.type.label, color = c.textTertiary)
                    }
                }
                item(key = "danger") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        GhostButton("Sign out", enabled = !busy, onClick = { cloud.signOut(); vm.say("Signed out. Your game stays on this device.") }, modifier = Modifier.fillMaxWidth())
                        GhostButton("Delete account", enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        conflict?.let { cf ->
            AurumDialog(title = "Two different saves", onDismiss = { conflict = null }) {
                Text("This device and the cloud both changed since the last sync. Choose which one to keep; the other is kept on this device as a backup.", style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.sm))
                SaveCard("This device", cf.local)
                Spacer(Modifier.height(Space.xs))
                SaveCard("Cloud", cf.remote)
                Spacer(Modifier.height(Space.md))
                GoldButton("Keep this device", onClick = { conflict = null; scope.launch { vm.say(syncText(cloud.resolve(keepLocal = true))) } }, modifier = Modifier.fillMaxWidth().testTag("keep-local"))
                GhostButton("Use cloud", onClick = { conflict = null; scope.launch { vm.say(syncText(cloud.resolve(keepLocal = false))) } }, modifier = Modifier.fillMaxWidth().testTag("use-cloud"))
                GhostButton("Decide later", onClick = { conflict = null }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (confirmDelete) {
            AurumDialog(title = "Delete your account?", onDismiss = { confirmDelete = false }) {
                Text("Your cloud save, profile and friends list are deleted, then the account. The game on this device is kept.", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Delete account", onClick = { confirmDelete = false; run("Account deleted.") { cloud.deleteAccount() } }, modifier = Modifier.fillMaxWidth())
                GhostButton("Cancel", onClick = { confirmDelete = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun syncText(r: SyncResult) = when (r) {
    is SyncResult.Pushed -> "Kept this device (cloud revision ${r.rev})."
    is SyncResult.Pulled -> "Loaded the cloud save."
    SyncResult.UpToDate -> "Up to date."
    is SyncResult.Conflict -> "Still in conflict."
    is SyncResult.Failed -> r.reason
}

@Composable
private fun SaveCard(label: String, s: com.arnavgames.tycoon.cloud.SaveSummary) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth()) {
        Text(label, style = Aurum.type.titleSmall, color = c.textPrimary)
        Text("Net worth 10^${String.format(Locale.US, "%.1f", s.nwLog10)} · generation ${s.generation} · ${s.playSeconds / 3600} h played", style = Aurum.type.bodySmall, color = c.textSecondary)
        if (s.device.isNotBlank() || s.savedAt > 0) Text(listOfNotNull(s.device.ifBlank { null }, if (s.savedAt > 0) DateFormat.getDateTimeInstance().format(Date(s.savedAt)) else null).joinToString(" · "), style = Aurum.type.label, color = c.textTertiary)
    }
}
