package com.arnavgames.tycoon.ui.dynasty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.CrownMark
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.MenuRow
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel

/** Story progress, missions, and the menu (settings, credits). Dynasty prestige arrives in Phase 10. */
@Composable
fun DynastyScreen(vm: GameViewModel, onOpenSettings: () -> Unit, onOpenCredits: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val content = vm.engine.content
    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.surfaceRaised, c.background))),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.huge),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                CrownMark(size = 84.dp)
                Spacer(Modifier.height(Space.md))
                Text("Your Story", style = Aurum.type.display, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text("Founder · Generation 1", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
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
                        ch.missions.forEach { m ->
                            val complete = m.id in s.progress.completedMissions
                            val claimable = m.id in s.progress.claimableMissions
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                AbtIcon(if (complete) AbtIcons.Check else AbtIcons.Star, null, tint = if (complete) c.gain else c.textTertiary, size = 18.dp)
                                Spacer(Modifier.width(Space.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(m.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                                    Text(m.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                                    if (!complete) {
                                        Spacer(Modifier.height(Space.xxs))
                                        GoldProgress(vm.goalProgress(s, m.goal).toFloat(), height = 3.dp, label = "${m.title} progress")
                                    }
                                }
                                if (claimable) {
                                    Spacer(Modifier.width(Space.sm))
                                    GoldButton("Claim", onClick = { vm.dispatch(Command.ClaimMission(m.id)) })
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbtIcon(AbtIcons.Crown, null, tint = c.goldText, size = 28.dp)
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text("Pass the Crown", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("Retire your founder and start a stronger generation. Unlocks after Chapter 8.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
        item { SectionHeader("More") }
        item {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xs)) {
                MenuRow("Settings", AbtIcons.Sliders, onOpenSettings, subtitle = "Theme, sound, haptics, save backup")
                MenuRow("Credits & licenses", AbtIcons.Book, onOpenCredits)
            }
        }
    }
}
