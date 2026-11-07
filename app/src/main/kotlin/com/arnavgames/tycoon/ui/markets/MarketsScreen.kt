package com.arnavgames.tycoon.ui.markets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.ChangeLabel
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.locked.ComingSoonScreen

/** The Markets tab (docs/GDD.md §6): portfolio header, news, positions and the four instrument desks. */
@Composable
fun MarketsScreen(vm: GameViewModel, onOpenInstrument: (String) -> Unit, onOpenNews: () -> Unit, onOpenTiming: () -> Unit = {}) {
    val ui by vm.markets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val mk = ui ?: return
    if (!mk.unlocked) {
        ComingSoonScreen(
            icon = AbtIcons.Markets, title = "Markets", chapter = mk.unlockChapter,
            lines = listOf("120 fictional companies across 12 sectors", "News that moves prices, dividends and earnings seasons", "Funds, bonds, commodities and, later, crypto"),
        )
        return
    }
    var tab by rememberSaveable { mutableStateOf(MarketTab.Stocks) }
    var sector by rememberSaveable { mutableStateOf("all") }
    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val c = Aurum.colors

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = with(density) { headerPx.toDp() } + Space.md,
                bottom = BottomBarReserve + navInset + Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content),
        ) {
            if (mk.news.isNotEmpty()) {
                item(key = "news") { NewsCard(mk.news.take(3), onOpenNews, onOpenInstrument) }
            }
            item(key = "timing") {
                com.arnavgames.tycoon.designsystem.components.MenuRow(
                    "Market Timing", AbtIcons.Chart, onOpenTiming,
                    subtitle = "30-second practice on a real price tape · up to 10 Favor a day",
                )
            }
            if (mk.positions.isNotEmpty()) {
                item(key = "pos-header") { SectionHeader("Your positions") { Text("${mk.positions.size}", style = Aurum.type.label, color = c.textTertiary) } }
                item(key = "positions") {
                    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                        mk.positions.forEachIndexed { i, p ->
                            if (i > 0) HairlineDivider(Modifier.padding(start = 72.dp))
                            PositionRow(p, fmt, onClick = { onOpenInstrument(p.id) })
                        }
                    }
                }
            }
            item(key = "tabs") {
                Column {
                    SectionHeader("Exchange")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        MarketTab.entries.forEach { t ->
                            val lockCh = mk.tabsUnlocked[t]
                            SectorChip(if (lockCh != null) "${t.label} · Ch $lockCh" else t.label, tab == t, locked = lockCh != null) { tab = t }
                        }
                    }
                }
            }
            val lockedAt = mk.tabsUnlocked[tab]
            if (lockedAt != null) {
                item(key = "tab-locked") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AbtIcon(AbtIcons.Lock, null, tint = c.textSecondary, size = 20.dp)
                            Spacer(Modifier.width(Space.md))
                            Text("${tab.label} open in chapter $lockedAt. Keep growing your empire.", style = Aurum.type.body, color = c.textSecondary)
                        }
                    }
                }
            } else {
                if (tab == MarketTab.Stocks) {
                    item(key = "sectors") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            SectorChip("All", sector == "all") { sector = "all" }
                            mk.sectors.forEach { (id, name) -> SectorChip(name, sector == id) { sector = id } }
                        }
                    }
                }
                val rows = mk.rows[tab].orEmpty().filter { tab != MarketTab.Stocks || sector == "all" || it.group == sector }
                val groups = rows.groupBy { it.group }
                groups.forEach { (group, list) ->
                    if (tab != MarketTab.Stocks || sector == "all") item(key = "g-$group") { SectionHeader(mk.sectors.firstOrNull { it.first == group }?.second ?: groupTitle(group)) }
                    item(key = "list-$group") {
                        AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                            list.forEachIndexed { i, r ->
                                if (i > 0) HairlineDivider(Modifier.padding(start = 72.dp))
                                InstrumentRow(r, onClick = { onOpenInstrument(r.id) })
                            }
                        }
                    }
                }
            }
        }
        MarketsHeader(mk, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
    }
}

private fun groupTitle(g: String) = when (g) {
    "funds" -> "Index funds"
    "bonds" -> "Bonds"
    "tier1" -> "Established coins"
    "tier2" -> "Large coins"
    "tier3" -> "Small coins"
    "tier4" -> "Speculative tokens"
    "commodities" -> "Commodities"
    else -> g
}

