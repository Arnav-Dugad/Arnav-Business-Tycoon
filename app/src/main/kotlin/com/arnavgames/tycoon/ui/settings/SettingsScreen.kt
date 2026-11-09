package com.arnavgames.tycoon.ui.settings

import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.BuildConfig
import com.arnavgames.tycoon.data.settings.NotationPref
import com.arnavgames.tycoon.data.settings.ThemePref
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.AurumToggle
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.MenuRow
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit, onCredits: () -> Unit, onCheats: () -> Unit = {}, onSlots: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = Aurum.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) scope.launch { vm.exportSave(ctx, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { vm.importSave(ctx, uri) }
    }
    // Notifications: the Android permission is asked only when the player turns them on.
    var notifyBlocked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifyBlocked = !granted
        if (granted) vm.updateSettings { it.copy(notifications = true) }
    }
    val enableNotifications: (Boolean) -> Unit = { on ->
        when {
            !on -> { notifyBlocked = false; vm.updateSettings { it.copy(notifications = false) } }
            com.arnavgames.tycoon.notify.Notifications.canPost(ctx) -> vm.updateSettings { it.copy(notifications = true) }
            android.os.Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> notifyBlocked = true
        }
    }
    Box {
        GlassScaffold("Settings", onBack) {
            item { SectionHeader("Appearance") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Theme", style = Aurum.type.title, color = c.textPrimary)
                    Spacer(Modifier.height(Space.sm))
                    SegmentedSelector(ThemePref.entries, settings.theme, { it.name }, { t -> vm.updateSettings { it.copy(theme = t) } }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.lg))
                    Text("Number format", style = Aurum.type.title, color = c.textPrimary)
                    Text("Large values as \$1.23B or \$1.23e9", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    SegmentedSelector(NotationPref.entries, settings.notation, { if (it == NotationPref.Suffix) "Suffix" else "Scientific" }, { n -> vm.updateSettings { it.copy(notation = n) } }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.md))
                    HairlineDivider()
                    ToggleRow("Reduce motion", "Replace animations with instant changes", settings.reducedMotion) { v -> vm.updateSettings { it.copy(reducedMotion = v) } }
                }
            }
            item { SectionHeader("Sound & feel") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    ToggleRow("Haptics", "Gentle vibrations for taps and milestones", settings.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
                    Spacer(Modifier.height(Space.sm))
                    Text("Sound effects", style = Aurum.type.title, color = c.textPrimary)
                    Spacer(Modifier.height(Space.xs))
                    SegmentedSelector(listOf(0f, 0.4f, 0.8f), listOf(0f, 0.4f, 0.8f).minBy { kotlin.math.abs(it - settings.sfxVolume) },
                        { when (it) { 0f -> "Off"; 0.4f -> "Quiet"; else -> "Normal" } }, { v -> vm.updateSettings { it.copy(sfxVolume = v) } }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.sm))
                    Text("Music", style = Aurum.type.title, color = c.textPrimary)
                    Spacer(Modifier.height(Space.xs))
                    SegmentedSelector(listOf(0f, 0.3f, 0.6f), listOf(0f, 0.3f, 0.6f).minBy { kotlin.math.abs(it - settings.musicVolume) },
                        { when (it) { 0f -> "Off"; 0.3f -> "Quiet"; else -> "Normal" } }, { v -> vm.updateSettings { it.copy(musicVolume = v) } }, Modifier.fillMaxWidth())
                    Text("Four original tracks, one for each part of your empire. Pauses if another app is playing music.", style = Aurum.type.bodySmall, color = c.textTertiary)
                }
            }
            item { SectionHeader("Notifications") }
            item {
                AurumCard(Modifier.fillMaxWidth().testTag("notifications-card")) {
                    ToggleRow("While you're away", "A heads-up when something finishes or needs you. Never more than 3 a day.", settings.notifications, enableNotifications)
                    if (notifyBlocked) {
                        Text("Notifications are turned off for this game in Android settings.", style = Aurum.type.bodySmall, color = c.loss)
                        GhostButton("Open Android settings", onClick = {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }, modifier = Modifier.fillMaxWidth().padding(top = Space.xs))
                    }
                    if (settings.notifications) {
                        HairlineDivider()
                        ToggleRow("Finished work", "Expansions, renovations and construction", settings.notifyTimers) { v -> vm.updateSettings { it.copy(notifyTimers = v) } }
                        ToggleRow("Auctions", "The result of an auction you bid in", settings.notifyAuctions) { v -> vm.updateSettings { it.copy(notifyAuctions = v) } }
                        ToggleRow("The Desk", "When your Desk is full of decisions", settings.notifyDesk) { v -> vm.updateSettings { it.copy(notifyDesk = v) } }
                        HairlineDivider()
                        ToggleRow("Quiet hours", "Nothing from 10 pm to 8 am; it waits for the morning", settings.quietHours) { v -> vm.updateSettings { it.copy(quietHours = v) } }
                    }
                }
            }
            item { SectionHeader("Your save") }
            item {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                    val slotNow by vm.slot.collectAsStateWithLifecycle()
                    MenuRow("Save slots", AbtIcons.Menu, onSlots, subtitle = "Playing slot ${slotNow + 1} of 3 · switch or start a new game", modifier = Modifier.testTag("menu-slots"))
                    HairlineDivider(Modifier.padding(start = 60.dp))
                    MenuRow("Export save file", AbtIcons.Download, onClick = { exportLauncher.launch("arnav-business-tycoon.abtsave") }, subtitle = "Keep a backup copy of your progress")
                    HairlineDivider(Modifier.padding(start = 60.dp))
                    MenuRow("Import save file", AbtIcons.Upload, onClick = { confirmImport = true }, subtitle = "Replace this game with a backup")
                }
            }
            item { SectionHeader("Sandbox") }
            item {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                    val st by vm.state.collectAsStateWithLifecycle()
                    val used = st?.let { com.arnavgames.tycoon.core.engine.Cheats.uses(it) } ?: 0
                    MenuRow("Cheats", AbtIcons.Sparkle, onCheats, subtitle = if (used > 0) "This save is a sandbox · $used used" else "Cash, time skips and boosts, with Undo", modifier = Modifier.testTag("menu-cheats"))
                }
            }
            item { SectionHeader("Updates") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    ToggleRow("Automatic updates", "Download new versions on Wi-Fi and install them when you leave the game", settings.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
                    HairlineDivider()
                    GhostButton("Check for updates", onClick = { vm.checkForUpdate(force = true) }, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).testTag("check-updates"))
                }
            }
            item { com.arnavgames.tycoon.ui.common.UpdateCard(vm.updates, settings.autoUpdate) }
            item { SectionHeader("About") }
            item {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                    MenuRow("Credits & licenses", AbtIcons.Book, onCredits)
                    HairlineDivider(Modifier.padding(start = 60.dp))
                    MenuRow("Project page", AbtIcons.Cloud, onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")))
                    }, subtitle = "github.com/${BuildConfig.GITHUB_REPO}")
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.HAS_FIREBASE) "cloud ready" else "offline edition"}",
                        style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.padding(Space.md),
                    )
                }
            }
        }
        if (confirmImport) {
            AurumDialog(title = "Replace this game?", onDismiss = { confirmImport = false }) {
                Text("Importing a save replaces your current progress on this device. Your current game is kept as a backup snapshot.", style = Aurum.type.body, color = c.textSecondary)
                GoldButton("Choose a save file", onClick = { confirmImport = false; importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Upload)
                GhostButton("Cancel", onClick = { confirmImport = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Aurum.type.title, color = c.textPrimary)
            Text(subtitle, style = Aurum.type.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(Space.md))
        AurumToggle(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun CreditsScreen(onBack: () -> Unit, artCredits: List<String> = emptyList(), portraitCount: Int = 0) {
    val c = Aurum.colors
    GlassScaffold("Credits", onBack) {
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Arnav Business Tycoon", style = Aurum.type.headline, color = c.textPrimary)
                Text("Design and development: Arnav Games", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.sm))
                Text("Cities and neighbourhoods are real places. Companies, coins, products and people are parodies or fictional: satire, not real, and not affiliated with or endorsed by anyone they may resemble. All prices, news and events are made up.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        item { SectionHeader("Typeface") }
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Inter", style = Aurum.type.title, color = c.textPrimary)
                Text("© The Inter Project Authors · SIL Open Font License 1.1 · Text and Display optical cuts", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        item { SectionHeader("Libraries") }
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Haze", style = Aurum.type.title, color = c.textPrimary)
                Text("Backdrop blur for the glass interface · © Chris Banes · Apache License 2.0", style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                Text("Android Jetpack & Kotlin", style = Aurum.type.title, color = c.textPrimary)
                Text("© The Android Open Source Project, JetBrains · Apache License 2.0", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        item { SectionHeader("Icons & art") }
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("All icons, the crest, logos and the world map are original artwork drawn in code for this game.", style = Aurum.type.body, color = c.textSecondary)
                if (portraitCount > 0) {
                    Spacer(Modifier.height(Space.xs))
                    Text("$portraitCount portraits (managers, rivals and The Chairman) are AI-generated caricatures of invented people (Figma AI). They do not depict real people, including the rivals with parody names.", style = Aurum.type.bodySmall, color = c.textTertiary)
                    Text("Sound effects and the four music tracks were synthesized in code for this game (no third-party audio or samples).", style = Aurum.type.bodySmall, color = c.textTertiary)
                }
            }
        }
        if (artCredits.isNotEmpty()) {
            item { SectionHeader("Museum works (${artCredits.size})") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Public-domain images from the Art Institute of Chicago, released under CC0. Titles and artists are shown as credited by the museum.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    artCredits.forEach { line ->
                        Spacer(Modifier.height(Space.xs))
                        Text(line, style = Aurum.type.bodySmall, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

