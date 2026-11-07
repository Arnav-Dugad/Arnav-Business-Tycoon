package com.arnavgames.tycoon.notify

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.ui.GameViewModel

/**
 * Offers notifications once, at the moment they're obviously useful: the first time the player starts something
 * that takes a while. Asking at first launch would mean nothing; asking here explains itself. Never asked again.
 */
@Composable
fun NotifyOffer(vm: GameViewModel) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prefs = remember { ctx.getSharedPreferences("intros", Context.MODE_PRIVATE) }
    var offer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.notifications) {
        if (settings.notifications || prefs.getBoolean(KEY, false)) return@LaunchedEffect
        vm.events.collect { e ->
            val now = vm.state.value?.simTimeMs ?: return@collect
            val endsAt = when (e) {
                is GameEvent.ExpansionStarted -> e.endsAt
                is GameEvent.RenovationStarted -> e.endsAt
                is GameEvent.TowerStageStarted -> e.endsAt
                else -> return@collect
            }
            if (endsAt - now >= MIN_WAIT_MS && offer == null) offer = waitText(endsAt - now)
        }
    }
    fun done() { prefs.edit().putBoolean(KEY, true).apply(); offer = null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings { it.copy(notifications = true) }
        done()
    }
    val wait = offer ?: return
    NotifyOfferDialog(wait, onYes = {
        when {
            Notifications.canPost(ctx) -> { vm.updateSettings { it.copy(notifications = true) }; done() }
            Build.VERSION.SDK_INT >= 33 -> launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> done()
        }
    }, onNo = ::done)
}

/** The offer itself; [wait] reads like "about 2 hours". */
@Composable
fun NotifyOfferDialog(wait: String, onYes: () -> Unit, onNo: () -> Unit) {
    AurumDialog(title = "Want a heads-up?", onDismiss = onNo) {
        Text(
            "That takes $wait. The game can tell you when it's done, even when it's closed. Never more than 3 a day, and nothing at night.",
            style = Aurum.type.body, color = Aurum.colors.textSecondary,
        )
        GoldButton("Notify me", onClick = onYes, modifier = Modifier.fillMaxWidth().testTag("notify-yes"), leadingIcon = AbtIcons.Clock)
        GhostButton("Not now", onClick = onNo, modifier = Modifier.fillMaxWidth())
        Text("You can change this any time in Settings.", style = Aurum.type.bodySmall, color = Aurum.colors.textTertiary)
    }
}

private const val KEY = "notify-offer"
private const val MIN_WAIT_MS = 10 * 60_000L

private fun waitText(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 60 -> "$m minutes"
        m < 120 -> "about an hour"
        m < 24 * 60 -> "about ${m / 60} hours"
        else -> "about ${m / 1440} ${if (m / 1440 == 1L) "day" else "days"}"
    }
}
