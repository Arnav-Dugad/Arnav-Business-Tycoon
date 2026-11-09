package com.arnavgames.tycoon.ui.dynasty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.Art
import com.arnavgames.tycoon.ui.common.Illustration
import java.util.Locale

/**
 * The Goals tab: everything that pays you for playing, in one place and in the order you'll want it (docs/UX.md):
 * a single "Claim everything" when more than one reward is waiting, the daily reward as a 7-day strip, the chapter
 * you're on, today's and this week's goals, the season, and the achievements summary. Nothing here expires in a way
 * that punishes you: a missed day pauses the reward ladder and never resets it.
 */
@Composable
fun GoalsScreen(vm: GameViewModel, onOpenAchievements: () -> Unit, onOpenDynasty: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val m = vm.dynastyMapper
    val daily = remember(s.meta, s.simTimeMs / 10_000) { m.daily(s) }
    val weekly = remember(s.meta, s.simTimeMs / 10_000) { m.weekly(s) }
    val ladder = remember(s.meta, s.simTimeMs / 60_000) { m.ladder(s) }
    val season = remember(s.meta, s.simTimeMs / 60_000) { m.season(s) }
    val achievements = remember(s.meta) { m.achievements(s) }
    val chapter = vm.engine.content.chapters.firstOrNull { it.number == s.progress.chapter }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { vm.reselect.collect { if (it == "goals") list.animateScrollToItem(0) } }

    // Every reward that can be claimed right now, as the commands that claim it.
    val claims = buildList {
        if (ladder.available) add(Command.ClaimDailyReward)
        s.progress.claimableMissions.sorted().forEach { add(Command.ClaimMission(it)) }
        daily.filter { it.done && !it.claimed }.forEach { add(Command.ClaimDaily(it.index)) }
        weekly.filter { it.done && !it.claimed }.forEach { add(Command.ClaimWeekly(it.index)) }
        season.tiers.filter { it.reached && !it.claimed }.forEach { add(Command.ClaimSeasonTier(it.tier)) }
        achievements.filter { it.state == 1 }.forEach { add(Command.ClaimAchievement(it.id)) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("goals-list"), state = list,
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = BottomBarReserve + Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(top = Space.xl, start = Space.xs)) {
                    Text("Goals", style = Aurum.type.hero, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(
                        if (claims.isEmpty()) "All caught up. Your next daily reward comes tomorrow." else "${claims.size} reward${if (claims.size == 1) "" else "s"} ready to collect",
                        style = Aurum.type.body, color = if (claims.isEmpty()) c.textSecondary else c.goldText,
                    )
                }
            }
            if (claims.size >= 2) item(key = "claim-all") {
                GoldButton("Claim everything · ${claims.size}", onClick = { vm.dispatchAll(claims, "Claimed ${claims.size} rewards") },
                    modifier = Modifier.fillMaxWidth().testTag("claim-everything"), leadingIcon = AbtIcons.Gift)
            }
            item(key = "ladder") { LadderCard(ladder) { vm.dispatch(Command.ClaimDailyReward) } }
            chapter?.let { ch ->
                item(key = "chapter") {
                    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        Illustration(Art.chapter(ch.number), Modifier.fillMaxWidth().height(110.dp), scrim = true) {
                            Column(Modifier.align(Alignment.BottomStart).padding(Space.md)) {
                                Text("CHAPTER ${ch.number}", style = Aurum.type.overline, color = c.gold)
                                Text(ch.title, style = Aurum.type.title, color = Color.White)
                            }
                        }
                        Column(Modifier.padding(Space.md)) {
                            ch.missions.forEach { mi ->
                                val complete = mi.id in s.progress.completedMissions
                                val claimable = mi.id in s.progress.claimableMissions
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    AbtIcon(if (complete) AbtIcons.Check else AbtIcons.Star, null, tint = if (complete) c.gain else c.textTertiary, size = 18.dp)
                                    Spacer(Modifier.width(Space.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(mi.title, style = Aurum.type.titleSmall, color = if (complete && !claimable) c.textTertiary else c.textPrimary)
                                        if (!complete) {
                                            Text(mi.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                                            Spacer(Modifier.height(Space.xxs))
                                            GoldProgress(vm.goalProgress(s, mi.goal).toFloat(), height = 3.dp, label = "${mi.title} progress")
                                        }
                                    }
                                    if (claimable) { Spacer(Modifier.width(Space.sm)); PillAction("Claim", true, { vm.dispatch(Command.ClaimMission(mi.id)) }) }
                                }
                            }
                        }
                    }
                }
            }
            if (daily.isNotEmpty()) {
                item(key = "daily-h") {
                    SectionHeader("Today") {
                        val ready = daily.filter { it.done && !it.claimed }
                        if (ready.size >= 2) PillAction("Claim ${ready.size}", true, { vm.dispatchAll(ready.map { Command.ClaimDaily(it.index) }, "Claimed ${ready.size} goals") })
                    }
                }
                item(key = "daily") { MissionList(daily, fmt, onClaim = { vm.dispatch(Command.ClaimDaily(it)) }, onReroll = { vm.dispatch(Command.RerollDaily(it)) }) }
            }
            if (weekly.isNotEmpty()) {
                item(key = "weekly-h") {
                    SectionHeader("This week") {
                        val ready = weekly.filter { it.done && !it.claimed }
                        if (ready.size >= 2) PillAction("Claim ${ready.size}", true, { vm.dispatchAll(ready.map { Command.ClaimWeekly(it.index) }, "Claimed ${ready.size} goals") })
                    }
                }
                item(key = "weekly") { MissionList(weekly, fmt, onClaim = { vm.dispatch(Command.ClaimWeekly(it)) }, onReroll = null) }
            }
            item(key = "season-h") { SectionHeader("Season") }
            item(key = "season") { SeasonCard(season, onClaim = { vm.dispatch(Command.ClaimSeasonTier(it)) }) }
            item(key = "ach") {
                val done = achievements.count { it.state == 2 }
                val ready = achievements.count { it.state == 1 }
                AurumCard(Modifier.fillMaxWidth().testTag("goals-achievements"), onClick = onOpenAchievements, highlighted = ready > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Medallion(earned = ready > 0 || done > 0, glowing = ready > 0, size = 48)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text("Achievements", style = Aurum.type.title, color = c.textPrimary)
                            Text("$done of ${achievements.size} earned" + if (ready > 0) " · $ready ready" else "", style = Aurum.type.bodySmall, color = if (ready > 0) c.goldText else c.textSecondary)
                        }
                        AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 18.dp)
                    }
                    Spacer(Modifier.height(Space.sm))
                    GoldProgress(if (achievements.isEmpty()) 0f else done / achievements.size.toFloat(), height = 4.dp, label = "Achievements earned")
                }
            }
            item(key = "dynasty-link") {
                PillAction("Your Dynasty, story and Legacy", true, onOpenDynasty, style = com.arnavgames.tycoon.designsystem.components.PillStyle.Glass, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The daily reward as a strip of seven days: claimed days ticked, today lit, the rest waiting. */
@Composable
private fun LadderCard(l: LadderUi, onClaim: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth().testTag("ladder"), highlighted = l.available) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily reward", style = Aurum.type.title, color = c.textPrimary)
                Text(buildList {
                    if (l.cashHours > 0) add("${String.format(Locale.US, "%.0f", l.cashHours)} h of income")
                    if (l.bullion > 0) add("${l.bullion} Bullion")
                    if (l.favor > 0) add("${l.favor} Favor")
                }.joinToString(" + "), style = Aurum.type.bodySmall, color = c.goldText)
            }
            PillAction(if (l.available) "Claim" else "Tomorrow", l.available, onClaim)
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            (1..7).forEach { d ->
                val past = d < l.day
                val today = d == l.day
                Box(
                    Modifier.weight(1f).aspectRatio(0.8f).clip(AurumShapes.small)
                        .background(
                            when {
                                today && l.available -> c.gold.copy(alpha = 0.35f)
                                past || (today && !l.available) -> c.gain.copy(alpha = 0.16f)
                                else -> c.separator
                            },
                        )
                        .then(if (today) Modifier.border(1.5.dp, c.gold, AurumShapes.small) else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = "Day $d" + when { past -> ", claimed"; today -> if (l.available) ", ready" else ", claimed today"; else -> "" } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$d", style = Aurum.type.label, color = if (today) c.goldText else c.textSecondary, textAlign = TextAlign.Center)
                        AbtIcon(
                            when { past || (today && !l.available) -> AbtIcons.Check; d == 7 -> AbtIcons.Crown; else -> AbtIcons.Gift },
                            null, tint = when { past || (today && !l.available) -> c.gain; today -> c.goldText; else -> c.textTertiary }, size = 16.dp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text("Missing a day pauses the ladder; it never resets.", style = Aurum.type.label, color = c.textTertiary)
    }
}

/** A trophy medallion: gold when earned, glowing when ready to claim, a quiet silhouette otherwise. */
@Composable
internal fun Medallion(earned: Boolean, glowing: Boolean, size: Int, locked: Boolean = false) {
    val c = Aurum.colors
    Box(
        Modifier.width(size.dp).aspectRatio(1f).clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                when {
                    glowing -> androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.goldBright, c.gold))
                    earned -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.goldFillTop, c.goldFillBottom))
                    else -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.separator, c.separator))
                },
            )
            .border(if (glowing) 2.dp else 1.dp, if (glowing) c.goldBright else if (earned) c.gold else c.separator, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AbtIcon(if (locked) AbtIcons.Lock else AbtIcons.Trophy, null, tint = if (earned || glowing) Color(0xFF3A2A0A) else c.textTertiary, size = (size * 0.45f).dp)
    }
}
