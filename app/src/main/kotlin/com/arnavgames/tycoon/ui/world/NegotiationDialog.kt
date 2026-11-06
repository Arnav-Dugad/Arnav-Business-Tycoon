package com.arnavgames.tycoon.ui.world

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import java.util.Locale

private val MOVES = listOf("Hold firm" to "Push for your number.", "Meet halfway" to "Offer a fair split.", "Make it personal" to "Appeal to the relationship.")

/** How each personality reacts to Firm / Fair / Personal (+2 likes, 0 neutral, −2 dislikes). */
private val TASTES = mapOf(
    "Shark" to listOf(2, 0, -2), "Old Money" to listOf(0, 2, 0), "Disruptor" to listOf(2, -2, 0), "Collector" to listOf(-2, 0, 2),
    "Philanthropist" to listOf(-2, 2, 2), "Gambler" to listOf(2, -2, 2), "Recluse" to listOf(0, 2, -2), "Heir" to listOf(-2, 0, 2),
)
private val TELLS = mapOf(2 to "leans in, interested.", 0 to "nods, giving nothing away.", -2 to "taps the table, irritated.")

/**
 * The Negotiation mini-game (GDD §13.1): three rounds against the other side's personality, with a mood meter and
 * tells. The result shifts the odds of the option's best outcome by up to ±15 points; skipping keeps the shown odds.
 */
@Composable
fun NegotiationDialog(counterpart: String, archetype: String?, optionLabel: String, onDone: (edge: Double) -> Unit, onCancel: () -> Unit) {
    val c = Aurum.colors
    val taste = TASTES[archetype] ?: listOf(-2, 2, 0)
    var round by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<String?>(null) }
    val edge = (points / 6.0 * 0.15).coerceIn(-0.15, 0.15)
    AurumDialog(title = "Negotiate: $optionLabel", onDismiss = onCancel) {
        Text("Across the table: $counterpart" + (archetype?.let { " ($it)" } ?: ""), style = Aurum.type.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(Space.xs))
        Text("Mood", style = Aurum.type.label, color = c.textTertiary)
        GoldProgress(((points + 6) / 12f).coerceIn(0f, 1f), height = 6.dp, label = "Mood")
        last?.let { Spacer(Modifier.height(Space.xs)); Text("$counterpart $it", style = Aurum.type.bodySmall, color = c.textPrimary) }
        Spacer(Modifier.height(Space.sm))
        if (round < 3) {
            Text("Round ${round + 1} of 3", style = Aurum.type.titleSmall, color = c.textPrimary)
            MOVES.forEachIndexed { i, (label, hint) ->
                GhostButton("$label · $hint", onClick = { points += taste[i]; last = TELLS[taste[i]]; round++ }, modifier = Modifier.fillMaxWidth())
            }
            GhostButton("Skip (keep the shown odds)", onClick = { onDone(0.0) }, modifier = Modifier.fillMaxWidth())
        } else {
            Text(String.format(Locale.US, "Your edge: %+.0f points on the best outcome", edge * 100), style = Aurum.type.titleSmall, color = if (edge >= 0) c.gain else c.loss)
            Spacer(Modifier.height(Space.sm))
            GoldButton("Settle the deal", onClick = { onDone(edge) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
