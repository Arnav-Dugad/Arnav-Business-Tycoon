package com.arnavgames.tycoon.ui.markets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.ChangeLabel
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold

private enum class Range(val label: String) { Day("1D"), Week("1W") }

private enum class Preset(val label: String, val fraction: Double) { Ten("10%", 0.10), Quarter("25%", 0.25), Half("50%", 0.50), Max("Max", 1.0) }

/** One instrument: chart, facts, your position, and the order ticket. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstrumentScreen(vm: GameViewModel, instrumentId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val s = state ?: return
    val ui = remember(s.market.tick, s.market.holdings, s.market.shorts, s.cash, s.progress.chapter) { vm.instrument(s, instrumentId) } ?: return
    var range by rememberSaveable { mutableStateOf(Range.Day) }
    var side by rememberSaveable { mutableStateOf(OrderSide.Buy) }
    var preset by rememberSaveable { mutableStateOf(Preset.Quarter) }
    val c = Aurum.colors
    val watch by vm.watchlist.collectAsStateWithLifecycle()

    GlassScaffold(title = ui.name, onBack = onBack) {
        ui.art?.let { art -> item(key = "art") { com.arnavgames.tycoon.ui.common.Illustration(art, Modifier.fillMaxWidth().height(120.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large)) } }
        item(key = "hero") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TickerLogo(ui.ticker, ui.assetClass, size = 48.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(ui.ticker, style = Aurum.type.title, color = c.textPrimary)
                        Text(ui.kicker, style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    // Follow it: starred instruments gather under Markets → Watchlist.
                    val watched = instrumentId in watch
                    Box(
                        Modifier.size(Space.touch).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.pill)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = if (watched) "Remove from watchlist" else "Add to watchlist") { vm.toggleWatch(instrumentId) }
                            .testTag("watch-star"),
                        contentAlignment = Alignment.Center,
                    ) { AbtIcon(if (watched) AbtIcons.StarFilled else AbtIcons.Star, if (watched) "On your watchlist" else "Not on your watchlist", tint = if (watched) c.gold else c.textTertiary, size = 22.dp) }
                }
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ui.price, style = Aurum.type.numberHero, color = c.textPrimary, modifier = Modifier.weight(1f))
                    ChangeLabel(ui.change * 100)
                }
                Text("24-hour change", style = Aurum.type.bodySmall, color = c.textTertiary)
                Spacer(Modifier.height(Space.md))
                val pts = if (range == Range.Day) ui.day else ui.week
                val ch = if (pts.size > 1) pts.last() / pts.first() - 1.0 else 0.0
                PriceChart(pts, if (range == Range.Day) "one day" else "one week", ch, Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(Space.sm))
                SegmentedSelector(Range.entries, range, { it.label }, { range = it }, Modifier.fillMaxWidth())
            }
        }
        item(key = "about") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text(ui.about, style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md), maxItemsInEachRow = 2) {
                    ui.stats.forEach { st ->
                        Column(Modifier.weight(1f).widthIn(min = 120.dp).semantics(mergeDescendants = true) {}) {
                            Text(st.label.uppercase(), style = Aurum.type.overline, color = c.textTertiary)
                            Spacer(Modifier.height(Space.xxs))
                            Text(st.value, style = Aurum.type.titleSmall, color = c.textPrimary)
                        }
                    }
                }
            }
        }
        if (ui.holdingValue != null || ui.stake != null) {
            item(key = "position") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = ui.holdingValue != null) {
                    Text("Your position", style = Aurum.type.title, color = c.textPrimary)
                    Spacer(Modifier.height(Space.sm))
                    if (ui.holdingValue != null) {
                        KeyValue("Value", fmt(ui.holdingValue))
                        KeyValue("Holding", "${ui.holdingQty} ${MarketsMapper.unitWord(ui.assetClass)}", divider = true)
                        KeyValue("Paid", fmt(ui.costBasis!!), divider = true)
                        KeyValue("Gain or loss", MarketsMapper.signed(ui.pnl!!, fmt), divider = true, color = if (ui.pnl.isNegative) c.loss else c.gain)
                    } else {
                        Text("You don't own any yet.", style = Aurum.type.body, color = c.textSecondary)
                    }
                    ui.stake?.let { stake ->
                        Spacer(Modifier.height(Space.md))
                        val seat = stake >= ui.boardSeatStake
                        Text(
                            if (seat) "Board seat held: you own ${pctOf(stake)}." else "Own ${pctOf(ui.boardSeatStake)} for a board seat" +
                                (ui.seatCost?.let { " (about ${fmt(it * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(((ui.boardSeatStake - stake) / ui.boardSeatStake).coerceIn(0.0, 1.0)))} more)" } ?: "") +
                                ". You own ${pctOf(stake)}.",
                            style = Aurum.type.bodySmall, color = if (seat) c.gain else c.textSecondary,
                        )
                        Spacer(Modifier.height(Space.xs))
                        GoldProgress((stake / ui.boardSeatStake).toFloat().coerceIn(0f, 1f), label = "Board seat progress")
                        if (stake >= ui.divisionStake) Text("Division stake: opening a business in this sector costs 25% less.", style = Aurum.type.bodySmall, color = c.gain)
                    }
                }
            }
        }
        item(key = "ticket") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Trade", style = Aurum.type.title, color = c.textPrimary)
                Spacer(Modifier.height(Space.sm))
                if (ui.locked != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Lock, null, tint = c.textSecondary, size = 18.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text(ui.locked, style = Aurum.type.body, color = c.textSecondary)
                    }
                    return@AurumCard
                }
                val sides = buildList { add(OrderSide.Buy); if (ui.holdingValue != null) add(OrderSide.Sell); if (ui.assetClass == AssetClass.Stock) add(OrderSide.Short) }
                val current = if (side in sides) side else OrderSide.Buy
                SegmentedSelector(sides, current, { if (it == OrderSide.Short && ui.short != null) "Cover" else it.name }, { side = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.md))
                if (current == OrderSide.Short && !ui.canShort && ui.short == null) {
                    Text(ui.shortLockedReason ?: "", style = Aurum.type.body, color = c.textSecondary)
                    return@AurumCard
                }
                ui.short?.takeIf { current == OrderSide.Short }?.let { sh ->
                    KeyValue("Collateral", fmt(sh.collateral))
                    KeyValue("Cost to cover", fmt(sh.liability), divider = true)
                    KeyValue("Gain or loss", MarketsMapper.signed(sh.pnl, fmt), divider = true, color = if (sh.pnl.isNegative) c.loss else c.gain)
                    Spacer(Modifier.height(Space.xs))
                    GoldProgress(sh.health.toFloat(), label = "Margin health")
                    Text(if (sh.health < 0.3) "Margin is thin: a further rise forces a cover." else "Margin is healthy.", style = Aurum.type.bodySmall, color = if (sh.health < 0.3) c.warning else c.textSecondary)
                    Spacer(Modifier.height(Space.md))
                }
                if (current != OrderSide.Short || ui.short == null) {
                    Text(if (current == OrderSide.Sell) "How much of your position" else "How much of your cash", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.xs))
                    SegmentedSelector(Preset.entries, preset, { it.label }, { preset = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.md))
                }
                val pv = vm.previewOrder(s, instrumentId, current, preset.fraction, fmt)
                if (pv.error != null) {
                    Text(pv.error, style = Aurum.type.body, color = c.warning)
                } else {
                    pv.lines.forEachIndexed { i, (k, v) ->
                        if (i > 0) HairlineDivider(Modifier.padding(vertical = Space.xs))
                        KeyValue(k, v)
                    }
                }
                Spacer(Modifier.height(Space.md))
                GoldButton(
                    text = pv.actionLabel,
                    enabled = pv.ok,
                    onClick = {
                        val cmd = when (current) {
                            OrderSide.Buy -> Command.BuyInstrument(instrumentId, s.cash * Multiplier.ofDouble(preset.fraction))
                            OrderSide.Sell -> Command.SellInstrument(instrumentId, (preset.fraction * 1000).toInt())
                            OrderSide.Short -> if (ui.short != null) Command.CoverShort(instrumentId) else Command.OpenShort(instrumentId, s.cash * Multiplier.ofDouble(preset.fraction * SHORT_NOTIONAL))
                        }
                        vm.dispatch(cmd)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                Text("Orders fill at the current price plus slippage. Fees are 0.1%; realised gains pay 10% tax.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        if (ui.news.isNotEmpty()) {
            item(key = "news-h") { SectionHeader("In the news") }
            item(key = "news") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    ui.news.forEachIndexed { i, n ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = Space.md))
                        NewsRow(n, onClick = null)
                    }
                }
            }
        }
    }
}

/** Short notional as a multiple of the chosen share of cash: the margin (50 %) plus fee then fits in that cash. */
const val SHORT_NOTIONAL = 1.9

