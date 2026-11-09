package com.arnavgames.tycoon.ui.estates

import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumToggle
import com.arnavgames.tycoon.designsystem.components.ChangeLabel
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.markets.PriceChart
import java.util.Locale

private fun pct(x: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f%%", x * 100)

@Composable
private fun KV(key: String, value: String, divider: Boolean = false, color: androidx.compose.ui.graphics.Color = Aurum.colors.textPrimary) {
    if (divider) HairlineDivider(Modifier.padding(vertical = Space.xs))
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = Aurum.type.numberSmall, color = color)
    }
}

/**
 * A city: everything for sale there and everything you own there, in one list (owner request 2026-11-09: no
 * districts to drill through). Each listing names its neighbourhood and that area's price trend; sort by price,
 * rent yield or newest, and filter by kind of property.
 */
@Composable
fun CityScreen(vm: GameViewModel, cityId: String, onBack: () -> Unit, onOpenProperty: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.simTimeMs / 60_000, s.progress.chapter, s.estate.districtBoost) { vm.city(s, cityId) } ?: return
    val areas = remember(s.simTimeMs / 60_000, s.estate.properties, s.estate.boughtListings, s.cash, s.progress.chapter) {
        ui.districts.mapNotNull { vm.district(s, it.id) }
    }
    var sort by com.arnavgames.tycoon.ui.common.rememberPref("city.sort", "yield")
    var kind by rememberSaveable { mutableStateOf("all") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var ltv by rememberSaveable { mutableStateOf(0.0) }
    var insured by rememberSaveable { mutableStateOf(false) }
    val c = Aurum.colors
    val owned = areas.flatMap { it.owned }
    val listings = areas.flatMap { a -> a.listings.map { a to it } }
    val kinds = listings.map { it.second.typeId to it.second.type }.distinct().sortedBy { it.second }
    val shown = listings.filter { kind == "all" || it.second.typeId == kind }.let { l ->
        when (sort) {
            "cheap" -> l.sortedBy { it.second.price }
            "pricey" -> l.sortedByDescending { it.second.price }
            "deal" -> l.sortedBy { it.second.discount }
            else -> l.sortedByDescending { it.second.rentPerDay.ratioTo(it.second.price) }
        }
    }
    GlassScaffold(title = ui.name, onBack = onBack) {
        item(key = "hero") { com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.city(ui.id), Modifier.fillMaxWidth().height(180.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large)) }
        item(key = "about") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text(ui.description, style = Aurum.type.body, color = c.textSecondary)
                if (!ui.unlocked) {
                    Spacer(Modifier.height(Space.sm))
                    AurumChip("Opens in chapter ${ui.chapter}", icon = AbtIcons.Lock)
                } else {
                    Spacer(Modifier.height(Space.sm))
                    // The city's market at a glance: the average trend across its neighbourhoods.
                    val avg = if (ui.districts.isEmpty()) 0.0 else ui.districts.sumOf { it.change24h } / ui.districts.size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Prices today", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        ChangeLabel(avg * 100)
                    }
                }
            }
        }
        if (owned.isNotEmpty()) {
            item(key = "own-h") { SectionHeader("Yours in ${ui.name} (${owned.size})") }
            item(key = "own") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    owned.forEachIndexed { i, p ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        PropertyRow(p, fmt) { onOpenProperty(p.id) }
                    }
                }
            }
        }
        if (ui.unlocked) {
            item(key = "list-h") {
                Column {
                    SectionHeader("For sale (${shown.size})") {
                        com.arnavgames.tycoon.ui.common.SortToggle(
                            listOf(
                                com.arnavgames.tycoon.ui.common.Choice("yield", "Best rent yield"), com.arnavgames.tycoon.ui.common.Choice("cheap", "Cheapest"),
                                com.arnavgames.tycoon.ui.common.Choice("pricey", "Most expensive"), com.arnavgames.tycoon.ui.common.Choice("deal", "Biggest discount"),
                            ),
                            sort, { sort = it }, Modifier.testTag("city-sort"),
                        )
                    }
                    if (kinds.size > 1) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            PillAction("All", true, { kind = "all" }, style = if (kind == "all") PillStyle.Gold else PillStyle.Glass)
                            kinds.forEach { (id, name) -> PillAction(name, true, { kind = if (kind == id) "all" else id }, style = if (kind == id) PillStyle.Gold else PillStyle.Glass) }
                        }
                    }
                }
            }
            val refresh = areas.minOfOrNull { it.refreshInMinutes } ?: 0
            if (shown.isEmpty()) item(key = "none") { AurumCard(Modifier.fillMaxWidth()) { Text("Nothing for sale right now. New listings arrive in ${EstatesMapper.mins(refresh * 60_000)}.", style = Aurum.type.body, color = c.textSecondary) } }
            shown.forEach { (area, l) ->
                item(key = l.id) {
                    ListingCard(vm, area, l, fmt, selected == l.id, { selected = if (selected == l.id) null else l.id }, ltv, { ltv = it }, insured, { insured = it }) { selected = null }
                }
            }
        }
    }
}

