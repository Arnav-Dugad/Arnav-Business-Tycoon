package com.arnavgames.tycoon.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import com.arnavgames.tycoon.designsystem.components.AurumChip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.MenuRow
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.GameViewModel

/**
 * The More tab, rebuilt around "where do I go next?":
 * - you at the top (avatar, name, net worth, generation);
 * - every place in the game as a painted door in one grid, each with a live line and a badge when something
 *   waits (Collection, Dynasty, Store, Achievements, Desk, Fortune List, Finance, Friends);
 * - the quiet utilities last (account, save slots, cheats, settings, credits).
 */
@Composable
fun MoreScreen(
    vm: GameViewModel,
    onOpenCollection: () -> Unit, onOpenDynasty: () -> Unit,
    onOpenStore: () -> Unit, onOpenAchievements: () -> Unit, onOpenSocial: () -> Unit, onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit, onOpenCredits: () -> Unit,
    onOpenDesk: () -> Unit = {}, onOpenFortune: () -> Unit = {}, onOpenFinance: () -> Unit = {},
    onOpenSlots: () -> Unit = {}, onOpenCheats: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val collection by vm.collection.collectAsStateWithLifecycle()
    val empire by vm.empire.collectAsStateWithLifecycle()
    val desk by vm.desk.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val slot by vm.slot.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val fmt: (com.arnavgames.tycoon.core.money.Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val dy = remember(s.dynasty, s.meta, s.progress, s.simTimeMs / 60_000) { vm.dynastyMapper.dynasty(s) }
    val col = collection
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reselect.collect { if (it == "more") list.animateScrollToItem(0) } }
    val A = com.arnavgames.tycoon.ui.common.Art
    LazyColumn(
        Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("more-list"), state = list,
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = BottomBarReserve + Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "title") {
            Text("More", style = Aurum.type.hero, color = c.textPrimary, modifier = Modifier.statusBarsPadding().padding(top = Space.xl, start = Space.xs).semantics { heading() })
        }
        // You: who you are in this game, at a glance.
        item(key = "me") {
            AurumCard(Modifier.fillMaxWidth().testTag("more-profile"), onClick = onOpenDynasty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.arnavgames.tycoon.ui.common.AvatarBadge(s.store.avatar, s.store.frame, 56.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(s.holdingName ?: "Your empire", style = Aurum.type.title, color = c.textPrimary, maxLines = 1)
                        Text("Generation ${dy.generation} · chapter ${s.progress.chapter}" + if (slot != 0) " · slot ${slot + 1}" else "", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        empire?.let { Text(fmt(it.header.netWorth), style = Aurum.type.number, color = c.textPrimary) }
                        Text("net worth", style = Aurum.type.label, color = c.textTertiary)
                    }
                }
                if (s.store.boosted || com.arnavgames.tycoon.core.engine.Cheats.isSandbox(s)) {
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        if (s.store.boosted) AurumChip("Boosted", icon = AbtIcons.Bolt, tint = c.gain)
                        if (com.arnavgames.tycoon.core.engine.Cheats.isSandbox(s)) AurumChip("Sandbox", icon = AbtIcons.Sparkle, tint = c.goldText)
                    }
                }
            }
        }
        item(key = "places-h") { com.arnavgames.tycoon.designsystem.components.SectionHeader("Places") }
        val cardsWaiting = desk?.cards?.size ?: 0
        val doors = listOf(
            DoorSpec("door-collection", AbtIcons.Collection, "Collection",
                when { col == null -> ""; !col.unlocked -> "Opens in chapter ${col.unlockChapter}"; else -> "${col.tier} · ${fmt(col.value)}" },
                col?.auction?.let { "Auction live" }, onOpenCollection, col?.unlocked == false, A.luxuryCategory("painting")),
            DoorSpec("door-dynasty", AbtIcons.Crown, "Your Dynasty", "Generation ${dy.generation}" + if (dy.canPrestige) " · crown ready" else "",
                if (dy.canPrestige) "Crown ready" else null, onOpenDynasty, false, A.chapter(8)),
            DoorSpec("menu-store", AbtIcons.Bullion, "Store", "${s.ledger.bullion.centsOrNull ?: 0} Bullion", null, onOpenStore, false, A.luxuryCategory("jewelry")),
            DoorSpec("door-achievements", AbtIcons.Trophy, "Achievements", "${dy.achievementsDone} of ${dy.achievementsTotal}",
                if (dy.achievementsClaimable > 0) "${dy.achievementsClaimable} to claim" else null, onOpenAchievements, false, A.event("global_boom")),
            DoorSpec("door-desk", AbtIcons.Cards, "The Desk", if (cardsWaiting > 0) "$cardsWaiting waiting" else "All clear",
                if (cardsWaiting > 0) "Decisions waiting" else null, onOpenDesk, false, A.property("office_floor")),
            DoorSpec("door-fortune", AbtIcons.Trophy, "Fortune List", "The world's richest", null, onOpenFortune, false, A.city("new_york")),
            DoorSpec("door-finance", AbtIcons.Bank, "Finance", "Credit, loans and taxes", null, onOpenFinance, false, A.business("bank")),
            DoorSpec("door-social", AbtIcons.Rivals, "Friends", "Leagues, gifts and lots", null, onOpenSocial, false, A.city("london")),
        )
        doors.chunked(2).forEachIndexed { i, pair ->
            item(key = "doors-$i") {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    pair.forEach { d ->
                        Door(Modifier.weight(1f).fillMaxHeight().testTag(d.tag), d.icon, d.title, d.line, badge = d.badge, onClick = d.onClick, locked = d.locked, art = d.art)
                    }
                }
            }
        }
        item(key = "you-header") { com.arnavgames.tycoon.designsystem.components.SectionHeader("You") }
        item(key = "menu-you") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                MenuRow("Account & cloud save", AbtIcons.Cloud, onOpenAccount, subtitle = "Optional: back up, sync and add friends")
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Save slots", AbtIcons.Menu, onOpenSlots, subtitle = "Playing slot ${slot + 1} of 3", modifier = Modifier.testTag("more-slots"))
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Cheats", AbtIcons.Sparkle, onOpenCheats, subtitle = "Cash, time skips and boosts, with Undo")
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Settings", AbtIcons.Sliders, onOpenSettings, subtitle = "Theme, sound, notifications, save backup")
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Credits & licenses", AbtIcons.Book, onOpenCredits)
            }
        }
    }
}

