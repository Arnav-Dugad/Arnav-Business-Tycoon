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
        "1.2.0" to listOf(
            Highlight("Menu", "A new home for everything", "Businesses have their own tab, the counter has its own full-screen page, and Collection and your Dynasty live under More."),
            Highlight("News", "The Daily Ledger", "Come back to a front page of what happened while you were away: wins, problems and what's waiting."),
            Highlight("Info", "Always know what's next", "“Show me” takes you to your next goal, a gold chip marks the best-value purchase, and every cost is explained in plain words."),
            Highlight("Bullion", "A better store", "See what's active, how to earn Bullion free, and exactly what each item does for you. 15 new items to collect."),
        ),
    )

    fun forVersion(versionName: String): List<Highlight> = notes[versionName.substringBefore('-')].orEmpty()
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
