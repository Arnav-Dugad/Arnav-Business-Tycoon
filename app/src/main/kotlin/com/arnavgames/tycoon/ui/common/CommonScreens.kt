package com.arnavgames.tycoon.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.core.money.Rate
import com.arnavgames.tycoon.core.time.TimeFlag
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.CrownMark
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.game.GameSession

@Composable
fun LoadingScreen() {
    val c = Aurum.colors
    val pulse = if (Aurum.reducedMotion) 1f else {
        val t = rememberInfiniteTransition(label = "loading")
        t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse").value
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CrownMark(Modifier.scale(pulse), size = 104.dp)
            Spacer(Modifier.height(Space.xl))
            Text("Opening the books…", style = Aurum.type.body, color = c.textSecondary)
        }
    }
}

/** Shown when no save could be loaded. Never starts a new game without the player's explicit choice. */
@Composable
fun RecoveryScreen(errors: List<String>, onStartOver: (() -> Unit)?, title: String = "We couldn't open your save") {
    val c = Aurum.colors
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AbtIcon(AbtIcons.Shield, null, tint = c.warning, size = 56.dp)
        Spacer(Modifier.height(Space.lg))
        Text(title, style = Aurum.type.display, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.md))
        Text(
            if (onStartOver != null) "Your save files appear damaged. They have been set aside safely and were not deleted. " +
                "If you use cloud saves, signing in will restore your progress." else "Your progress is safe on this device.",
            style = Aurum.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(Space.lg))
        AurumCard(Modifier.fillMaxWidth().widthIn(max = 460.dp)) {
            Text("Details", style = Aurum.type.overline, color = c.textTertiary)
            Spacer(Modifier.height(Space.xs))
            errors.take(5).forEach { Text(it, style = Aurum.type.bodySmall, color = c.textSecondary) }
        }
        if (onStartOver != null) {
            Spacer(Modifier.height(Space.xxl))
            GhostButton("Start a new game", onClick = onStartOver, leadingIcon = AbtIcons.Refresh)
        }
    }
}

@Composable
fun OfflineEarningsDialog(report: GameSession.OfflineReport, onDismiss: () -> Unit) {
    if (report.stories.isNotEmpty()) return DailyLedgerDialog(report, onDismiss)
    val c = Aurum.colors
    AurumDialog(title = "While you were away", onDismiss = onDismiss) {
        CrownMark(size = 64.dp)
        Text(durationText(report.creditedMs), style = Aurum.type.body, color = c.textSecondary)
        RollingMoney(value = report.earned, rate = Rate.ZERO, color = c.goldText, format = { "+" + com.arnavgames.tycoon.core.money.MoneyFormat.compact(it) })
        val note = when {
            report.flag == TimeFlag.ForwardJumpDetected || report.flag == TimeFlag.ClockWentBackwards ->
                "Your device clock changed, so only the time we could verify was counted."
            report.rawMs > report.capMs -> "Offline earnings are capped at ${durationText(report.capMs)}. Upgrade your HQ to extend it."
            else -> "Your managers kept the doors open."
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Info, null, tint = c.textTertiary, size = 16.dp)
            Spacer(Modifier.width(Space.xs))
            Text(note, style = Aurum.type.bodySmall, color = c.textTertiary)
        }
        GoldButton("Collect", onClick = onDismiss, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Cash)
    }
}

/**
 * The Daily Ledger (docs/PLAN_2.0.md D1): a newspaper front page for absences of 30 minutes or more. Masthead in
 * the system serif (no bundled font), the earnings as the lead, then up to five ranked stories.
 */
@Composable
private fun DailyLedgerDialog(report: GameSession.OfflineReport, onDismiss: () -> Unit) {
    val c = Aurum.colors
    val masthead = Aurum.type.headline.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp))
    AurumDialog(title = "The Daily Ledger", onDismiss = onDismiss, titleStyle = masthead) {
        Text("DAY ${report.dayNumber} · WHILE YOU WERE AWAY · ${durationText(report.creditedMs).uppercase()}", style = Aurum.type.overline, color = c.goldText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        HairlineDivider()
        Text("Your empire earned", style = Aurum.type.bodySmall, color = c.textSecondary)
        RollingMoney(value = report.earned, rate = Rate.ZERO, color = c.goldText, format = { "+" + com.arnavgames.tycoon.core.money.MoneyFormat.compact(it) })
        if (report.rawMs > report.capMs) Text("Offline earnings stop after ${durationText(report.capMs)}. A bigger HQ keeps them going longer.", style = Aurum.type.label, color = c.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        HairlineDivider()
        Column(
            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            report.stories.forEach { st ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    val tint = when (st.tone) { com.arnavgames.tycoon.ui.ledger.Tone.Good -> c.gain; com.arnavgames.tycoon.ui.ledger.Tone.Bad -> c.loss; else -> c.goldText }
                    AbtIcon(AbtIcons.named(st.icon), null, tint = tint, size = 20.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(st.headline, style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text(st.detail, style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
        GoldButton("Back to work", onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("ledger-close"), leadingIcon = AbtIcons.Cash)
    }
}

fun durationText(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m.coerceAtLeast(1)}m"
    }
}

/** "4m 12s", "1h 05m", "8s". */
fun countdownText(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, sec)
        else -> "${sec}s"
    }
}