/** One property for sale: what it is, where, its price and rent; tap to choose financing and buy. */
@Composable
private fun ListingCard(
    vm: GameViewModel, area: DistrictUi, l: ListingUi, fmt: (Money) -> String, open: Boolean, onToggle: () -> Unit,
    ltv: Double, onLtv: (Double) -> Unit, insured: Boolean, onInsured: (Boolean) -> Unit, onBought: () -> Unit,
) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth().testTag("listing-${l.id}"), highlighted = open, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.arnavgames.tycoon.ui.common.Illustration(
                com.arnavgames.tycoon.ui.common.Art.property(l.typeId), Modifier.size(72.dp, 52.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small),
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(l.type, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1)
                Text(area.name + if (area.cbd) " · business district" else "", style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("Rent ${fmt(l.rentPerDay)} a day · ${pct(l.rentPerDay.ratioTo(l.price), 1)}", style = Aurum.type.bodySmall, color = c.gain, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(l.price), style = Aurum.type.numberSmall, color = c.textPrimary)
                if (l.distressed) AurumChip("−${((1 - l.discount) * 100).toInt()}%", tint = c.warning)
                else ChangeLabel(area.change24h * 100)
            }
        }
        if (open) {
            Spacer(Modifier.height(Space.md))
            KV("Condition", "${l.condition / 10}%" + if (l.distressed) " · renovate to flip it" else "")
            KV("Area market", area.cycle + cycleHint(area.cycle), divider = true, color = cycleColor(area.cycle))
            Spacer(Modifier.height(Space.md))
            if (area.lockedReason != null) {
                Text(area.lockedReason, style = Aurum.type.body, color = c.textSecondary)
                return@AurumCard
            }
            if (area.mortgagesUnlocked) {
                Text("Mortgage", style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.xs))
                SegmentedSelector(area.ltvOptions, ltv, { if (it == 0.0) "Cash" else "${(it * 100).toInt()}%" }, onLtv, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Umbrella, null, tint = c.goldText, size = 16.dp)
                Spacer(Modifier.width(Space.xs))
                Text("Insure against disasters", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                AurumToggle(insured, onInsured)
            }
            Spacer(Modifier.height(Space.sm))
            val chosen = if (area.mortgagesUnlocked) ltv else 0.0
            val need = l.cashNeeded[chosen] ?: l.cashNeeded.getValue(0.0)
            KV("Price", fmt(l.price))
            KV("You pay now (incl. 3% costs)", fmt(need), divider = true)
            Spacer(Modifier.height(Space.md))
            GoldButton(
                text = if (area.cash >= need) "Buy · ${fmt(need)}" else "Need ${fmt(need - area.cash)} more",
                enabled = area.cash >= need,
                onClick = { vm.dispatch(Command.BuyProperty(l.id, chosen, insured)) { onBought() } },
                modifier = Modifier.fillMaxWidth().testTag("buy-listing"),
            )
        }
    }
}

/** What a district's market phase means for the player, in a few words. */
private fun cycleHint(cycle: String) = when (cycle) {
    "Peak" -> " · good time to sell"
    "Bottom" -> " · good time to buy"
    "Rising" -> " · climbing"
    "Falling" -> " · dropping"
    else -> ""
}

@Composable
private fun cycleColor(cycle: String) = when (cycle) {
    "Rising" -> Aurum.colors.gain
    "Falling" -> Aurum.colors.loss
    "Peak" -> Aurum.colors.warning
    else -> Aurum.colors.goldText
}

