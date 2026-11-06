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
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit, onCredits: () -> Unit) {
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
                    Text("Music and sound effects arrive in the polish phase.", style = Aurum.type.bodySmall, color = c.textTertiary)
                }
            }
            item { SectionHeader("Your save") }
            item {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                    MenuRow("Export save file", AbtIcons.Download, onClick = { exportLauncher.launch("arnav-business-tycoon.abtsave") }, subtitle = "Keep a backup copy of your progress")
                    HairlineDivider(Modifier.padding(start = 60.dp))
                    MenuRow("Import save file", AbtIcons.Upload, onClick = { confirmImport = true }, subtitle = "Replace this game with a backup")
                }
            }
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
        AurumToggle(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val c = Aurum.colors
    GlassScaffold("Credits", onBack) {
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Arnav Business Tycoon", style = Aurum.type.headline, color = c.textPrimary)
                Text("Design and development: Arnav Games", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.sm))
                Text("All companies, people, places and products in this game are fictional. Any resemblance is coincidental.", style = Aurum.type.bodySmall, color = c.textTertiary)
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
                Text("All icons and the crest are original artwork made for this game.", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.xs))
                Text("Photo credits will be listed here as licensed images are added.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
    }
}

