package com.arnavgames.tycoon.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/**
 * Plain-language explanations of the game's own words (docs/PLAN_2.0.md F8). Anything with a made-up name gets an
 * entry: what it is, how you get it, what it's for. Opened by tapping the term wherever it appears.
 */
enum class Term(val title: String, val icon: ImageVector, val what: String, val get: String, val use: String) {
    Cash(
        "Cash", AbtIcons.Cash,
        "The money you can spend right now.",
        "Your businesses earn it every second, even while you're away (with a manager). Tapping the counter, rent, dividends and rewards add more.",
        "Levels, new businesses, managers, expansions, investments, property and luxury.",
    ),
    NetWorth(
        "Net worth", AbtIcons.Chart,
        "Everything you own minus everything you owe: cash, businesses, investments, property and collections, less loans.",
        "Grows as you earn and as what you own rises in value.",
        "Chapter goals, the Fortune list of billionaires and leaderboards are all measured by net worth.",
    ),
    Income(
        "Income per second", AbtIcons.ArrowUp,
        "How much cash all your businesses earn each second, after upkeep.",
        "Buy levels, reach new tiers (levels 10, 25, 50…), hire managers and finish expansions.",
        "Decision costs are shown in \"hours of income\" so they always fit your empire's size.",
    ),
    Bullion(
        "Bullion", AbtIcons.Bullion,
        "The game's premium currency (the gold bars).",
        "Earned free from story missions, daily and weekly goals, achievements, the daily reward and season tiers. The demo store can also grant it, but no real money is ever charged.",
        "Spend it in the Store on boosts, upgrades, titles, frames and avatars.",
    ),
    Favor(
        "Favor", AbtIcons.Favor,
        "Goodwill you've built up (the heart).",
        "From missions, achievements, the Market Timing practice game and gifts from friends.",
        "Rush a timer so an expansion or renovation finishes now.",
    ),
    Economy(
        "The economy", AbtIcons.Globe,
        "The world moves through four phases: Expansion, Boom, Slowdown and Recession. Each lasts a few days.",
        "The news hints at what's coming next.",
        "Booms lift sales and prices; recessions make property and stocks cheaper. The central-bank rate sets what loans cost and what bonds pay.",
    ),
    Legacy(
        "Legacy", AbtIcons.Crown,
        "Your family's permanent advantage.",
        "Earned when you pass the crown to an heir after Chapter 8. The richer you became, the more Legacy you earn.",
        "Every Legacy point raises the next generation's income for good, and unlocks Family Charter perks.",
    ),
    Status(
        "Status", AbtIcons.Gem,
        "How prestigious your collection looks to the world.",
        "Displayed luxury items earn it: put pieces in your showrooms (only displayed items count). Rare and legendary pieces earn more.",
        "Higher Status tiers get dealer discounts, invitations to better auctions and friendlier rivals.",
    ),
    SeasonXp(
        "Season XP", AbtIcons.Star,
        "Progress on the current 4-week season.",
        "Every daily and weekly goal gives some.",
        "Each 100 XP reaches a season tier with a reward. The free track holds everything that affects play.",
    ),
    Hours(
        "Hours of income", AbtIcons.Clock,
        "Costs and rewards on the Desk are measured in time: \"1 hour\" means what your empire earns in one hour.",
        "It's worked out from your income when you decide.",
        "So a choice is always meaningful, whether you run a kiosk or an airline.",
    ),
}

/** The explanation sheet for one term. */
@Composable
fun GlossarySheet(term: Term, onDismiss: () -> Unit) {
    val c = Aurum.colors
    AurumDialog(title = term.title, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.Top) {
            AbtIcon(term.icon, null, tint = c.goldText, size = 22.dp)
            Spacer(Modifier.width(Space.md))
            Text(term.what, style = Aurum.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Fact("How you get it", term.get)
            Fact("What it's for", term.use)
        }
        GoldButton("Got it", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Fact(label: String, body: String) {
    val c = Aurum.colors
    Column {
        Text(label.uppercase(), style = Aurum.type.overline, color = c.goldText)
        Text(body, style = Aurum.type.bodySmall, color = c.textSecondary)
    }
}
