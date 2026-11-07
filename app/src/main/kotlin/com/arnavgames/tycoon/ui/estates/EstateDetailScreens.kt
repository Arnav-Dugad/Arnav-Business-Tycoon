package com.arnavgames.tycoon.ui.estates

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
import com.arnavgames.tycoon.ui.markets.Sparkline
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

/** A city: its districts with their cycle, yield and a 24-hour index sparkline. */
@Composable
fun CityScreen(vm: GameViewModel, cityId: String, onBack: () -> Unit, onOpenDistrict: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val ui = remember(s.simTimeMs / 60_000, s.progress.chapter, s.estate.districtBoost) { vm.city(s, cityId) } ?: return
    val c = Aurum.colors
    GlassScaffold(title = ui.name, onBack = onBack) {
        item(key = "about") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text(ui.description, style = Aurum.type.body, color = c.textSecondary)
                if (!ui.unlocked) {
                    Spacer(Modifier.height(Space.sm))
                    AurumChip("Opens in chapter ${ui.chapter}", icon = AbtIcons.Lock)
                }
            }
        }
        item(key = "d-h") { SectionHeader("Districts") }
        item(key = "districts") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                ui.districts.forEachIndexed { i, d ->
                    if (i > 0) HairlineDivider(Modifier.padding(start = Space.md))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenDistrict(d.id) }.heightIn(min = Space.touch + 12.dp)
                            .padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("${d.tier} · rent ${pct(d.yieldPerDay)} of price a day" + if (d.cbd) " · business district" else "", style = Aurum.type.bodySmall, color = c.textTertiary)
                            Text(d.cycle + cycleHint(d.cycle), style = Aurum.type.bodySmall, color = cycleColor(d.cycle))
                        }
                        Sparkline(d.spark, up = d.change24h >= 0, modifier = Modifier.size(width = 52.dp, height = 26.dp))
                        Spacer(Modifier.width(Space.sm))
                        ChangeLabel(d.change24h * 100)
                    }
                }
            }
        }
    }
}

/** What a district's market phase means for the player, in a few words. */
private fun cycleHint(cycle: String) = when (cycle) {
    "Peak" -> " · prices high: a good time to sell"
    "Bottom" -> " · prices low: a good time to buy"
    "Rising" -> " · prices climbing"
    "Falling" -> " · prices dropping"
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
                        IconTile(AbtIcons.named(l.icon), size = 44.dp, highlighted = l.distressed)
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
        item(key = "hero") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(AbtIcons.named(ui.icon), size = 48.dp, highlighted = true)
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