private data class DoorSpec(
    val tag: String, val icon: ImageVector, val title: String, val line: String, val badge: String?,
    val onClick: () -> Unit, val locked: Boolean, val art: String,
)

/** A painted door to one place: icon, name, a live line, and a badge when something is waiting. */
@Composable
private fun Door(modifier: Modifier, icon: ImageVector, title: String, line: String, badge: String?, onClick: () -> Unit, locked: Boolean = false, art: String? = null) {
    val c = Aurum.colors
    AurumCard(modifier, onClick = onClick, highlighted = badge != null, contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.sm)) {
        Box(Modifier.fillMaxWidth()) {
            if (art != null) {
                com.arnavgames.tycoon.ui.common.Illustration(art, Modifier.fillMaxWidth().height(96.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.medium)
                    .then(if (locked) Modifier.alpha(0.45f) else Modifier))
            } else IconTile(if (locked) AbtIcons.Lock else icon, size = 48.dp, highlighted = !locked, tint = if (locked) c.textTertiary else c.goldText)
            if (locked) AbtIcon(AbtIcons.Lock, null, tint = c.textPrimary, size = 18.dp, modifier = Modifier.align(Alignment.Center))
            if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(Space.xs).size(10.dp).clip(CircleShape).background(c.loss))
        }
        Spacer(Modifier.height(Space.sm))
        Column(Modifier.padding(horizontal = Space.xs, vertical = Space.xxs)) {
            Text(title, style = Aurum.type.title, color = c.textPrimary)
            if (line.isNotEmpty()) Text(line, style = Aurum.type.bodySmall, color = c.textSecondary)
            badge?.let { Text(it, style = Aurum.type.label, color = c.goldText) }
        }
    }
}
