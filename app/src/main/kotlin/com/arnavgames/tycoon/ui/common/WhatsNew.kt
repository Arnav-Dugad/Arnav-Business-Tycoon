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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/** One highlight of a release. [icon] names an AbtIcons entry. */
data class Highlight(val icon: String, val title: String, val body: String)

/**
 * "What's new" after an update (docs/PLAN_2.0.md H3): a few highlights, shown once, instead of a snackbar.
 * Add an entry for every release that players will notice.
 */
object WhatsNew {
    private val notes: Map<String, List<Highlight>> = mapOf(
        "1.8.1" to listOf(
            Highlight("Clock", "Auctions you can catch", "Each sale now stays open for four and a half hours, with a new one every six, and the lots on offer are ones your cash can actually bid on."),
            Highlight("Globe", "Clearer property news", "Tenant and repair news names the city, and Mexico City's historic centre has its painting."),
        ),
        "1.8.0" to listOf(
            Highlight("Cash", "Real-world money", "Prices now match the real world, from a Lisbon flat to a $45 billion space company. The richest people are worth hundreds of billions, and so can you be."),
            Highlight("Globe", "22 cities on every continent", "Riyadh, Tokyo, Singapore, Paris, Sydney, São Paulo, Monaco and more: 135 real neighbourhoods, each with real prices and its own painting."),
            Highlight("Chart", "Markets that make sense", "Companies have their real size, so stakes, board seats and the 49% limit work at every level of wealth."),
        ),
        "1.7.0" to listOf(
            Highlight("Chart", "Trade exact amounts", "Type how many dollars or shares you want. Orders above the 49% ownership limit are trimmed to it instead of failing."),
            Highlight("Empire", "Empire is just your businesses", "Your next goal is on Goals; headquarters and mergers have their own Company page."),
            Highlight("Estates", "Cities, not districts", "Every city lists its properties for sale directly, with neighbourhood, rent yield and trend on each."),
        ),
        "1.6.0" to listOf(
            Highlight("Trophy", "A new Goals tab", "Your daily reward, chapter, goals, season and achievements together, with one button to claim everything."),
            Highlight("Menu", "More, rebuilt", "Every place in the game is one tap away: Collection, Store, Desk, Fortune List, Finance and Friends."),
            Highlight("Frame", "430 more paintings", "Every stock, sector, coin and commodity, every store item, Desk decision and tenant moment now has its own picture."),
        ),
        "1.5.0" to listOf(
            Highlight("Chart", "Sort, search and follow", "Search and sort the markets, star stocks to a watchlist, sort your businesses, properties and collection, and collect or claim everything in one tap."),
            Highlight("Menu", "Three save slots", "Keep separate games on this phone. Slot 1 is your main game and the one backed up to the cloud."),
            Highlight("Sparkle", "Cheats, with Undo", "Settings → Cheats: cash, time skips and boosts. A save that uses them becomes a sandbox and stays off the leaderboards."),
        ),
        "1.4.0" to listOf(
            Highlight("Frame", "Hundreds of new paintings", "Every business grows through its own paintings, and every district, tower, event and collection piece has one too."),
            Highlight("Cash", "Know what you get", "Buy buttons show the income they add, and each business page has a Buy bar at the bottom."),
            Highlight("Info", "Calmer, clearer pages", "One tip at a time, shorter labels, and details folded away until you want them."),
        ),
        "1.3.0" to listOf(
            Highlight("Clock", "Earnings while you're away, fixed", "A wrong phone clock could stop offline earnings for days. Now real time always counts."),
            Highlight("Hand", "The Counter has its own tab", "Use as many fingers as you like. Coins fly into your cash, and the gold light follows your touch."),
            Highlight("Frame", "Painted from top to bottom", "Every business, city, property, chapter and collection now has its own painting."),
            Highlight("Empire", "Everything else on Empire", "Your next move, the world and all your businesses now live together on one tab."),
        ),
        "1.2.2" to listOf(
            Highlight("Hand", "Tap anywhere", "The counter now fills the top of the Empire tab: no button to aim for, tap anywhere and watch the cash rise from your finger."),
            Highlight("Cash", "Cash first", "Your cash is the big number at the top; net worth sits just below it."),
        ),
        "1.2.0" to listOf(
            Highlight("Menu", "A new home for everything", "Businesses have their own tab, the counter has its own full-screen page, and Collection and your Dynasty live under More."),
            Highlight("News", "The Daily Ledger", "Come back to a front page of what happened while you were away: wins, problems and what's waiting."),
            Highlight("Info", "Always know what's next", "“Show me” takes you to your next goal, a gold chip marks the best-value purchase, and every cost is explained in plain words."),
            Highlight("Bullion", "A better store", "See what's active, how to earn Bullion free, and exactly what each item does for you. 15 new items to collect."),
        ),
    )

    fun forVersion(versionName: String): List<Highlight> = notes[versionName.substringBefore('-')].orEmpty()

    /**
     * Highlights after updating [from] → [to]: the exact release's notes if it has any; otherwise, when the update
     * crosses into a new major.minor line (1.1.0 → 1.2.1), that line's x.y.0 notes; a patch within a line
     * (1.2.0 → 1.2.1) shows none, so nobody sees the same sheet twice.
     */
    fun forUpdate(from: String, to: String): List<Highlight> {
        val t = to.substringBefore('-')
        fun line(v: String) = v.substringBefore('-').split('.').take(2).joinToString(".")
        val exact = notes[t].orEmpty()
        val lineNotes = if (line(from) != line(t) && t != "${line(t)}.0") notes["${line(t)}.0"].orEmpty() else emptyList()
        // Arriving from an older line: that line's big notes first, then this patch's own.
        return lineNotes + exact
    }
}

@Composable
fun WhatsNewSheet(version: String, highlights: List<Highlight>, onDismiss: () -> Unit) {
    val c = Aurum.colors
    AurumDialog(title = "What's new in ${version.substringBefore('-')}", onDismiss = onDismiss) {
        Text("Your game updated itself. Your empire is exactly as you left it.", style = Aurum.type.bodySmall, color = c.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            highlights.forEach { h ->
                Row(verticalAlignment = Alignment.Top) {
                    IconTile(AbtIcons.named(h.icon), size = 40.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text(h.body, style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
        GoldButton("Let's go", onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("whats-new-close"))
    }
}
