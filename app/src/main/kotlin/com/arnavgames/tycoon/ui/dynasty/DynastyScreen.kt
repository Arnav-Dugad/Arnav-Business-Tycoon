package com.arnavgames.tycoon.ui.dynasty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.CrownMark
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.MenuRow
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.collection.ItemImage
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.durationText
import java.util.Locale

/** 24,035,878 → "24.0M"; small numbers stay exact. */
private fun compactCount(n: Long): String = when {
    n >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 100_000L -> String.format(Locale.US, "%.0fK", n / 1e3)
    else -> String.format(Locale.US, "%,d", n)
}

private fun fmtMult(x: Double) = "×" + String.format(Locale.US, if (x < 10) "%.2f" else "%,.0f", x)

/** Missions, rewards, seasons, the Dynasty and the story (docs/GDD.md §12). */
@Composable
fun DynastyScreen(
    vm: GameViewModel, onOpenSettings: () -> Unit, onOpenCredits: () -> Unit,
    onOpenAchievements: () -> Unit = {}, onOpenCrown: () -> Unit = {}, onOpenStore: () -> Unit = {}, onOpenAccount: () -> Unit = {}, onOpenSocial: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val c = Aurum.colors
    val content = vm.engine.content
    val m = vm.dynastyMapper
    val dy = remember(s.dynasty, s.meta, s.progress, s.holdingName, s.simTimeMs / 60_000) { m.dynasty(s) }
    val daily = remember(s.meta, s.simTimeMs / 10_000) { m.daily(s) }
    val weekly = remember(s.meta, s.simTimeMs / 10_000) { m.weekly(s) }
    val ladder = remember(s.meta, s.simTimeMs / 60_000) { m.ladder(s) }
    var ipoConfirm by remember { mutableStateOf<Double?>(null) }
    var avatarPicker by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf<com.arnavgames.tycoon.ui.common.Term?>(null) }
    val update by vm.updates.state.collectAsStateWithLifecycle()
    val cloudConflict by vm.cloudConflict.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(GlassLayer.Content),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = BottomBarReserve + Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (onBack != null) item { com.arnavgames.tycoon.ui.common.BackChip(onBack, Modifier.statusBarsPadding().padding(top = Space.xs)) }
            item {
                Column(Modifier.fillMaxWidth().then(if (onBack == null) Modifier.statusBarsPadding().padding(top = Space.xl) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The family seat, painted, with your avatar (the crest until you choose one) set into it; tap to change it.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.chapter(8), Modifier.fillMaxWidth().padding(bottom = 42.dp).height(150.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large), scrim = true)
                        Box(Modifier.clickable(onClickLabel = "Change avatar") { avatarPicker = true }.testTag("my-avatar")) {
                            if (s.store.avatar == null && s.store.frame == null) CrownMark(size = 84.dp)
                            else com.arnavgames.tycoon.ui.common.AvatarBadge(s.store.avatar, s.store.frame, 84.dp)
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Text("Your Dynasty", style = Aurum.type.display, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    val title = s.store.title?.let { content.store.cosmeticById[it]?.name }
                    Text("Generation ${dy.generation}" + (dy.trait?.let { " · $it" } ?: "") + (title?.let { " · $it" } ?: ""), style = Aurum.type.bodySmall, color = c.textSecondary)
                    if (s.store.boosted) { Spacer(Modifier.height(Space.xs)); AurumChip("Boosted (demo purchases)", icon = AbtIcons.Bolt, tint = c.gain) }
                }
            }
            if (cloudConflict) {
                item(key = "cloud-conflict") {
                    AurumCard(Modifier.fillMaxWidth(), highlighted = true, onClick = onOpenAccount) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AbtIcon(AbtIcons.Cloud, null, tint = c.goldText, size = 24.dp)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text("Choose which save to keep", style = Aurum.type.titleSmall, color = c.textPrimary)
                                Text("This device and your cloud save both changed. Nothing was overwritten. Tap to choose.", style = Aurum.type.bodySmall, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
            if (update !is com.arnavgames.tycoon.update.UpdateState.Idle) {
                item(key = "update") { com.arnavgames.tycoon.ui.common.UpdateCard(vm.updates, settings.autoUpdate) }
            }
            // Legacy is the hero only once it matters (crown ready, or a later generation); before that, today's goals lead.
            val legacyMatters = dy.canPrestige || dy.legacy > 0 || dy.generation > 1
            if (legacyMatters) item(key = "dynasty") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = dy.canPrestige) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("LEGACY", style = Aurum.type.overline, color = c.goldText)
                            Text(String.format(Locale.US, "%,d", dy.legacy), style = Aurum.type.numberHero, color = c.textPrimary)
                            Text("Income ${fmtMult(dy.multiplier)} from Legacy", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        dy.challenge?.let { AurumChip(it, icon = AbtIcons.Shield, tint = c.goldText) }
                    }
                    dy.traitText?.let { Spacer(Modifier.height(Space.xs)); Text("Heir trait: ${dy.trait}. $it", style = Aurum.type.bodySmall, color = c.textSecondary) }
                    Spacer(Modifier.height(Space.md))
                    if (dy.canPrestige) {
                        Text("The founder can retire. The heir starts again with ${String.format(Locale.US, "%,d", dy.legacyGain)} more Legacy.", style = Aurum.type.body, color = c.textSecondary)
                        Spacer(Modifier.height(Space.sm))
                        GoldButton("Pass the Crown", leadingIcon = AbtIcons.Crown, onClick = onOpenCrown, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Finish chapter 8 to pass the crown. Your heir restarts with Legacy (a permanent income multiplier), a trait, heirlooms and a Family Charter.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
            // Daily reward, goals, the season and achievements live on the Goals tab now.
            if (!legacyMatters) item(key = "legacy-teaser") {
                AurumCard(Modifier.fillMaxWidth(), onClick = { explain = com.arnavgames.tycoon.ui.common.Term.Legacy }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Crown, null, tint = c.goldText, size = 24.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text("Legacy", style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("After Chapter 8 you pass the crown to an heir, who keeps a permanent income boost.", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        AbtIcon(AbtIcons.Info, "What is Legacy?", tint = c.textTertiary, size = 18.dp)
                    }
                }
            }
            if (dy.ipoAvailable || dy.ipoFloat != null) item(key = "ipo") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("IPO", style = Aurum.type.overline, color = c.goldText)
                    if (dy.ipoFloat != null) {
                        Text("Listed: ${(dy.ipoFloat * 100).toInt()}% of your holding trades publicly.", style = Aurum.type.body, color = c.textSecondary)
                    } else {
                        Text("Take your holding company public. Sell a share to investors for cash now; that share of business income goes to them from then on.", style = Aurum.type.body, color = c.textSecondary)
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            dy.ipoFloats.forEach { f -> PillAction("${(f * 100).toInt()}% · ${fmt(DynastyMapper.ipoRaise(dy.ipoValuation, f))}", true, { ipoConfirm = f }, style = PillStyle.Glass) }
                        }
                    }
                }
            }
            item { SectionHeader("Story") }
            content.chapters.forEach { ch ->
                val unlocked = ch.number <= s.progress.chapter
                val done = ch.missions.count { it.id in s.progress.completedMissions }
                item(key = "ch-${ch.number}") {
                    AurumCard(Modifier.fillMaxWidth(), highlighted = ch.number == s.progress.chapter) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("CHAPTER ${ch.number}", style = Aurum.type.overline, color = if (unlocked) c.goldText else c.textTertiary)
                                Text(ch.title, style = Aurum.type.headline, color = if (unlocked) c.textPrimary else c.textTertiary)
                            }
                            when {
                                !unlocked -> AbtIcon(AbtIcons.Lock, "Locked", tint = c.textTertiary)
                                done == ch.missions.size -> AurumChip("Complete", icon = AbtIcons.Check, tint = c.gain)
                                else -> Text("$done/${ch.missions.size}", style = Aurum.type.number, color = c.textSecondary)
                            }
                        }
                        if (unlocked) {
                            Spacer(Modifier.height(Space.xs))
                            Text(ch.summary, style = Aurum.type.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(Space.md))
                            ch.missions.forEach { mi ->
                                val complete = mi.id in s.progress.completedMissions
                                val claimable = mi.id in s.progress.claimableMissions
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    AbtIcon(if (complete) AbtIcons.Check else AbtIcons.Star, null, tint = if (complete) c.gain else c.textTertiary, size = 18.dp)
                                    Spacer(Modifier.width(Space.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(mi.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                                        Text(mi.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                                        if (!complete) {
                                            Spacer(Modifier.height(Space.xxs))
                                            GoldProgress(vm.goalProgress(s, mi.goal).toFloat(), height = 3.dp, label = "${mi.title} progress")
                                        }
                                    }
                                    if (claimable) {
                                        Spacer(Modifier.width(Space.sm))
                                        GoldButton("Claim", onClick = { vm.dispatch(Command.ClaimMission(mi.id)) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        explain?.let { com.arnavgames.tycoon.ui.common.GlossarySheet(it) { explain = null } }
        if (avatarPicker) {
            com.arnavgames.tycoon.designsystem.components.AurumDialog(title = "Choose your avatar", onDismiss = { avatarPicker = false }) {
                com.arnavgames.tycoon.ui.common.AvatarPicker(
                    current = s.store.avatar, owned = s.store.cosmetics,
                    onPick = { id -> vm.dispatch(Command.EquipCosmetic(id, "avatar")); avatarPicker = false },
                    onLocked = { avatarPicker = false; vm.say("That avatar is in the Store (Cosmetics), bought with Bullion you earn in play."); onOpenStore() },
                )
                Text("Frames from the Store ring your avatar. Friends see it on your profile.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        ipoConfirm?.let { f ->
            AurumDialog(title = "List ${(f * 100).toInt()}% of your holding?", onDismiss = { ipoConfirm = null }) {
                Text("You receive ${fmt(DynastyMapper.ipoRaise(dy.ipoValuation, f))} now. From then on ${(f * 100).toInt()}% of all business income goes to your new shareholders. This cannot be undone this generation.", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Ring the bell", onClick = { ipoConfirm = null; vm.dispatch(Command.LaunchIpo(f)) }, modifier = Modifier.fillMaxWidth())
                GhostButton("Not now", onClick = { ipoConfirm = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun MissionList(items: List<MissionUi>, fmt: (Money) -> String, onClaim: (Int) -> Unit, onReroll: ((Int) -> Unit)?) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
        items.forEachIndexed { i, m ->
            if (i > 0) HairlineDivider(Modifier.padding(vertical = Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = Aurum.type.titleSmall, color = if (m.claimed) c.textTertiary else c.textPrimary)
                    Text(m.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                    Text(buildList { if (m.reward.isPositive) add("+" + fmt(m.reward)); if (m.bullion > 0) add("+${m.bullion} Bullion") }.joinToString(" · "),
                        style = Aurum.type.label, color = c.goldText)
                    if (!m.done) { Spacer(Modifier.height(Space.xxs)); GoldProgress(m.progress, height = 3.dp, label = "${m.title} progress") }
                }
                Spacer(Modifier.width(Space.sm))
                when {
                    m.claimed -> AbtIcon(AbtIcons.Check, "Claimed", tint = c.gain, size = 20.dp)
                    m.done -> PillAction("Claim", true, { onClaim(m.index) })
                    onReroll != null -> PillAction("Swap", true, { onReroll(m.index) }, style = PillStyle.Glass)
                }
            }
        }
    }
}

@Composable
internal fun SeasonCard(season: SeasonUi, onClaim: (Int) -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SEASON", style = Aurum.type.overline, color = c.goldText)
                Text(season.name, style = Aurum.type.headline, color = c.textPrimary)
                Text("${season.xp} season XP" + (season.nextTierXp?.let { " · next tier at $it" } ?: "") + " · ends in ${durationText(season.endsInMs)}", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        season.event?.let { ev ->
            Spacer(Modifier.height(Space.sm))
            Text("This week: $ev", style = Aurum.type.titleSmall, color = c.textPrimary)
            season.eventText?.let { Text(it, style = Aurum.type.bodySmall, color = c.textSecondary) }
            season.eventGoals.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(top = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                    AbtIcon(if (g.claimed) AbtIcons.Check else AbtIcons.Star, null, tint = if (g.claimed) c.gain else c.textTertiary, size = 16.dp)
                    Spacer(Modifier.width(Space.xs))
                    Text("${g.description} (+${g.xp} XP)", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                    if (!g.claimed) Text("${(g.progress * 100).toInt()}%", style = Aurum.type.label, color = c.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            season.tiers.take(30).forEach { t ->
                Column(
                    Modifier.width(112.dp).clip(AurumShapes.medium).background(if (t.reached && !t.claimed) c.gold.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.06f))
                        .clickable(enabled = t.reached && !t.claimed, role = Role.Button) { onClaim(t.tier) }.padding(Space.sm),
                ) {
                    Text("Tier ${t.tier}", style = Aurum.type.label, color = if (t.reached) c.goldText else c.textTertiary)
                    Text(t.reward, style = Aurum.type.bodySmall, color = c.textPrimary, maxLines = 2)
                    Text(if (t.claimed) "Claimed" else if (t.reached) "Tap to claim" else "${t.xp} XP", style = Aurum.type.label, color = if (t.claimed) c.gain else c.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text("Earn XP from daily and weekly missions and weekly event goals. The premium track arrives with the demo Season Pass.", style = Aurum.type.label, color = c.textTertiary)
    }
}

/**
 * Achievements as a trophy cabinet: a summary with your progress, category chips, and a two-column grid of medallions:
 * gold when earned, glowing with a Claim button when ready, a quiet silhouette with the condition when not yet. Ready
 * ones come first, then those still to earn, then the ones you have. Claim all sits at the bottom edge.
 */
@Composable
fun AchievementsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val list = remember(s.meta) { vm.dynastyMapper.achievements(s) }
    val c = Aurum.colors
    var show by com.arnavgames.tycoon.ui.common.rememberPref("achievements.show", "all")
    var category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    val ready = list.filter { it.state == 1 }
    val earned = list.count { it.state >= 1 }
    val bullionEarned = list.filter { it.state == 2 }.sumOf { it.bullion }
    val shown = list
        .filter { category == "all" || it.category == category }
        .filter { when (show) { "ready" -> it.state == 1; "todo" -> it.state == 0; "done" -> it.state == 2; else -> true } }
        .sortedBy { when (it.state) { 1 -> 0; 0 -> 1; else -> 2 } }
    GlassScaffold(title = "Achievements", onBack = onBack, bottomBar = if (ready.isNotEmpty()) ({
        com.arnavgames.tycoon.designsystem.components.GoldButton(
            "Claim all ${ready.size} · +${ready.sumOf { it.bullion }} Bullion",
            onClick = { vm.dispatchAll(ready.map { Command.ClaimAchievement(it.id) }, "Claimed ${ready.size} achievements") },
            modifier = Modifier.fillMaxWidth().testTag("claim-all-achievements"), leadingIcon = AbtIcons.Trophy,
        )
    }) else null) {
        item(key = "summary") {
            AurumCard(Modifier.fillMaxWidth(), highlighted = ready.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medallion(earned = earned > 0, glowing = ready.isNotEmpty(), size = 64)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("$earned of ${list.size}", style = Aurum.type.numberHero, color = c.textPrimary)
                        Text("earned · $bullionEarned Bullion collected so far", style = Aurum.type.bodySmall, color = c.textSecondary)
                        if (ready.isNotEmpty()) Text("${ready.size} ready to claim", style = Aurum.type.label, color = c.goldText)
                    }
                }
                Spacer(Modifier.height(Space.md))
                GoldProgress(if (list.isEmpty()) 0f else earned / list.size.toFloat(), height = 6.dp, label = "Achievements earned")
            }
        }
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    (listOf("all") + list.map { it.category }.distinct()).forEach { cat ->
                        val sel = category == cat
                        val n = if (cat == "all") list.size else list.count { it.category == cat }
                        PillAction(if (cat == "all") "All · $n" else "$cat · $n", true, { category = cat }, style = if (sel) PillStyle.Gold else PillStyle.Glass)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${shown.size} shown", style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f).padding(start = Space.xs))
                    com.arnavgames.tycoon.ui.common.SortToggle(
                        listOf(com.arnavgames.tycoon.ui.common.Choice("all", "Show all"), com.arnavgames.tycoon.ui.common.Choice("ready", "Ready to claim"),
                            com.arnavgames.tycoon.ui.common.Choice("todo", "Not yet"), com.arnavgames.tycoon.ui.common.Choice("done", "Earned")),
                        show, { show = it },
                    )
                }
            }
        }
        if (shown.isEmpty()) item(key = "none") { Text("Nothing here yet.", style = Aurum.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs)) }
        shown.chunked(2).forEachIndexed { i, pair ->
            item(key = "row-$category-$show-$i") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    pair.forEach { a -> AchievementTile(a, Modifier.weight(1f).heightIn(min = 150.dp)) { vm.dispatch(Command.ClaimAchievement(a.id)) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(a: AchievementUi, modifier: Modifier, onClaim: () -> Unit) {
    val c = Aurum.colors
    AurumCard(modifier.testTag("ach-${a.id}"), highlighted = a.state == 1, contentPadding = PaddingValues(Space.md)) {
        Medallion(earned = a.state == 2, glowing = a.state == 1, size = 44, locked = a.state == 0)
        Spacer(Modifier.height(Space.sm))
        Text(a.title, style = Aurum.type.titleSmall, color = if (a.state == 0) c.textSecondary else c.textPrimary, maxLines = 2)
        Text(a.description, style = Aurum.type.bodySmall, color = c.textTertiary, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(Space.sm))
        when (a.state) {
            1 -> PillAction("Claim +${a.bullion}", true, onClaim, modifier = Modifier.fillMaxWidth())
            2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Check, null, tint = c.gain, size = 14.dp)
                Spacer(Modifier.width(Space.xxs))
                Text("Earned · ${a.bullion} Bullion", style = Aurum.type.label, color = c.gain)
            }
            else -> Text("${a.bullion} Bullion", style = Aurum.type.label, color = c.goldText)
        }
    }
}

@Composable
fun CrownScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.dynasty.generation, s.luxury.owned) { vm.dynastyMapper.crown(s) { null } }
    val c = Aurum.colors
    var trait by rememberSaveable { mutableStateOf(ui.traits.firstOrNull()?.first) }
    var heirlooms by remember { mutableStateOf(setOf<String>()) }
    var charter by remember { mutableStateOf(mapOf<String, String>()) } // "branch:tier" -> node id
    var challenge by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // The one irreversible action is pinned to the bottom edge with its outcome, never below the fold.
        GlassScaffold(title = "Pass the Crown", onBack = onBack, bottomBar = {
            Column {
                Text(
                    (ui.traits.firstOrNull { it.first == trait }?.second?.let { "Heir: $it" } ?: "Choose your heir's trait") +
                        if (heirlooms.isNotEmpty()) " · ${heirlooms.size} heirloom${if (heirlooms.size == 1) "" else "s"}" else "",
                    style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
                GoldButton("Retire the founder · +${compactCount(ui.legacyGain)} Legacy", leadingIcon = AbtIcons.Crown, enabled = trait != null,
                    onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().testTag("crown-go"))
            }
        }) {
            item(key = "legacy") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Text("GENERATION ${ui.generation}", style = Aurum.type.overline, color = c.goldText)
                    Text("+${compactCount(ui.legacyGain)}", style = Aurum.type.numberHero, color = c.textPrimary, maxLines = 1)
                    Text("Legacy", style = Aurum.type.label, color = c.goldText)
                    Text("Total ${String.format(Locale.US, "%,d", ui.newLegacy)} · income ${fmtMult(ui.multiplier)} for every business, forever.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.xs))
                    Text("Your heir starts again from a single kiosk. Businesses, cash, investments, properties and collections are left behind, except the heirlooms you choose. Bullion, Favor, achievements and Legacy carry over.", style = Aurum.type.bodySmall, color = c.textSecondary)
                }
            }
            item(key = "traits-h") { SectionHeader("Heir trait") }
            items(ui.traits, key = { "t-${it.first}" }) { (id, name, text) ->
                ChoiceRow(name, text, selected = trait == id, enabled = true) { trait = id }
            }
            item(key = "heir-h") { SectionHeader("Heirlooms · up to ${ui.heirloomsAllowed}") }
            if (ui.heirlooms.isEmpty()) item(key = "no-heir") { Text("You own no luxury pieces to pass on.", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs)) }
            items(ui.heirlooms.take(30), key = { "h-${it.id}" }) { h ->
                val on = h.id in heirlooms
                Row(
                    Modifier.fillMaxWidth().clip(AurumShapes.medium).background(if (on) c.gold.copy(alpha = 0.16f) else c.textPrimary.copy(alpha = 0.06f))
                        .clickable(role = Role.Checkbox) { heirlooms = if (on) heirlooms - h.id else if (heirlooms.size < ui.heirloomsAllowed) heirlooms + h.id else heirlooms }
                        .padding(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemImage(h.image, h.icon, h.name, Modifier.size(44.dp).clip(AurumShapes.small), iconSize = 20.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1)
                        Text("${h.maker} · ${fmt(h.value)}", style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 1)
                    }
                    if (on) AbtIcon(AbtIcons.Check, "Selected", tint = c.goldText, size = 20.dp)
                }
            }
            item(key = "charter-h") { SectionHeader("Family Charter") }
            item(key = "charter-note") { Text("One choice per tier. Tiers open as Legacy grows; you can choose again at every prestige.", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs)) }
            ui.charter.filter { t -> t.choices.any { it.unlocked } }.forEach { t ->
                item(key = "ct-${t.branch}-${t.tier}") {
                    Column {
                        Text("${t.branch} · tier ${t.tier}", style = Aurum.type.label, color = c.goldText, modifier = Modifier.padding(start = Space.xs, bottom = Space.xxs))
                        t.choices.forEach { ch ->
                            val key = "${t.branch}:${t.tier}"
                            ChoiceRow(ch.name, if (ch.unlocked) ch.description else ch.requirement, selected = charter[key] == ch.id, enabled = ch.unlocked) {
                                charter = if (charter[key] == ch.id) charter - key else charter + (key to ch.id)
                            }
                            Spacer(Modifier.height(Space.xxs))
                        }
                    }
                }
            }
            item(key = "challenge-h") { SectionHeader("Challenge (optional)") }
            item(key = "challenge-none") { ChoiceRow("No challenge", "Play the next generation normally.", selected = challenge == null, enabled = true) { challenge = null } }
            items(ui.challenges, key = { "c-${it.first}" }) { (id, name, text) -> ChoiceRow(name, text, selected = challenge == id, enabled = true) { challenge = id } }
        }
        if (confirm) {
            AurumDialog(title = "Retire the founder?", onDismiss = { confirm = false }) {
                Text("Your heir begins generation ${ui.generation} with ${String.format(Locale.US, "%,d", ui.newLegacy)} Legacy (${fmtMult(ui.multiplier)} income)" +
                    (if (heirlooms.isNotEmpty()) " and ${heirlooms.size} heirloom" + (if (heirlooms.size > 1) "s" else "") else "") + ". Everything else starts over. This cannot be undone.",
                    style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Pass the Crown", modifier = Modifier.fillMaxWidth().testTag("crown-confirm"), onClick = {
                    confirm = false
                    vm.dispatch(Command.PassTheCrown(trait!!, heirlooms.toList(), challenge, charter.values.toSet())) { onBack() }
                })
                GhostButton("Not yet", onClick = { confirm = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().clip(AurumShapes.medium).background(if (selected) c.gold.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.06f))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Aurum.type.titleSmall, color = if (enabled) c.textPrimary else c.textTertiary)
            Text(text, style = Aurum.type.bodySmall, color = if (enabled) c.textSecondary else c.textTertiary)
        }
        if (selected) AbtIcon(AbtIcons.Check, "Selected", tint = c.goldText, size = 20.dp)
    }
}
