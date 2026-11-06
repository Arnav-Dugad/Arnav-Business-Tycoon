package com.arnavgames.tycoon.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.BuildConfig
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.ChangeLabel
import com.arnavgames.tycoon.designsystem.components.CrownMark
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.StatPill
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.ThemeMode

/**
 * Phase 1 foundation screen: proves the build, fonts, color tokens (both themes) and the custom icon set
 * on a real device. It is replaced by onboarding + the Empire tab in Phase 3.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoundationScreen(themeMode: ThemeMode, onToggleTheme: () -> Unit) {
    val c = Aurum.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.fillMaxWidth().height(360.dp).background(c.heroWash))
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StatPill(AbtIcons.Cash, "$1,250.00", "Cash")
                StatPill(AbtIcons.Bullion, "40", "Bullion", tint = c.bullion)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.xxxl))
            CrownMark(size = 112.dp)
            Spacer(Modifier.height(Space.xl))
            Text("ARNAV GAMES", style = Aurum.type.overline, color = c.goldText)
            Spacer(Modifier.height(Space.sm))
            Text(
                "Arnav Business Tycoon",
                style = Aurum.type.hero,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.sm))
            Text("Build the empire. Pass on the crown.", style = Aurum.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xxl))

            AurumCard(Modifier.fillMaxWidth().widthIn(max = 520.dp), highlighted = true) {
                Text("NET WORTH", style = Aurum.type.overline, color = c.textTertiary)
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$12,480,516.42", style = Aurum.type.numberHero, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    ChangeLabel(4.82)
                    ChangeLabel(-1.37)
                    Text("today", style = Aurum.type.bodySmall, color = c.textTertiary)
                }
            }

            Spacer(Modifier.height(Space.lg))
            SectionHeader("Icon set preview", Modifier.widthIn(max = 520.dp))
            AurumCard(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    PreviewIcons.forEach { (name, icon) ->
                        AbtIcon(icon, contentDescription = name, tint = if (name in GoldNames) c.goldText else c.textSecondary)
                    }
                }
            }

            Spacer(Modifier.height(Space.xxl))
            GoldButton("Begin your empire", onClick = {}, modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp), leadingIcon = AbtIcons.Crown)
            Spacer(Modifier.height(Space.md))
            GhostButton(
                text = if (Aurum.colors.isDark) "Switch to light theme" else "Switch to dark theme",
                onClick = onToggleTheme,
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                leadingIcon = if (Aurum.colors.isDark) AbtIcons.Sun else AbtIcons.Moon,
            )
            Spacer(Modifier.height(Space.xxl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Info, null, tint = c.textTertiary, size = 16.dp)
                Spacer(Modifier.width(Space.xs))
                Text(
                    "Foundation build ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.HAS_FIREBASE) "cloud ready" else "offline edition"} · theme ${themeMode.name.lowercase()}",
                    style = Aurum.type.bodySmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}

private val GoldNames = setOf("Crown", "Bullion", "Trophy", "Star", "Sparkle")

private val PreviewIcons = listOf(
    "Empire" to AbtIcons.Empire, "Markets" to AbtIcons.Markets, "Estates" to AbtIcons.Estates,
    "Collection" to AbtIcons.Collection, "Crown" to AbtIcons.Crown, "Cash" to AbtIcons.Cash,
    "Bullion" to AbtIcons.Bullion, "Coin" to AbtIcons.Coin, "Favor" to AbtIcons.Favor,
    "Briefcase" to AbtIcons.Briefcase, "Factory" to AbtIcons.Factory, "Manager" to AbtIcons.Manager,
    "Expand" to AbtIcons.Expand, "Timer" to AbtIcons.Timer, "Bolt" to AbtIcons.Bolt,
    "Trophy" to AbtIcons.Trophy, "Star" to AbtIcons.Star, "Sparkle" to AbtIcons.Sparkle,
    "Shield" to AbtIcons.Shield, "News" to AbtIcons.News, "Map" to AbtIcons.Map,
    "Rivals" to AbtIcons.Rivals, "Gift" to AbtIcons.Gift, "Store" to AbtIcons.Store,
    "Bell" to AbtIcons.Bell, "Inbox" to AbtIcons.Inbox, "Chart" to AbtIcons.Chart,
    "Hand" to AbtIcons.Hand, "Lock" to AbtIcons.Lock, "Cloud" to AbtIcons.Cloud,
    "Volume" to AbtIcons.Volume, "Music" to AbtIcons.Music, "Vibrate" to AbtIcons.Vibrate,
    "Sliders" to AbtIcons.Sliders, "Mail" to AbtIcons.Mail, "User" to AbtIcons.User,
)
