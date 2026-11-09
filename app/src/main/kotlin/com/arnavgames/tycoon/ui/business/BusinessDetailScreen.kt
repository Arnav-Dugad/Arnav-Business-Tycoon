package com.arnavgames.tycoon.ui.business

import androidx.compose.ui.draw.alpha
import com.arnavgames.tycoon.ui.common.shortPersonName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.content.BusinessDef
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Mechanics
import com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.state.BusinessState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.countdownText
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.glass

@Composable
fun BusinessDetailScreen(vm: GameViewModel, businessId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: return
    val b = s.businesses[businessId] ?: return
    val def = vm.engine.content.business(businessId)
    val eco = vm.engine.economy
    val notation = vm.notation(settings)
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val rate = eco.businessRate(s, businessId)
    val c = Aurum.colors

    GlassScaffold(def.name, onBack) {
            item(key = "art") { com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.business(def.id), Modifier.fillMaxWidth().height(190.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large)) }
            item {
                AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val tier = def.tiers.lastOrNull { b.level >= it.level }
                            Text(tier?.name ?: def.starterName, style = Aurum.type.headline, color = c.textPrimary)
                            Text("Level ${b.level} · ${def.tagline}", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Space.lg))
                    Row {
                        Stat("Net income", MoneyFormat.perSecond(rate.net.perSecond(), notation), Modifier.weight(1f))
                        Stat("Efficiency", "${(rate.efficiency * 100).toInt()}%", Modifier.weight(1f))
                        Stat("Upkeep", MoneyFormat.perSecond(rate.upkeep.perSecond(), notation), Modifier.weight(1f))
                    }
                }
            }
            item { SectionHeader(mechanicTitle(vm, s, def, b)) }
            item { MechanicPanel(vm, s, def, b, fmt) }
            item { SectionHeader("Managers") }
            def.managers.forEach { m ->
                item(key = "m-" + m.id) {
                    val hired = b.managerId == m.id
                    AurumCard(Modifier.fillMaxWidth(), highlighted = hired) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val portrait = vm.portraitPath(m.id)
                            if (portrait != null) {
                                coil3.compose.AsyncImage(
                                    model = "file:///android_asset/$portrait", contentDescription = "Portrait of ${m.name}",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small)
                                        .then(if (hired) Modifier.border(1.5.dp, Aurum.colors.gold, com.arnavgames.tycoon.designsystem.theme.AurumShapes.small) else Modifier),
                                )
                            } else IconTile(AbtIcons.Manager, highlighted = hired)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                                Text("${m.title} · ${m.policy.name} style", style = Aurum.type.bodySmall, color = c.textSecondary)
                                Spacer(Modifier.height(Space.xxs))
                                StarRow(m.stars.toDouble(), size = 14)
                                Text(m.trait, style = Aurum.type.bodySmall, color = c.textTertiary)
                            }
                            if (hired) {
                                Spacer(Modifier.width(Space.sm))
                                AurumChip("Hired", icon = AbtIcons.Check, tint = c.gain)
                            }
                        }
                        if (!hired) {
                            Spacer(Modifier.height(Space.md))
                            SmallButton(
                                if (b.managerId != null) "Replace with ${shortPersonName(m.name)} · ${fmt(m.cost)}" else "Hire · ${fmt(m.cost)}",
                                enabled = s.cash >= m.cost, modifier = Modifier.fillMaxWidth(),
                            ) { vm.dispatch(Command.HireManager(def.id, m.id)) }
                        }
                    }
                }
            }
            item { SectionHeader("Expansions") }
            def.expansions.forEach { e ->
                item(key = "e-" + e.id) {
                    val done = e.id in b.expansionsDone
                    val timer = s.timers.firstOrNull { it.targetId == def.id && it.detailId == e.id }
                    AurumCard(Modifier.fillMaxWidth(), highlighted = done) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (done) AbtIcons.Check else AbtIcons.Expand, highlighted = done)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                                Text("${e.description} Income ${e.multiplier.display()}.", style = Aurum.type.bodySmall, color = c.textSecondary)
                                if (!done && timer == null) Text("Needs level ${e.requiresLevel} · takes ${countdownText(e.durationSeconds * 1000)}", style = Aurum.type.label, color = c.textTertiary)
                            }
                        }
                        when {
                            done -> Unit
                            timer != null -> {
                                Spacer(Modifier.height(Space.md))
                                val total = (timer.endSim - timer.startSim).toFloat()
                                GoldProgress(((s.simTimeMs - timer.startSim) / total).coerceIn(0f, 1f), label = "Expansion progress")
                                Spacer(Modifier.height(Space.sm))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AbtIcon(AbtIcons.Timer, null, tint = c.info, size = 16.dp)
                                    Spacer(Modifier.width(Space.xs))
                                    Text("${countdownText(timer.endSim - s.simTimeMs)} left", style = Aurum.type.numberSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                                    val favor = vm.engine.rushCostFavor(s, timer)
                                    SmallButton("Rush · $favor Favor", enabled = (s.ledger.favor.centsOrNull ?: 0) >= favor) { vm.dispatch(Command.RushTimer(timer.id)) }
                                }
                            }
                            else -> {
                                Spacer(Modifier.height(Space.md))
                                val blocked = s.timers.any { it.targetId == def.id }
                                SmallButton(
                                    if (blocked) "Another expansion is under way" else "Build · ${fmt(e.cost)}",
                                    enabled = !blocked && b.level >= e.requiresLevel && s.cash >= e.cost,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { vm.dispatch(Command.StartExpansion(def.id, e.id)) }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Scale tiers") }
            item {
                AurumCard(Modifier.fillMaxWidth()) {
                    def.tiers.forEach { t ->
                        val reached = b.level >= t.level
                        Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            AbtIcon(if (reached) AbtIcons.Check else AbtIcons.Lock, null, tint = if (reached) c.gain else c.textTertiary, size = 16.dp)
                            Spacer(Modifier.width(Space.sm))
                            Text("Lv ${t.level}", style = Aurum.type.numberSmall, color = c.textTertiary, modifier = Modifier.width(52.dp))
                            Text(t.name, style = Aurum.type.titleSmall, color = if (reached) c.textPrimary else c.textSecondary, modifier = Modifier.weight(1f))
                            Text(t.multiplier.display(), style = Aurum.type.numberSmall, color = c.goldText)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(Space.sm))
                GhostButton(
                    if (b.mothballed) "Reopen this business" else "Mothball (pause and free a slot)",
                    onClick = { vm.dispatch(Command.SetMothballed(def.id, !b.mothballed)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (b.mothballed) AbtIcons.Play else AbtIcons.Pause,
                )
            }
    }
}

private fun mechanicTitle(vm: GameViewModel, s: GameState, def: BusinessDef, b: BusinessState) = when (val m = def.mechanic) {
    is MechanicDef.Retail -> "Pricing & promotions"
    is MechanicDef.Restaurant -> "Reputation & kitchen"
    is MechanicDef.Logistics -> "Fleet & routes"
    is MechanicDef.Module -> moduleView(vm, s, def, b, m).title
    MechanicDef.Simple -> "Operations"
}

private fun moduleView(vm: GameViewModel, s: GameState, def: BusinessDef, b: BusinessState, m: MechanicDef.Module) =
    MechanicRegistry.of(m.kind).view(m, vm.engine.economy.moduleState(s, def, b, m), vm.engine.economy.mechCtx(s, def, b))

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = Aurum.type.overline, color = Aurum.colors.textTertiary)
        Spacer(Modifier.height(Space.xxs))
        Text(value, style = Aurum.type.number, color = Aurum.colors.textPrimary)
    }
}

@Composable
private fun StarRow(stars: Double, size: Int = 18) {
    val c = Aurum.colors
    Row(Modifier.semantics { contentDescription = "%.1f stars".format(stars) }) {
        for (i in 1..5) {
            val fill = (stars - (i - 1)).coerceIn(0.0, 1.0)
            if (fill >= 0.5) AbtIcon(AbtIcons.StarFilled, null, tint = c.gold, size = size.dp)
            else AbtIcon(AbtIcons.Star, null, tint = c.outlineStrong, size = size.dp)
        }
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PillAction(text, enabled, onClick, modifier)

// ------------------------------------------------------------------ mechanic panels

@Composable
private fun MechanicPanel(vm: GameViewModel, s: GameState, def: BusinessDef, b: BusinessState, fmt: (Money) -> String) {
    AurumCard(Modifier.fillMaxWidth()) {
        if (b.managerId != null) {
            ManagerNote(def.managers.first { it.id == b.managerId }.name)
            Spacer(Modifier.height(Space.md))
        }
        when (val m = def.mechanic) {
            is MechanicDef.Retail -> RetailPanel(vm, s, def, m, b.mechanic as? MechanicState.Retail ?: MechanicState.Retail(), b.managerId != null, fmt)
            is MechanicDef.Restaurant -> RestaurantPanel(vm, def, m, b.mechanic as? MechanicState.Restaurant ?: Mechanics.restaurantInitial(m), b.managerId != null)
            is MechanicDef.Logistics -> LogisticsPanel(vm, def, m, b, b.mechanic as? MechanicState.Logistics ?: MechanicState.Logistics(), b.managerId != null)
            is MechanicDef.Module -> ModulePanel(
                view = moduleView(vm, s, def, b, m),
                cash = s.cash,
                managed = b.managerId != null,
                freeActions = GameEngine.MANAGER_FREE_ACTIONS,
                fmt = fmt,
                onAction = { action, value -> vm.dispatch(Command.MechanicAction(def.id, action, value)) },
            )
            MechanicDef.Simple -> Text("This business runs on autopilot.", style = Aurum.type.body, color = Aurum.colors.textSecondary)
        }
    }
}

@Composable
private fun ManagerNote(name: String) {
    val c = Aurum.colors
    Row(Modifier.fillMaxWidth().clip(AurumShapes.small).background(c.gold.copy(alpha = if (c.isDark) 0.12f else 0.08f)).padding(Space.sm + Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        AbtIcon(AbtIcons.Manager, null, tint = c.goldText, size = 18.dp)
        Spacer(Modifier.width(Space.sm))
        Text("$name is making these decisions. You can still watch how they play it.", style = Aurum.type.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun ColumnScope.RetailPanel(vm: GameViewModel, s: GameState, def: BusinessDef, m: MechanicDef.Retail, st: MechanicState.Retail, managed: Boolean, fmt: (Money) -> String) {
    val c = Aurum.colors
    var price by remember(st.pricePct) { mutableFloatStateOf(st.pricePct.toFloat()) }
    val preview = Mechanics.retailEfficiency(m, st.copy(pricePct = price.toInt()), s.simTimeMs)
    Text("Price level", style = Aurum.type.titleSmall, color = c.textPrimary)
    Text("Higher prices earn more per sale but drive shoppers away. Find the sweet spot.", style = Aurum.type.bodySmall, color = c.textSecondary)
    Spacer(Modifier.height(Space.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${price.toInt()}%", style = Aurum.type.number, color = c.textPrimary, modifier = Modifier.width(64.dp))
        Slider(
            value = price,
            onValueChange = { price = (Math.round(it / 5f) * 5).toFloat() },
            onValueChangeFinished = { vm.dispatch(Command.SetRetailPrice(def.id, price.toInt())) },
            valueRange = m.minPricePct.toFloat()..m.maxPricePct.toFloat(),
            enabled = !managed,
            colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.surfaceHigh, disabledThumbColor = c.textTertiary, disabledActiveTrackColor = c.outlineStrong),
            modifier = Modifier.weight(1f).semantics { stateDescription = "${price.toInt()} percent of reference price" },
        )
    }
    // Say how close the player is to the best price (without giving the answer away).
    val best = Mechanics.retailEfficiency(m, st.copy(pricePct = Mechanics.retailOptimalPricePct(m)), s.simTimeMs)
    val atBest = preview >= best * 0.985
    Text(
        "Profit at this price: ${(preview * 100).toInt()}% of standard" + if (atBest) " · you've found the sweet spot" else " · the best possible is ${(best * 100).toInt()}%",
        style = Aurum.type.bodySmall, color = if (atBest) c.gain else if (preview >= 1.0) c.textSecondary else c.loss,
    )
    Spacer(Modifier.height(Space.lg))
    Text("Promotion", style = Aurum.type.titleSmall, color = c.textPrimary)
    Text("Footfall ×${m.promoFootfall} for ${m.promoSeconds / 60} minutes.", style = Aurum.type.bodySmall, color = c.textSecondary)
    Spacer(Modifier.height(Space.sm))
    if (st.promoUntil > s.simTimeMs) {
        AurumChip("Promotion running · ${countdownText(st.promoUntil - s.simTimeMs)}", icon = AbtIcons.Megaphone, tint = c.gain)
    } else {
        val cost = vm.engine.promoCost(s, def.id)
        SmallButton("Run promotion · ${fmt(cost)}", enabled = s.cash >= cost, modifier = Modifier.fillMaxWidth()) { vm.dispatch(Command.StartRetailPromo(def.id)) }
    }
}

@Composable
private fun ColumnScope.RestaurantPanel(vm: GameViewModel, def: BusinessDef, m: MechanicDef.Restaurant, st: MechanicState.Restaurant, managed: Boolean) {
    val c = Aurum.colors
    val stars = st.milliStars / 1000.0
    val target = m.qualityTiers.first { it.id == st.qualityTier }.targetStars
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Reputation", style = Aurum.type.titleSmall, color = c.textPrimary)
            Text(
                when {
                    stars < target - 0.01 -> "Rising toward %.1f stars".format(target)
                    stars > target + 0.01 -> "Slipping toward %.1f stars".format(target)
                    else -> "Steady"
                },
                style = Aurum.type.bodySmall, color = c.textSecondary,
            )
        }
        Text("%.2f".format(stars), style = Aurum.type.numberHero.copy(fontSize = Aurum.type.display.fontSize), color = c.goldText)
    }
    StarRow(stars, size = 22)
    Spacer(Modifier.height(Space.md))
    Text("Kitchen standard", style = Aurum.type.titleSmall, color = c.textPrimary)
    Text("Better food builds reputation slowly and costs more to run.", style = Aurum.type.bodySmall, color = c.textSecondary)
    Spacer(Modifier.height(Space.sm))
    m.qualityTiers.forEach { t ->
        val sel = t.id == st.qualityTier
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.xxs)
                .then(if (sel) Modifier.clip(AurumShapes.small).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.10f)).border(1.25.dp, c.gold, AurumShapes.small) else Modifier.glass(AurumShapes.small, GlassWeight.Thin, blur = false))
                .clickable(enabled = !managed && !sel, role = Role.RadioButton) { vm.dispatch(Command.SetRestaurantQuality(def.id, t.id)) }
                .heightIn(min = Space.touch).padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbtIcon(if (sel) AbtIcons.Check else AbtIcons.Chef, null, tint = if (sel) c.goldText else c.textTertiary, size = 18.dp)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                Text("Aims for %.1f stars · upkeep ×%.2f".format(t.targetStars, t.upkeepMultiplier), style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun ColumnScope.LogisticsPanel(vm: GameViewModel, def: BusinessDef, m: MechanicDef.Logistics, b: BusinessState, st: MechanicState.Logistics, managed: Boolean) {
    val c = Aurum.colors
    val total = Mechanics.vehicles(m, b.level)
    val assigned = Mechanics.assignedTotal(st)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Fleet: $total vehicles", style = Aurum.type.titleSmall, color = c.textPrimary)
            Text("${total - assigned} in the depot · each carries ${m.vehicleCapacity.toInt()} loads", style = Aurum.type.bodySmall, color = c.textSecondary)
        }
        if (!managed) SmallButton("Auto-plan", enabled = true) { vm.dispatch(Command.AutoPlanRoutes(def.id)) }
    }
    Spacer(Modifier.height(Space.sm))
    Text("Put vehicles on the routes with the best margins, without sending more than a route can fill.", style = Aurum.type.bodySmall, color = c.textTertiary)
    Spacer(Modifier.height(Space.md))
    m.routes.forEach { r ->
        val unlocked = b.level >= r.unlockLevel
        val n = st.assignments[r.id] ?: 0
        val demand = Mechanics.routeDemand(r, b.level)
        val used = (n * m.vehicleCapacity).coerceAtMost(demand)
        Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(if (unlocked) AbtIcons.Route else AbtIcons.Lock, null, tint = if (unlocked) c.goldText else c.textTertiary, size = 18.dp)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = Aurum.type.titleSmall, color = if (unlocked) c.textPrimary else c.textTertiary)
                    Text(
                        if (unlocked) "Margin ×%.1f · demand %.1f".format(r.margin, demand) else "Unlocks at level ${r.unlockLevel}",
                        style = Aurum.type.bodySmall, color = c.textSecondary,
                    )
                }
                if (unlocked) {
                    Stepper(
                        value = n,
                        canMinus = !managed && n > 0,
                        canPlus = !managed && assigned < total,
                        onMinus = { vm.dispatch(Command.AssignVehicles(def.id, r.id, n - 1)) },
                        onPlus = { vm.dispatch(Command.AssignVehicles(def.id, r.id, n + 1)) },
                        label = r.name,
                    )
                }
            }
            if (unlocked) {
                Spacer(Modifier.height(Space.xs))
                GoldProgress((used / demand).toFloat(), height = 4.dp, label = "${r.name} filled ${((used / demand) * 100).toInt()} percent")
                if (n * m.vehicleCapacity > demand + m.vehicleCapacity - 0.01) {
                    Text("More trucks than cargo on this route", style = Aurum.type.label, color = c.warning)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, label: String) {
    val c = Aurum.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false)) {
        Box(
            Modifier.size(Space.touch).clickable(enabled = canMinus, role = Role.Button, onClickLabel = "Remove a vehicle from $label", onClick = onMinus),
            contentAlignment = Alignment.Center,
        ) { AbtIcon(AbtIcons.Minus, "Remove a vehicle from $label", tint = if (canMinus) c.textPrimary else c.textTertiary, size = 18.dp) }
        Text("$value", style = Aurum.type.number, color = c.textPrimary, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(
            Modifier.size(Space.touch).clickable(enabled = canPlus, role = Role.Button, onClickLabel = "Add a vehicle to $label", onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) { AbtIcon(AbtIcons.Plus, "Add a vehicle to $label", tint = if (canPlus) c.textPrimary else c.textTertiary, size = 18.dp) }
    }
}

