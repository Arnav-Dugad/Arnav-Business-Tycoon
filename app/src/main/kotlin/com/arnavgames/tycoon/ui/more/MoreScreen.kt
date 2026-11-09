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
 * The More tab: Collection and your Dynasty as the two big doors, then the Store, achievements, friends, account,
 * settings and credits. Badges show where something is waiting.
 */
@Composable
fun MoreScreen(
    vm: GameViewModel,
    onOpenCollection: () -> Unit, onOpenDynasty: () -> Unit,
    onOpenStore: () -> Unit, onOpenAchievements: () -> Unit, onOpenSocial: () -> Unit, onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit, onOpenCredits: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val collection by vm.collection.collectAsStateWithLifecycle()
    val dynastyBadge by vm.dynastyBadge.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val dy = remember(s.dynasty, s.meta, s.progress, s.simTimeMs / 60_000) { vm.dynastyMapper.dynasty(s) }
    val col = collection
    LazyColumn(
        Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("more-list"),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = BottomBarReserve + Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "title") {
            Text("More", style = Aurum.type.hero, color = c.textPrimary, modifier = Modifier.statusBarsPadding().padding(top = Space.xl, start = Space.xs).semantics { heading() })
        }
        item(key = "doors") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Door(
                    Modifier.weight(1f).fillMaxHeight().testTag("door-collection"), AbtIcons.Collection, "Collection",
                    when {
                        col == null -> ""
                        !col.unlocked -> "Opens in chapter ${col.unlockChapter}"
                        else -> "${col.tier} · ${MoneyFormat.compact(col.value, vm.notation(settings))}"
                    },
                    badge = col?.auction?.let { "Auction live" }, onClick = onOpenCollection, locked = col?.unlocked == false,
                    art = com.arnavgames.tycoon.ui.common.Art.luxuryCategory("painting"),
                )
                Door(
                    Modifier.weight(1f).fillMaxHeight().testTag("door-dynasty"), AbtIcons.Crown, "Your Dynasty",
                    "Generation ${dy.generation}" + if (dy.canPrestige) " · crown ready" else "",
                    badge = if (dynastyBadge) "Rewards waiting" else null, onClick = onOpenDynasty,
                    art = com.arnavgames.tycoon.ui.common.Art.chapter(8),
                )
            }
        }
        item(key = "play-header") { com.arnavgames.tycoon.designsystem.components.SectionHeader("Play") }
        item(key = "menu") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                MenuRow("Store", AbtIcons.Bullion, onOpenStore, subtitle = "${s.ledger.bullion.centsOrNull ?: 0} Bullion · boosts, upgrades and cosmetics", modifier = Modifier.testTag("menu-store"))
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow(
                    "Achievements", AbtIcons.Trophy, onOpenAchievements,
                    subtitle = "${dy.achievementsDone} of ${dy.achievementsTotal}" + if (dy.achievementsClaimable > 0) " · ${dy.achievementsClaimable} to claim" else "",
                )
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Friends & leaderboards", AbtIcons.Rivals, onOpenSocial, subtitle = "Friend codes, leagues, gifts and Sealed Lots")
            }
        }
        item(key = "you-header") { com.arnavgames.tycoon.designsystem.components.SectionHeader("You") }
        item(key = "menu-you") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                MenuRow("Account & cloud save", AbtIcons.Cloud, onOpenAccount, subtitle = "Optional: back up, sync and add friends")
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Settings", AbtIcons.Sliders, onOpenSettings, subtitle = "Theme, sound, notifications, save backup")
                HairlineDivider(Modifier.padding(start = 60.dp))
                MenuRow("Credits & licenses", AbtIcons.Book, onOpenCredits)
            }
        }
    }
}

/** One of the two big doors: icon, name, a live line, and a badge when something is waiting. */
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