@Composable
private fun MarketsHeader(mk: MarketsUi, fmt: (Money) -> String, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter).padding(top = Space.md, bottom = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PORTFOLIO", style = Aurum.type.overline, color = c.textTertiary, modifier = Modifier.weight(1f))
                var explain by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (explain) com.arnavgames.tycoon.ui.common.GlossarySheet(com.arnavgames.tycoon.ui.common.Term.Economy) { explain = false }
                AurumChip("Economy: ${mk.phase} · rates ${String.format(java.util.Locale.US, "%.2f", mk.ratePerDay * 100)}%/day", icon = AbtIcons.Globe,
                    modifier = Modifier.clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.pill).clickable(onClickLabel = "What does the economy do?") { explain = true })
            }
            Text(fmt(mk.portfolioValue), style = Aurum.type.numberHero, color = c.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Cash, null, tint = c.goldText, size = 16.dp)
                Spacer(Modifier.width(Space.xs))
                Text(fmt(mk.cash), style = Aurum.type.number, color = c.textPrimary)
                Spacer(Modifier.width(Space.xs))
                Text("to invest", style = Aurum.type.bodySmall, color = c.textTertiary)
                Spacer(Modifier.weight(1f))
                if (!mk.portfolioValue.isZero) ChangeLabel(mk.dayChange * 100)
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun SectorChip(label: String, selected: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier
            .then(if (selected) Modifier.glass(AurumShapes.pill, GlassWeight.Thick, blur = false) else Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false))
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (locked) {
            AbtIcon(AbtIcons.Lock, null, tint = c.textTertiary, size = 12.dp)
            Spacer(Modifier.width(Space.xxs + 2.dp))
        }
        Text(label, style = if (selected) Aurum.type.titleSmall else Aurum.type.label, color = if (selected) c.goldText else if (locked) c.textTertiary else c.textSecondary, maxLines = 1)
    }
}

@Composable
fun InstrumentRow(r: InstrumentRowUi, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = Space.touch + 16.dp)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .semantics(mergeDescendants = true) { contentDescription = "${r.name}, ${r.price}, ${MarketsMapper.pct(r.change)} today" + if (r.held) ", in your portfolio" else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickerLogo(r.ticker, r.assetClass)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.held) {
                    Spacer(Modifier.width(Space.xs))
                    AbtIcon(AbtIcons.Briefcase, null, tint = c.goldText, size = 12.dp)
                }
            }
            Text(r.subtitle, style = Aurum.type.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.sm))
        Sparkline(r.spark, up = r.change >= 0, modifier = Modifier.size(width = 52.dp, height = 26.dp))
        Spacer(Modifier.width(Space.sm))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 76.dp)) {
            Text(r.price, style = Aurum.type.numberSmall, color = c.textPrimary, maxLines = 1)
            ChangeLabel(r.change * 100)
        }
    }
}

@Composable
private fun PositionRow(p: PositionUi, fmt: (Money) -> String, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = Space.touch + 12.dp).padding(horizontal = Space.md, vertical = Space.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickerLogo(p.ticker, p.assetClass)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (p.short) "${p.ticker} · Short" else p.ticker, style = Aurum.type.bodySmall, color = c.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt(p.value), style = Aurum.type.numberSmall, color = c.textPrimary)
            Text(MarketsMapper.signed(p.pnl, fmt) + " (" + MarketsMapper.pct(p.pnlPct) + ")", style = Aurum.type.bodySmall, color = if (p.pnl.isNegative) c.loss else c.gain)
        }
    }
}

@Composable
private fun NewsCard(news: List<NewsUi>, onOpenNews: () -> Unit, onOpenInstrument: (String) -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.News, null, tint = c.goldText, size = 18.dp)
            Spacer(Modifier.width(Space.sm))
            Text("Market news", style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        news.forEach { n ->
            HairlineDivider(Modifier.padding(start = Space.md))
            NewsRow(n, onClick = if (n.targetTicker != null) ({ onOpenInstrument(n.target) }) else null)
        }
        HairlineDivider(Modifier.padding(start = Space.md))
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenNews).heightIn(min = Space.touch).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All news", style = Aurum.type.titleSmall, color = c.goldText, modifier = Modifier.weight(1f))
            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 16.dp)
        }
    }
}

@Composable
fun NewsRow(n: NewsUi, onClick: (() -> Unit)?) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(n.headline, style = Aurum.type.body, color = c.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(MarketsMapper.ago(n.minutesAgo) + (n.targetTicker?.let { " · $it" } ?: ""), style = Aurum.type.bodySmall, color = c.textTertiary)
                if (n.reliability.isNotEmpty()) {
                    Spacer(Modifier.width(Space.xs))
                    AurumChip(n.reliability, tint = when (n.reliability) { "Confirmed" -> c.gain; "Report" -> c.goldText; else -> c.textSecondary })
                }
            }
        }
        if (n.effect != 0.0) {
            Spacer(Modifier.width(Space.sm))
            ChangeLabel(n.effect * 100)
        }
    }
}