private fun pctOf(x: Double) = when {
    x <= 0.0 -> "0%"
    x < 0.00001 -> "less than 0.001%"
    x < 0.001 -> String.format(java.util.Locale.US, "%.3f%%", x * 100)
    else -> String.format(java.util.Locale.US, "%.2f%%", x * 100)
}

@Composable
private fun KeyValue(key: String, value: String, divider: Boolean = false, color: androidx.compose.ui.graphics.Color = Aurum.colors.textPrimary) {
    if (divider) HairlineDivider(Modifier.padding(vertical = Space.xs))
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = Aurum.type.numberSmall, color = color)
    }
}

/** All market news, newest first. */
@Composable
fun NewsScreen(vm: GameViewModel, onBack: () -> Unit, onOpenInstrument: (String) -> Unit) {
    val ui by vm.markets.collectAsStateWithLifecycle()
    val news = ui?.news ?: emptyList()
    GlassScaffold(title = "Market news", onBack = onBack) {
        item(key = "list") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                news.forEachIndexed { i, n ->
                    if (i > 0) HairlineDivider(Modifier.padding(start = Space.md))
                    NewsRow(n, onClick = if (n.targetTicker != null) ({ onOpenInstrument(n.target) }) else null)
                }
            }
        }
        item(key = "note") {
            Text(
                "Stories marked Rumor, Report or Confirmed hint at the next earnings season. Confirmed stories are right about 9 times in 10; rumors only a little more than half the time.",
                style = Aurum.type.bodySmall, color = Aurum.colors.textTertiary, modifier = Modifier.padding(horizontal = Space.xs),
            )
        }
    }
}
