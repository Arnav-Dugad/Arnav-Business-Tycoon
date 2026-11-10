package com.arnavgames.tycoon.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Cheats
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.Art
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.Illustration
import kotlinx.coroutines.delay

/** One cheat tile: what it is, what it will give right now, and what to say once it's done. */
private data class CheatTile(val kind: String, val icon: ImageVector, val title: String, val preview: String, val done: String)

/**
 * Settings → Cheats. Designed around three ideas (docs/UX.md):
 * - Honest consequences up front: the first cheat marks this save as a sandbox for good and keeps it off leaderboards,
 *   so the page asks once, plainly, and offers the better option (an empty slot) right there.
 * - Show what you get before you tap: every tile previews its exact effect ("+$1.2B").
 * - Undo instead of confirm: one tap applies, and the bar at the bottom puts it back.
 */
@Composable
fun CheatsScreen(vm: GameViewModel, onBack: () -> Unit, onOpenSlots: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val slot by vm.slot.collectAsStateWithLifecycle()
    val last by vm.lastCheat.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val haptics = LocalHapticFeedback.current
    val uses = Cheats.uses(s)
    val sandbox = uses > 0
    val mainSlot = slot == 0
    // Cheats unlock per slot: a sandbox save or a side slot needs no question; the main game asks once.
    var unlocked by remember(slot) { mutableStateOf(sandbox || !mainSlot || prefs.getBoolean("cheatsOk.$slot", false)) }
    var asking by remember { mutableStateOf(false) }

    // The undo bar fades away by itself after a while; using another cheat restarts the clock.
    LaunchedEffect(last) { if (last != null) { delay(8_000); vm.clearLastCheat() } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.clearLastCheat() } }

    val eco = vm.engine.economy
    val hour = maxOf(eco.totalNetRate(s, managedOnly = false).over(3_600), Money.ofDollars(1_000))
    val day = maxOf(eco.totalNetRate(s, managedOnly = false).over(86_400), Money.ofDollars(1_000))
    val double = maxOf(s.cash, Money.ofDollars(1_000))
    val groups = listOf(
        "Money" to listOf(
            CheatTile(Cheats.CASH_HOUR, AbtIcons.Cash, "An hour's income", "+${fmt(hour)}", "+${fmt(hour)} cash"),
            CheatTile(Cheats.CASH_DAY, AbtIcons.Bank, "A day's income", "+${fmt(day)}", "+${fmt(day)} cash"),
            CheatTile(Cheats.CASH_DOUBLE, AbtIcons.Sparkle, "Double my cash", "+${fmt(double)}", "Cash doubled"),
        ),
        "Currencies" to listOf(
            CheatTile(Cheats.BULLION, AbtIcons.Bullion, "Bullion", "+1,000", "+1,000 Bullion"),
            CheatTile(Cheats.FAVOR, AbtIcons.Favor, "Favor", "+500", "+500 Favor"),
        ),
        "Time" to listOf(
            CheatTile(Cheats.SKIP_HOUR, AbtIcons.Clock, "Skip an hour", "Everything runs 1 h", "An hour went by"),
            CheatTile(Cheats.SKIP_8H, AbtIcons.Moon, "Skip a night", "Everything runs 8 h", "Eight hours went by"),
            CheatTile(Cheats.FINISH_TIMERS, AbtIcons.Timer, "Finish all builds", if (s.timers.isEmpty()) "Nothing building" else "${s.timers.size} finish now", "Every build finished"),
        ),
        "Boosts" to listOf(
            CheatTile(Cheats.FRENZY, AbtIcons.Bolt, "Start a Frenzy", "Counter pays more", "Frenzy started"),
            CheatTile(Cheats.LEVELS, AbtIcons.ArrowUp, "+10 levels", "Every business", "+10 levels everywhere"),
        ),
    )

    GlassScaffold("Cheats", onBack, bottomBar = if (last != null) ({
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("cheat-undo-bar")) {
            AbtIcon(AbtIcons.Check, null, tint = c.gain, size = 18.dp)
            Spacer(Modifier.width(Space.sm))
            Text(last.orEmpty(), style = Aurum.type.body, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            PillAction("Undo", true, { vm.undoCheat() }, style = PillStyle.Positive, modifier = Modifier.testTag("cheat-undo"))
        }
    }) else null) {
        item(key = "hero") {
            Illustration(Art.event("gold_rush"), Modifier.fillMaxWidth().height(170.dp).clip(AurumShapes.large), scrim = true, fallback = Art.WELCOME) {
                Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                    Text("SANDBOX", style = Aurum.type.overline, color = c.gold)
                    Text(
                        when {
                            sandbox -> "This save is a sandbox"
                            mainSlot -> "Bend the rules"
                            else -> "Slot ${slot + 1} plays by your rules"
                        },
                        style = Aurum.type.headline, color = Color.White,
                    )
                    Text(
                        when {
                            sandbox -> "$uses cheat${if (uses == 1L) "" else "s"} used · kept off leaderboards"
                            mainSlot && unlocked -> "Your first cheat makes this save a sandbox"
                            mainSlot -> "Your main game is clean"
                            else -> "A side slot: it never reaches leaderboards"
                        },
                        style = Aurum.type.bodySmall, color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        if (!unlocked) {
            item(key = "gate") {
                AurumCard(Modifier.fillMaxWidth().testTag("cheats-gate"), highlighted = true) {
                    Text("Before you start", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Cheats turn this save into a sandbox for good: it stays playable, but it's kept off the global board, your weekly league and your friends' rankings. " +
                            "An empty save slot gives you a sandbox without touching your main game.",
                        style = Aurum.type.body, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Space.md))
                    GoldButton("Try them in an empty slot", onClick = onOpenSlots, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Plus)
                    Spacer(Modifier.height(Space.sm))
                    GhostButton("Use cheats on this save", onClick = { asking = true }, modifier = Modifier.fillMaxWidth().testTag("cheats-use-here"))
                }
            }
        } else {
            groups.forEach { (title, tiles) ->
                item(key = "h-$title") { SectionHeader(title) }
                item(key = "g-$title") {
                    FlowRow(
                        Modifier.fillMaxWidth(), maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        tiles.forEach { t ->
                            CheatTileCard(t, Modifier.weight(1f)) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.cheat(t.kind, t.done)
                            }
                        }
                        if (tiles.size % 2 == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item(key = "note") {
                Text(
                    "Each cheat changes only this save. Undo puts things back exactly as they were.",
                    style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = Space.xs),
                )
            }
        }
    }

    if (asking) {
        AurumDialog(title = "Make slot ${slot + 1} a sandbox?", onDismiss = { asking = false }) {
            Text(
                "Your main game will be kept off leaderboards from the first cheat on. This can't be reversed for this save, but Undo works on each cheat.",
                style = Aurum.type.body, color = c.textSecondary,
            )
            GoldButton("Make it a sandbox", onClick = {
                prefs.edit().putBoolean("cheatsOk.$slot", true).apply()
                unlocked = true
                asking = false
            }, modifier = Modifier.fillMaxWidth().testTag("cheats-confirm"))
            GhostButton("Keep it clean", onClick = { asking = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheatTileCard(t: CheatTile, modifier: Modifier, onClick: () -> Unit) {
    val c = Aurum.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed && !Aurum.reducedMotion) 0.96f else 1f, label = "press")
    AurumCard(
        modifier.scale(k).semantics(mergeDescendants = true) { contentDescription = "${t.title}: ${t.preview}" }.testTag("cheat-${t.kind}"),
        onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
    ) {
        IconTile(t.icon, size = 40.dp)
        Spacer(Modifier.height(Space.sm))
        Text(t.title, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1)
        Text(t.preview, style = Aurum.type.numberSmall, color = c.goldText, maxLines = 1)
    }
}

/**
 * Settings → Save slots: three games on this phone. Slot 1 is the main game (the one the cloud backs up); slots 2
 * and 3 are local. Switching saves the current game first; deleting asks once and names what will be lost.
 */
@Composable
fun SaveSlotsScreen(vm: GameViewModel, onBack: () -> Unit, onPlay: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val slot by vm.slot.collectAsStateWithLifecycle()
    val c = Aurum.colors
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    var refresh by remember { mutableIntStateOf(0) }
    var infos by remember { mutableStateOf<List<com.arnavgames.tycoon.data.save.SaveRepository.SlotInfo?>?>(null) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(refresh, slot) { infos = vm.slotInfos() }

    GlassScaffold("Save slots", onBack) {
        item(key = "intro") {
            Text(
                "Three separate games on this phone. Slot 1 is your main game and the only one backed up to the cloud; slots 2 and 3 are great for a fresh start or a sandbox.",
                style = Aurum.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs),
            )
        }
        val list = infos
        if (list == null) item(key = "loading") { Text("Reading your saves…", style = Aurum.type.bodySmall, color = c.textTertiary) }
        else list.forEachIndexed { n, info ->
            item(key = "slot-$n") {
                val playing = n == slot
                AurumCard(Modifier.fillMaxWidth().testTag("slot-$n"), highlighted = playing) {
                    if (info != null) {
                        Illustration(Art.chapter(info.state.progress.chapter), Modifier.fillMaxWidth().height(96.dp).clip(AurumShapes.medium), scrim = true) {
                            Row(Modifier.align(Alignment.BottomStart).padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                                Text("Chapter ${info.state.progress.chapter}", style = Aurum.type.label, color = Color.White)
                                if (Cheats.isSandbox(info.state)) {
                                    Spacer(Modifier.width(Space.sm))
                                    AurumChip("Sandbox", tint = Color.White, background = Color.Black.copy(alpha = 0.35f))
                                }
                            }
                        }
                        Spacer(Modifier.height(Space.md))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (n == 0) "Slot 1 · Main game" else "Slot ${n + 1}", style = Aurum.type.title, color = c.textPrimary)
                            Text(
                                when {
                                    info == null -> "Empty"
                                    else -> "${fmt(vm.engine.economy.netWorth(info.state))} net worth · generation ${info.state.dynasty.generation} · ${ago(info.savedAtWall)}"
                                },
                                style = Aurum.type.bodySmall, color = c.textSecondary,
                            )
                            if (n == 0) Text("Backed up to the cloud when you're signed in", style = Aurum.type.label, color = c.textTertiary)
                        }
                        if (playing) AurumChip("Playing", icon = AbtIcons.Play, tint = c.gain)
                    }
                    if (!playing) {
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            // An empty slot's "new game" is a quieter choice than playing a saved one.
                            if (info == null) GhostButton("Start a new game here", onClick = { busy = true; vm.switchSlot(n) { busy = false; onPlay() } },
                                enabled = !busy, modifier = Modifier.weight(1f).testTag("slot-play-$n"), leadingIcon = AbtIcons.Plus)
                            else GoldButton(
                                "Play slot ${n + 1}",
                                onClick = { busy = true; vm.switchSlot(n) { busy = false; onPlay() } },
                                enabled = !busy, modifier = Modifier.weight(1f).testTag("slot-play-$n"),
                                leadingIcon = if (info == null) AbtIcons.Plus else AbtIcons.Play,
                            )
                            if (info != null) GhostButton("Delete", onClick = { deleting = n }, enabled = !busy, leadingIcon = AbtIcons.Trash)
                        }
                    }
                }
            }
        }
    }

    deleting?.let { n ->
        val info = infos?.getOrNull(n)
        AurumDialog(title = "Delete slot ${n + 1}?", onDismiss = { deleting = null }) {
            Text(
                "This removes " + (info?.let { "the game with ${fmt(vm.engine.economy.netWorth(it.state))} net worth (chapter ${it.state.progress.chapter})" } ?: "this game") +
                    " from this phone for good. Your other slots aren't touched.",
                style = Aurum.type.body, color = c.textSecondary,
            )
            // The safe choice is the prominent one; the destructive one is quieter and names its outcome.
            GoldButton("Keep it", onClick = { deleting = null }, modifier = Modifier.fillMaxWidth())
            GhostButton("Delete slot ${n + 1}", onClick = { deleting = null; vm.deleteSlot(n) { refresh++ } }, modifier = Modifier.fillMaxWidth().testTag("slot-delete-confirm"), leadingIcon = AbtIcons.Trash)
        }
    }
}

private fun ago(wall: Long): String {
    val m = ((System.currentTimeMillis() - wall) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / 1440} days ago"
    }
}
