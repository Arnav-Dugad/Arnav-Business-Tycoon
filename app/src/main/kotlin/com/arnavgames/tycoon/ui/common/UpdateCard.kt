package com.arnavgames.tycoon.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.update.UpdateManager
import com.arnavgames.tycoon.update.UpdateState

/** The update card (Dynasty tab and Settings): progress, "allow installs", "install now". Nothing when up to date. */
@Composable
fun UpdateCard(updates: UpdateManager, autoUpdate: Boolean, modifier: Modifier = Modifier) {
    val state by updates.state.collectAsStateWithLifecycle()
    val c = Aurum.colors
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val s = state
    if (s is UpdateState.Idle) return
    val info = when (s) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info
        UpdateState.Idle -> return
    }
    AurumCard(modifier.fillMaxWidth().testTag("update-card"), highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Download, null, tint = c.goldText, size = 24.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    when (s) {
                        is UpdateState.Ready -> "Update ${info.version} is ready"
                        is UpdateState.Installing -> "Installing ${info.version}…"
                        is UpdateState.Downloading -> "Downloading ${info.version}…"
                        else -> "Update available: ${info.version}"
                    },
                    style = Aurum.type.titleSmall, color = c.textPrimary,
                )
                Text(
                    when (s) {
                        is UpdateState.Available -> if (info.apkUrl == null) "Open the release page to download it." else if (autoUpdate) "It will download on Wi-Fi, or download it now." else "Automatic updates are off in Settings."
                        is UpdateState.Downloading -> "Checked against the release checksum before installing."
                        is UpdateState.Ready -> when {
                            s.needsPermission -> "Allow this game to install its own updates once (Android asks for this for apps from outside the Play Store)."
                            autoUpdate -> "Verified. It installs when you leave the game, or install it now. Your save is kept."
                            else -> "Verified. Install it now; your save is kept."
                        }
                        is UpdateState.Installing -> "The game closes while it updates. Open it again in a few seconds."
                        is UpdateState.Failed -> s.message
                        UpdateState.Idle -> ""
                    },
                    style = Aurum.type.bodySmall, color = if (s is UpdateState.Failed) c.loss else c.textSecondary,
                )
            }
        }
        if (s is UpdateState.Downloading) {
            Spacer(Modifier.height(Space.sm))
            GoldProgress(s.progress, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.sm))
        when (s) {
            is UpdateState.Available -> if (info.apkUrl != null) GoldButton("Download now", onClick = { updates.download(info) }, modifier = Modifier.fillMaxWidth().testTag("update-download"), leadingIcon = AbtIcons.Download)
                else GhostButton("Open release page", onClick = { runCatching { uri.openUri(info.url) } }, modifier = Modifier.fillMaxWidth())
            is UpdateState.Ready -> if (s.needsPermission) GoldButton("Allow updates", onClick = { runCatching { ctx.startActivity(updates.permissionIntent()) } }, modifier = Modifier.fillMaxWidth().testTag("update-allow"))
                else GoldButton("Install now", onClick = { updates.installNow() }, modifier = Modifier.fillMaxWidth().testTag("update-install"))
            is UpdateState.Failed -> if (info.apkUrl != null) GhostButton("Try again", onClick = { updates.download(info) }, modifier = Modifier.fillMaxWidth())
                else GhostButton("Open release page", onClick = { runCatching { uri.openUri(info.url) } }, modifier = Modifier.fillMaxWidth())
            else -> Unit
        }
    }
}
