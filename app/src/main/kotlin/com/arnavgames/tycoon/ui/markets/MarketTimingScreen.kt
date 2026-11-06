package com.arnavgames.tycoon.ui.markets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.state.PriceHistory
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Market Timing (GDD §13.3): a 30-second replay of a real in-game price tape. Tap to hold or sell a practice position;
 * the score turns into a small daily Favor bonus. It never touches your real money.
 */
@Composable
fun MarketTimingScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    // Today's tape: a seeded stock's week of prices (coarse + fine history).
    val tape = remember(s.simTimeMs / 86_400_000L) {
        val stocks = vm.engine.content.markets.stocks.filter { (s.market.quotes[it.id]?.fine?.size ?: 0) > 10 }
        val st = stocks.getOrNull(GameRandom.stream(s.worldSeed, "timing", s.simTimeMs / 86_400_000L).nextInt(maxOf(1, stocks.size)))
        val q = st?.let { s.market.quotes[it.id] }
        st?.name to (q?.let { (it.coarse + it.fine).map { v -> PriceHistory.price(v) } } ?: emptyList())
    }
    val (name, prices) = tape
    var step by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var cash by remember { mutableDoubleStateOf(1.0) }
    var units by remember { mutableDoubleStateOf(0.0) }
    var finished by remember { mutableStateOf(false) }
    val playedToday = s.meta.timingDay == vm.engine.dynasty.day(s.simTimeMs)

    LaunchedEffect(running) {
        if (!running || prices.size < 2) return@LaunchedEffect
        val perStep = 30_000L / (prices.size - 1)
        while (step < prices.size - 1) { delay(perStep); step++ }
        running = false
        finished = true
    }
    val price = prices.getOrNull(step) ?: 1.0
    val value = cash + units * price
    val score = ((value - 1.0) * 1000).toInt().coerceIn(0, 100)

    GlassScaffold(title = "Market Timing", onBack = onBack) {
        item(key = "intro") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("PRACTICE", style = Aurum.type.overline, color = c.goldText)
                Text("Replay a week of ${name ?: "a stock"} in 30 seconds. Buy before it rises, sell before it falls. Your real money is never used; the score pays up to 10 Favor once a day.",
                    style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        item(key = "chart") {
            AurumCard(Modifier.fillMaxWidth()) {
                val gold = c.gold
                val line = c.textTertiary
                Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Price tape" }) {
                    if (prices.size < 2) return@Canvas
                    val lo = prices.min(); val hi = prices.max().let { if (it - lo < 1e-9) lo + 1 else it }
                    fun pt(i: Int) = Offset(size.width * i / (prices.size - 1), size.height * (1 - ((prices[i] - lo) / (hi - lo)).toFloat()))
                    val shown = Path(); val ghost = Path()
                    prices.indices.forEach { i -> val p = pt(i); if (i == 0) { shown.moveTo(p.x, p.y); ghost.moveTo(p.x, p.y) } else { if (i <= step) shown.lineTo(p.x, p.y); ghost.lineTo(p.x, p.y) } }
                    if (finished) drawPath(ghost, line, style = Stroke(width = 1.5.dp.toPx()))
                    drawPath(shown, gold, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
                Spacer(Modifier.height(Space.sm))
                Text("Practice value ${String.format(Locale.US, "%+.1f%%", (value - 1) * 100)} · score $score", style = Aurum.type.titleSmall, color = if (value >= 1) c.gain else c.loss, modifier = Modifier.testTag("timing-score"))
            }
        }
        item(key = "controls") {
            when {
                prices.size < 2 -> Text("No price history yet. Come back once the markets have been open a while.", style = Aurum.type.bodySmall, color = c.textSecondary)
                !running && !finished -> GoldButton("Start", onClick = { step = 0; cash = 1.0; units = 0.0; holding = false; running = true }, modifier = Modifier.fillMaxWidth())
                running -> GoldButton(if (holding) "Sell" else "Buy", onClick = {
                    if (holding) { cash = units * price; units = 0.0 } else { units = cash / price; cash = 0.0 }
                    holding = !holding
                }, modifier = Modifier.fillMaxWidth().testTag("timing-trade"))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GoldButton(if (playedToday) "Bonus already collected today" else "Collect ${(1 + score / 11).coerceIn(1, 10)} Favor", enabled = !playedToday,
                        onClick = { vm.dispatch(Command.FinishMarketTiming(score)) { vm.say("Nice trading. Favor collected.") } }, modifier = Modifier.weight(1f))
                    GhostButton("Again", onClick = { finished = false; step = 0; cash = 1.0; units = 0.0; holding = false; running = true })
                }
            }
        }
    }
}