/** A district: its price index, today's listings (with a buy ticket) and what you own there. */
@Composable
fun DistrictScreen(vm: GameViewModel, districtId: String, onBack: () -> Unit, onOpenProperty: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.simTimeMs / 60_000, s.estate.properties, s.estate.boughtListings, s.cash, s.progress.chapter) { vm.district(s, districtId) } ?: return
    var weekly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var ltv by rememberSaveable { mutableStateOf(0.0) }
    var insured by rememberSaveable { mutableStateOf(false) }
    val c = Aurum.colors
    GlassScaffold(title = ui.name, onBack = onBack) {
        item(key = "art") { com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.district(ui.id), Modifier.fillMaxWidth().height(170.dp).clip(AurumShapes.large)) }
        item(key = "index") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${ui.cityName} · ${ui.tier}" + if (ui.cbd) " · Central business district" else "", style = Aurum.type.bodySmall, color = c.textSecondary)
                        Text("Price index · ${ui.cycle}", style = Aurum.type.title, color = c.textPrimary)
                    }
                    ChangeLabel(ui.change24h * 100)
                }
                Spacer(Modifier.height(Space.md))
                val pts = if (weekly) ui.week else ui.day
                PriceChart(pts, if (weekly) "one week" else "one day", (pts.last() / pts.first() - 1.0), Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(Space.sm))
                SegmentedSelector(listOf(false, true), weekly, { if (it) "1W" else "1D" }, { weekly = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
                Text("Rent earns about ${pct(ui.yieldPerDay)} of the price a day, before property tax. Prices rise and fall on the district's cycle: buy low, sell high.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        if (ui.lockedReason != null) {
            item(key = "locked") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Lock, null, tint = c.textSecondary, size = 18.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text(ui.lockedReason, style = Aurum.type.body, color = c.textSecondary)
                    }
                }
            }
        }
        if (ui.owned.isNotEmpty()) {
            item(key = "own-h") { SectionHeader("You own here") }
            item(key = "own") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    ui.owned.forEachIndexed { i, p ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        PropertyRow(p, fmt) { onOpenProperty(p.id) }
                    }
                }
            }
        }
        item(key = "list-h") { SectionHeader("Listings") { Text("New in ${EstatesMapper.mins(ui.refreshInMinutes * 60_000)}", style = Aurum.type.label, color = c.textTertiary) } }
        if (ui.listings.isEmpty()) item(key = "none") { AurumCard(Modifier.fillMaxWidth()) { Text("Everything here has sold. New listings arrive soon.", style = Aurum.type.body, color = c.textSecondary) } }
        ui.listings.forEach { l ->
            item(key = l.id) {
                AurumCard(Modifier.fillMaxWidth(), highlighted = selected == l.id, onClick = { selected = if (selected == l.id) null else l.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.property(l.typeId), Modifier.size(64.dp, 46.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small))
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(l.type, style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("Size ×${String.format(Locale.US, "%.2f", l.size)}", style = Aurum.type.bodySmall, color = c.textTertiary)
                            Text("Rent about ${fmt(l.rentPerDay)} a day", style = Aurum.type.bodySmall, color = c.gain)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(l.price), style = Aurum.type.numberSmall, color = c.textPrimary)
                            if (l.distressed) AurumChip("Distressed −${((1 - l.discount) * 100).toInt()}%", tint = c.warning)
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    GoldProgress(l.condition / 1000f, label = "Condition ${l.condition / 10}%")
                    Text("Condition ${l.condition / 10}%" + if (l.distressed) " · renovate to flip it" else "", style = Aurum.type.bodySmall, color = c.textTertiary)
                    if (selected == l.id) {
                        Spacer(Modifier.height(Space.md))
                        HairlineDivider()
                        Spacer(Modifier.height(Space.md))
                        if (ui.mortgagesUnlocked) {
                            Text("Mortgage", style = Aurum.type.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(Space.xs))
                            SegmentedSelector(ui.ltvOptions, ltv, { if (it == 0.0) "Cash" else "${(it * 100).toInt()}% loan" }, { ltv = it }, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(Space.sm))
                        } else {
                            Text("Mortgages open in chapter 3.", style = Aurum.type.bodySmall, color = c.textTertiary)
                            Spacer(Modifier.height(Space.sm))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AbtIcon(AbtIcons.Umbrella, null, tint = c.goldText, size = 16.dp)
                            Spacer(Modifier.width(Space.xs))
                            Text("Insure against disasters (0.05% a day)", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                            AurumToggle(insured, { insured = it })
                        }
                        Spacer(Modifier.height(Space.sm))
                        val chosen = if (ui.mortgagesUnlocked) ltv else 0.0
                        KV("Price", fmt(l.price))
                        KV("You pay now (incl. 3% costs)", fmt(l.cashNeeded.getValue(chosen)), divider = true)
                        Spacer(Modifier.height(Space.md))
                        GoldButton(
                            text = "Buy · ${fmt(l.cashNeeded.getValue(chosen))}",
                            enabled = ui.lockedReason == null && ui.cash >= l.cashNeeded.getValue(chosen),
                            onClick = { vm.dispatch(Command.BuyProperty(l.id, chosen, insured)) { selected = null } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** One property: value, rent, works, sale, insurance, mortgage, hosting a business, skyscrapers and decisions. */
@Composable
fun PropertyScreen(vm: GameViewModel, propertyId: Long, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.simTimeMs / 30_000, s.estate, s.timers, s.cash) { vm.property(s, propertyId) }
    val c = Aurum.colors
    if (ui == null) {
        GlassScaffold(title = "Sold", onBack = onBack) { item(key = "gone") { AurumCard(Modifier.fillMaxWidth()) { Text("This property has been sold.", style = Aurum.type.body, color = c.textSecondary) } } }
        return
    }
    GlassScaffold(title = ui.title, onBack = onBack) {
        item(key = "art") { com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.property(ui.typeId), Modifier.fillMaxWidth().height(170.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large)) }
        item(key = "hero") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(ui.location, style = Aurum.type.bodySmall, color = c.textSecondary)
                        Text(fmt(ui.value), style = Aurum.type.numberHero, color = c.textPrimary)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                KV("Paid", fmt(ui.costBasis))
                KV("Gain or loss", (if (ui.gain.isNegative) "−" else "+") + fmt(ui.gain.abs()), divider = true, color = if (ui.gain.isNegative) c.loss else c.gain)
                if (ui.mortgage.isPositive) KV("Mortgage", fmt(ui.mortgage), divider = true, color = c.warning)
                KV("Condition", "${ui.condition / 10}%", divider = true, color = if (ui.condition < 700) c.warning else c.textPrimary)
                KV("Renovations", "${ui.renovations}/${ui.maxRenovations}", divider = true)
                KV("Status", ui.tenantStatus, divider = true)
            }
        }
        ui.decisions.forEach { d -> item(key = "d-${d.id}") { DecisionCard(d, onChoose = { i -> vm.dispatch(Command.ResolveTenantEvent(d.id, i)) }) } }
        if (ui.busyLabel != null && ui.busyEndsAt != null) {
            item(key = "busy") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Text(ui.busyLabel, style = Aurum.type.title, color = c.textPrimary)
                    Text("Done in ${EstatesMapper.mins(ui.busyEndsAt - ui.simNow)}", style = Aurum.type.bodySmall, color = c.textSecondary)
                    if (ui.selling) {
                        Spacer(Modifier.height(Space.sm))
                        Text("Sells for about ${fmt(ui.saleQuoteNet)} after costs, tax and the mortgage (at the price when it completes).", style = Aurum.type.bodySmall, color = c.textTertiary)
                        Spacer(Modifier.height(Space.sm))
                        PillAction("Cancel sale", enabled = true, onClick = { vm.dispatch(Command.CancelPropertySale(ui.id)) }, style = PillStyle.Glass)
                    }
                }
            }
        }
        if (ui.hostedBusiness == null) {
            item(key = "rent") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Asking rent", style = Aurum.type.title, color = c.textPrimary)
                    Text("Higher rent takes longer to let and attracts more problems. Changing it means finding a new tenant.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    SegmentedSelector(ui.rentLevels, ui.rentPct, { "$it%" }, { vm.dispatch(Command.SetRent(ui.id, it)) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.sm))
                    KV("Expected at this level", "${fmt(ui.rentLevelPreview.getValue(ui.rentPct))}/day")
                    KV("Next payout", fmt(ui.expectedRent6h), divider = true, color = if (ui.expectedRent6h.isPositive) c.gain else c.textTertiary)
                }
            }
        }
        item(key = "works") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Works", style = Aurum.type.title, color = c.textPrimary)
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Renovate", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("Raises value and rent; ${ui.renovationHours} h with no tenant.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    PillAction(ui.renovationCost?.let { "Renovate · ${fmt(it)}" } ?: if (ui.renovations >= ui.maxRenovations) "Fully renovated" else "Busy",
                        enabled = ui.renovationCost != null && ui.cash >= ui.renovationCost, onClick = { vm.dispatch(Command.RenovateProperty(ui.id)) })
                }
                if (ui.repairCost != null) {
                    HairlineDivider(Modifier.padding(vertical = Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repair", style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("Restores condition to 100%.", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        PillAction("Repair · ${fmt(ui.repairCost)}", enabled = ui.cash >= ui.repairCost, onClick = { vm.dispatch(Command.RenovateProperty(ui.id, repair = true)) }, style = PillStyle.Glass)
                    }
                }
            }
        }
        if (ui.hostOptions.isNotEmpty() || ui.hostedBusiness != null) {
            item(key = "host") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Run a business from here", style = Aurum.type.title, color = c.textPrimary)
                    Text("A business on your own property pays no rent: its upkeep drops by 10%. The property earns no rent while it hosts.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    if (ui.hostedBusiness != null) {
                        Text("${ui.hostedBusiness} operates here.", style = Aurum.type.body, color = c.gain)
                        Spacer(Modifier.height(Space.xs))
                        PillAction("Rent it out instead", enabled = true, onClick = { vm.dispatch(Command.HostBusiness(ui.id, null)) }, style = PillStyle.Glass)
                    } else {
                        ui.hostOptions.forEach { (id, name) ->
                            PillAction("Move $name here", enabled = ui.busyLabel == null, onClick = { vm.dispatch(Command.HostBusiness(ui.id, id)) }, modifier = Modifier.fillMaxWidth().padding(vertical = Space.xxs), style = PillStyle.Glass)
                        }
                    }
                }
            }
        }
        if (ui.tower != null || ui.towerDesigns.isNotEmpty() || ui.towerLocked != null) {
            item(key = "tower") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Tower, null, tint = c.goldText, size = 20.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text("Skyscraper", style = Aurum.type.title, color = c.textPrimary)
                    }
                    Text("Build a landmark in four stages. Floors lease one an hour once it opens, and the whole district's prices rise.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    when {
                        ui.towerLocked != null -> Text(ui.towerLocked, style = Aurum.type.body, color = c.textTertiary)
                        ui.tower != null -> {
                            val t = ui.tower
                            Text("${t.name} · ${t.floors} floors", style = Aurum.type.titleSmall, color = c.textPrimary)
                            GoldProgress(t.stagesDone / 4f, label = "Stages done ${t.stagesDone} of 4")
                            Text("${t.stagesDone} of 4 stages done", style = Aurum.type.bodySmall, color = c.textSecondary)
                            if (t.nextStage != null && t.nextCost != null) {
                                Spacer(Modifier.height(Space.sm))
                                PillAction("Build ${t.nextStage} · ${fmt(t.nextCost)}", enabled = ui.busyLabel == null && ui.cash >= t.nextCost, onClick = { vm.dispatch(Command.BuildTower(ui.id, t.id)) })
                            } else Text("Open for business.", style = Aurum.type.body, color = c.gain)
                        }
                        else -> ui.towerDesigns.forEach { d ->
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                                com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.tower(d.id), Modifier.size(56.dp, 72.dp).clip(AurumShapes.small))
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                                    Text("${d.floors} floors · ${fmt(d.totalCost)} in total", style = Aurum.type.bodySmall, color = c.textTertiary)
                                }
                                PillAction("Start · ${fmt(d.nextCost!!)}", enabled = ui.busyLabel == null && ui.cash >= d.nextCost, onClick = { vm.dispatch(Command.BuildTower(ui.id, d.id)) })
                            }
                        }
                    }
                }
            }
        }
        item(key = "money") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbtIcon(AbtIcons.Umbrella, null, tint = c.goldText, size = 18.dp)
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text("Insurance", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("${fmt(ui.insurancePerDay)} a day. Disasters are repaired for free.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    AurumToggle(ui.insured, { vm.dispatch(Command.SetInsured(ui.id, it)) })
                }
                if (ui.mortgage.isPositive) {
                    HairlineDivider(Modifier.padding(vertical = Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mortgage", style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("Interest every 6 h. If the loan reaches 95% of the value, the bank sells.", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        PillAction("Repay · ${fmt(ui.mortgage)}", enabled = ui.cash >= ui.mortgage, onClick = { vm.dispatch(Command.RepayMortgage(ui.id)) }, style = PillStyle.Glass)
                    }
                }
                HairlineDivider(Modifier.padding(vertical = Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sell", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("Takes ${ui.saleHours} h to find a buyer. About ${fmt(ui.saleQuoteNet)} to you today.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    PillAction("List for sale", enabled = ui.busyLabel == null, onClick = { vm.dispatch(Command.SellProperty(ui.id)) }, style = PillStyle.Glass)
                }
            }
        }
    }
}

