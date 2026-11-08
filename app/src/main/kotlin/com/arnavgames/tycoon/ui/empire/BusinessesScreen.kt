package com.arnavgames.tycoon.ui.empire

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.BuyQty
import com.arnavgames.tycoon.ui.EmpireUi
import com.arnavgames.tycoon.ui.GameViewModel

/**
 * The Businesses tab: every business you own (buy levels, collect tills), what you can open next, the merger tree and
 * headquarters. [focus] scrolls to "open:<id>" (the card that opens a business) or "hq" once, then [onFocusDone].
 */
@Composable
fun BusinessesScreen(vm: GameViewModel, onOpenBusiness: (String) -> Unit, focus: String? = null, onFocusDone: () -> Unit = {}) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val qty by vm.buyQty.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val e = ui ?: return
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val guide = guideText(e, GuideWhere.Businesses)
    val available = e.openable.filter { it.lockedReason == null }
    val locked = e.openable.filter { it.lockedReason != null }.take(2)

    val listState = rememberLazyListState()
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        // Mirrors the list below: guide?, buy row, businesses, open header + cards, locked, mergers, HQ.
        var i = (if (guide != null) 1 else 0) + 1 + e.businesses.size
        val target = when {
            f.startsWith("open:") -> {
                val id = f.removePrefix("open:")
                val a = available.indexOfFirst { it.id == id }
                val l = locked.indexOfFirst { it.id == id }
                when {
                    a >= 0 -> i + 1 + a
                    l >= 0 -> i + (if (available.isNotEmpty()) 1 + available.size else 0) + l
                    else -> i
                }
            }
            f == "hq" -> {
                i += (if (available.isNotEmpty()) 1 + available.size else 0) + locked.size
                if (e.mergers.isNotEmpty()) i += 1 + e.mergers.size + (if (e.holding != null) 1 else 0)
                i
            }
            else -> 0
        }
        listState.animateScrollToItem(target)
        onFocusDone()
    }

    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = with(density) { headerPx.toDp() } + Space.md,
                bottom = BottomBarReserve + navInset + Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("businesses-list"),
        ) {
            guide?.let { g -> item(key = "guide") { GuideBanner(g) } }
            item(key = "buy-row") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Buy amount", style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
                    SegmentedSelector(BuyQty.entries, qty, { it.label }, vm::setBuyQty)
                }
            }
            items(e.businesses, key = { it.id }) { b ->
                BusinessCard(
                    b, fmt,
                    onOpen = { onOpenBusiness(b.id) },
                    onBuy = { vm.dispatch(Command.BuyLevels(b.id, if (qty == BuyQty.Max) -1 else qty.count)) },
                    onCollect = { vm.dispatch(Command.Collect(b.id)) },
                )
            }
            if (available.isNotEmpty()) {
                item(key = "open-header") { SectionHeader("Open a new business") }
                items(available, key = { "open-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = { vm.dispatch(Command.OpenBusiness(o.id)) }) }
            }
            if (locked.isNotEmpty()) {
                items(locked, key = { "locked-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = {}) }
            }
            if (e.mergers.isNotEmpty()) {
                item(key = "merger-header") { SectionHeader("Merger tree") }
                items(e.mergers, key = { "merger-" + it.id }) { m -> MergerCard(m, fmt, onForm = { vm.dispatch(Command.FormConglomerate(m.id)) }) }
                e.holding?.let { h -> item(key = "holding") { HoldingCard(h, fmt, onForm = { name -> vm.dispatch(Command.FormHolding(name)) }) } }
            }
            item(key = "hq") {
                Column {
                    SectionHeader("Headquarters")
                    HqCard(e.hq, fmt, onUpgrade = { vm.dispatch(Command.UpgradeHq) })
                }
            }
        }
        BusinessesHeader(e, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
        MomentOverlay(vm, Modifier.align(Alignment.TopCenter).padding(top = with(density) { headerPx.toDp() }))
    }
}

/** Cash to spend, what the businesses earn, and how many slots are used. */
@Composable
private fun BusinessesHeader(e: EmpireUi, fmt: (Money) -> String, modifier: Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter).padding(top = Space.md, bottom = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BUSINESSES", style = Aurum.type.overline, color = c.textTertiary, modifier = Modifier.weight(1f).semantics { heading() })
                Text("${e.hq.slotsUsed}/${e.hq.slots} slots", style = Aurum.type.label, color = c.textTertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Cash, null, tint = c.goldText, size = 20.dp)
                Spacer(Modifier.width(Space.xs))
                RollingMoney(e.header.cash, e.header.income, style = Aurum.type.numberHero, color = c.textPrimary, format = fmt)
                Spacer(Modifier.width(Space.xs))
                Text("to spend", style = Aurum.type.bodySmall, color = c.textTertiary)
                Spacer(Modifier.weight(1f))
                if (!e.header.income.isZero) {
                    AbtIcon(AbtIcons.ArrowUp, null, tint = c.gain, size = 14.dp)
                    Text(MoneyFormat.perSecond(e.header.income.perSecond()), style = Aurum.type.numberSmall, color = c.gain)
                }
            }
        }
        HairlineDivider()
    }
}
