package com.arnavgames.tycoon.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.BuildConfig
import com.arnavgames.tycoon.data.settings.NotationPref
import com.arnavgames.tycoon.data.settings.ThemePref
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.MenuRow
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit, onCredits: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = Aurum.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) scope.launch { vm.exportSave(ctx, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { vm.importSave(ctx, uri) }
    }
    var confirmImport by remember { mutableStateOf(false) }
    if (confirmImport) {
        AurumDialog(title = "Replace this game?", onDismiss = { confirmImport = false }) {
            Text("Importing a save replaces your current progress on this device. Your current game is kept as a backup snapshot.", style = Aurum.type.body, color = c.textSecondary)
            GoldButton("Choose a save file", onClick = { confirmImport = false; importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Upload)
            GhostButton("Cancel", onClick = { confirmImport = false }, modifier = Modifier.fillMaxWidth())
        }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Space.touch).clip(AurumShapes.pill).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { AbtIcon(AbtIcons.ChevronLeft, null, tint = c.textPrimary) }
            Spacer(Modifier.width(Space.xs))
            Text("Settings", style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        }
        LazyColumn(contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.huge), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            item { SectionHeader("Appearance") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Theme", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(Space.sm))
                    SegmentedSelector(ThemePref.entries, settings.theme, { it.name }, { t -> vm.updateSettings { it.copy(theme = t) } })
                    Spacer(Modifier.height(Space.lg))
                    Text("Number format", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Text("Large values as \$1.23B or \$1.23e9", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    SegmentedSelector(NotationPref.entries, settings.notation, { if (it == NotationPref.Suffix) "Suffix" else "Scientific" }, { n -> vm.updateSettings { it.copy(notation = n) } })
                    Spacer(Modifier.height(Space.md))
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
                    MenuRow("Import save file", AbtIcons.Upload, onClick = { confirmImport = true }, subtitle = "Replace this game with a backup")
                }
            }
            item { SectionHeader("About") }
            item {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                    MenuRow("Credits & licenses", AbtIcons.Book, onCredits)
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
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Aurum.type.titleSmall, color = c.textPrimary)
            Text(subtitle, style = Aurum.type.bodySmall, color = c.textSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = c.textOnGold, checkedTrackColor = c.gold, uncheckedThumbColor = c.textTertiary, uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.outlineStrong),
        )
    }
}

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val c = Aurum.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Space.touch).clip(AurumShapes.pill).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { AbtIcon(AbtIcons.ChevronLeft, null, tint = c.textPrimary) }
            Spacer(Modifier.width(Space.xs))
            Text("Credits & licenses", style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        }
        LazyColumn(contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.huge), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Arnav Business Tycoon", style = Aurum.type.headline, color = c.textPrimary)
                    Text("Design and development: Arnav Games", style = Aurum.type.body, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    Text("All companies, people, places and products in this game are fictional. Any resemblance is coincidental.", style = Aurum.type.bodySmall, color = c.textTertiary)
                }
            }
            item { SectionHeader("Fonts") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Inter", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Text("© The Inter Project Authors · SIL Open Font License 1.1", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.md))
                    Text("Fraunces", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Text("© The Fraunces Project Authors · SIL Open Font License 1.1", style = Aurum.type.bodySmall, color = c.textSecondary)
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
}
