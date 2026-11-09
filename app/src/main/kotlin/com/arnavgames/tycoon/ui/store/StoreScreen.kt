package com.arnavgames.tycoon.ui.store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.store.SimulatedPurchaseProvider
import com.arnavgames.tycoon.core.content.ProductDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.store.StoreEngine
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.AvatarBadge
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.durationText

/**
 * The demo store (docs/GDD.md §15). Every price is a demo label; confirming opens no payment flow. No random rewards,
 * no countdowns, no "only N left", and nothing here ever interrupts play.
 *
 * Layout: your wallet and what's active, how to earn Bullion free, one featured item, then boosts, upgrades, a
 * cosmetics gallery with previews, passes and (last) the demo Bullion packs. Every item says what it does for you now.
 */
@Composable
fun StoreScreen(vm: GameViewModel, onBack: () -> Unit, onOpenGoals: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val empire by vm.empire.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val content = vm.engine.content.store
    val store = vm.engine.economy.store
    val fmt: (com.arnavgames.tycoon.core.money.Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    var demo by remember { mutableStateOf<ProductDef?>(null) }
    var cosmeticKind by remember { mutableStateOf("avatar") }
    val bullion = s.ledger.bullion.cents.toLong()
    val season = vm.engine.dynasty.season(s.simTimeMs)
    val ctx = StoreCtx(s, vm, store, content.config, season, empire?.header?.income, fmt)

    // Section chips jump down the one list (nothing is hidden behind tabs).
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val keys = remember { mutableListOf<String>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun jump(key: String) { val i = keys.indexOf(key); if (i >= 0) scope.launch { list.animateScrollToItem(i + 1) } }
    Box(Modifier.fillMaxSize()) {
        GlassScaffold(title = "Store", onBack = onBack, list = list) {
            keys.clear()
            fun add(key: String, body: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) { keys += key; item(key = key, content = body) }
            add("hero") {
                com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.luxuryCategory("jewelry"), Modifier.fillMaxWidth().height(140.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.large), scrim = true) {
                    Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                        Text("THE STORE", style = Aurum.type.overline, color = c.gold)
                        Text("Boosts, upgrades and looks", style = Aurum.type.headline, color = androidx.compose.ui.graphics.Color.White)
                        Text("Spend the Bullion you earn by playing.", style = Aurum.type.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            add("wallet") { Wallet(s, bullion, store) }
            add("jump") {
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf("sec-boosts" to "Boosts", "sec-upgrades" to "Upgrades", "sec-cosmetics" to "Looks", "sec-passes" to "Passes", "sec-bullion" to "Bullion")
                        .forEach { (k, label) -> PillAction(label, true, { jump(k) }, style = PillStyle.Glass) }
                }
            }
            ctx.activeLines().takeIf { it.isNotEmpty() }?.let { lines -> add("active") { ActiveNow(lines) } }
            add("earn") { EarnFree(vm, s, onOpenGoals) }
            featured(s, content.products, store, season)?.let { f ->
                add("featured") { ProductCard(f, ctx, bullion, onDemo = { demo = it }, featured = true) }
            }
            for ((secId, title) in listOf("boosts" to "Boosts", "upgrades" to "Upgrades", "passes" to "VIP")) {
                val products = content.products.filter { it.section == secId && it.grants.seasonPass == 0 }
                if (products.isEmpty()) continue
                add("sec-$secId") { SectionHeader(title) }
                products.forEach { p -> add(p.id) { ProductCard(p, ctx, bullion, onDemo = { demo = it }) } }
            }
            // Cosmetics: a gallery with previews, filtered by kind.
            add("sec-cosmetics") {
                Column {
                    SectionHeader("Cosmetics") {
                        val owned = content.cosmetics.count { it.id in s.store.cosmetics }
                        Text("$owned of ${content.cosmetics.size} owned", style = Aurum.type.label, color = c.textTertiary)
                    }
                    Text("Looks only. They don't change the game.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    com.arnavgames.tycoon.designsystem.components.SegmentedSelector(
                        listOf("avatar", "frame", "title"), cosmeticKind, { when (it) { "avatar" -> "Avatars"; "frame" -> "Frames"; else -> "Titles" } },
                        { cosmeticKind = it }, Modifier.fillMaxWidth(),
                    )
                }
            }
            val cosmetics = content.products.filter { p -> p.grants.cosmetic?.let { content.cosmeticById[it]?.kind } == cosmeticKind }
            cosmetics.chunked(2).forEachIndexed { i, row ->
                add("cos-$cosmeticKind-$i") {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        row.forEach { p -> CosmeticTile(p, ctx, bullion, Modifier.weight(1f).fillMaxHeight()) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            val packs = content.products.filter { it.section == "bullion" }
            if (packs.isNotEmpty()) {
                add("sec-bullion") {
                    Column {
                        SectionHeader("Demo Bullion packs")
                        Text("Demo only. No money changes hands.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
                packs.forEach { p -> add(p.id) { ProductCard(p, ctx, bullion, onDemo = { demo = it }) } }
            }
        }
        demo?.let { p ->
            AurumDialog(title = "Demo purchase", onDismiss = { demo = null }) {
                Text(SimulatedPurchaseProvider.DEMO_LABEL, style = Aurum.type.headline, color = c.goldText, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("demo-label"))
                Spacer(Modifier.height(Space.sm))
                Text(p.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                Text(p.description, style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.xs))
                Text("Shown price: ${p.demoPrice} (demo only). Nothing will be charged and you won't be asked for payment details. Your profile will show a Boosted badge.",
                    style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Confirm demo purchase", onClick = { demo = null; vm.demoPurchase(p.id) }, modifier = Modifier.fillMaxWidth().testTag("demo-confirm"))
                GhostButton("Cancel", onClick = { demo = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Everything a card needs to explain an item in terms of the player's own game. */
private class StoreCtx(
    val s: GameState, val vm: GameViewModel, val store: StoreEngine, val cfg: com.arnavgames.tycoon.core.content.StoreConfig,
    val season: Long, val income: com.arnavgames.tycoon.core.money.Rate?, val fmt: (com.arnavgames.tycoon.core.money.Money) -> String,
) {
    val boostLeftMs: Long = s.world.modifiers.firstOrNull { it.source == StoreEngine.BOOST && it.untilSim > s.simTimeMs }?.let { it.untilSim - s.simTimeMs } ?: 0
    private fun pct(x: Double) = "${Math.round(x * 100)}%"

    /** What is running or owned right now. */
    fun activeLines(): List<Pair<ImageVector, String>> = buildList {
        if (boostLeftMs > 0) add(AbtIcons.Bolt to "Income boost +${pct(cfg.boostPct)} · ${durationText(boostLeftMs)} left")
        if (store.vipActive(s)) add(AbtIcons.Crown to "VIP · ${durationText(s.store.vipUntil - s.simTimeMs)} left")
        if (s.store.permanentPct > 0) add(AbtIcons.ArrowUp to "Investor's edge: +${pct(s.store.permanentPct)} income, for good")
        if (s.store.extraSlots > 0) add(AbtIcons.Store to "${s.store.extraSlots} extra business ${if (s.store.extraSlots == 1) "slot" else "slots"}")
        if (s.store.offlineHours > 0) add(AbtIcons.Moon to "Night shift: +${s.store.offlineHours} h offline earnings")
    }

    /** "What this does for you now", in numbers. */
    fun effectNow(p: ProductDef): String? {
        val g = p.grants
        return when {
            g.boostHours > 0 -> buildString {
                income?.takeIf { !it.isZero }?.let { append("About +${MoneyFormat.perSecond((it * Multiplier.ofDouble(cfg.boostPct)).perSecond())} while it runs") }
                if (boostLeftMs > 0) { if (isNotEmpty()) append(" · "); append("adds to the ${durationText(boostLeftMs)} left") }
                if (g.favor > 0) { if (isNotEmpty()) append(" · "); append("you have ${s.ledger.favor.centsOrNull ?: 0} Favor") }
            }.ifEmpty { null }
            g.favor > 0 -> "You have ${s.ledger.favor.centsOrNull ?: 0} Favor. It finishes expansions and renovations instantly."
            g.permanentPct > 0 -> "Now +${pct(s.store.permanentPct)} → +${pct(s.store.permanentPct + g.permanentPct)} income (most is +${pct(cfg.maxPermanentPct)})"
            g.slots > 0 -> "Extra slots: ${s.store.extraSlots} of ${cfg.maxSlots}"
            g.offlineHours > 0 -> {
                val cap = vm.engine.economy.offlineCapMs(s) / 3_600_000.0
                "Away earnings stop after ${trim(cap)} h now → ${trim(cap + g.offlineHours)} h"
            }
            g.vipDays > 0 -> if (store.vipActive(s)) "Adds ${g.vipDays} days to your ${durationText(s.store.vipUntil - s.simTimeMs)} left" else null
            g.seasonPass > 0 -> if (store.seasonPassActive(s, season)) "Already active this season" else "Every season tier also gives its premium reward"
            g.bullion > 0 -> "You have ${s.ledger.bullion.centsOrNull ?: 0} Bullion"
            else -> null
        }
    }

    private fun trim(h: Double) = if (h == Math.floor(h)) h.toInt().toString() else String.format(java.util.Locale.US, "%.1f", h)
}

private fun iconFor(p: ProductDef): ImageVector = with(p.grants) {
    when {
        cosmetic != null -> AbtIcons.Sparkle
        boostHours > 0 -> AbtIcons.Bolt
        favor > 0 && bullion == 0L -> AbtIcons.Favor
        permanentPct > 0 -> AbtIcons.ArrowUp
        slots > 0 -> AbtIcons.Store
        offlineHours > 0 -> AbtIcons.Moon
        vipDays > 0 -> AbtIcons.Crown
        seasonPass > 0 -> AbtIcons.Star
        else -> AbtIcons.Bullion
    }
}

/**
 * One featured item, changing each game week among useful upgrades you don't own yet (never a countdown: it simply
 * changes on the next visit after the week turns).
 */
private fun featured(s: GameState, products: List<ProductDef>, store: StoreEngine, season: Long): ProductDef? {
    val pool = listOf("bundle_weekend", "permanent_5", "offline_2h", "slot_1", "boost_8h", "vip_30")
        .mapNotNull { id -> products.firstOrNull { it.id == id } }
        .filter { store.blocker(s, it) == null && it.grants.seasonPass == 0 }
    if (pool.isEmpty()) return null
    val week = s.simTimeMs / (7 * 86_400_000L)
    return pool[(week % pool.size).toInt()]
}

@Composable
private fun Wallet(s: GameState, bullion: Long, store: StoreEngine) {
    val c = Aurum.colors
    var explain by remember { mutableStateOf(false) }
    if (explain) com.arnavgames.tycoon.ui.common.GlossarySheet(com.arnavgames.tycoon.ui.common.Term.Bullion) { explain = false }
    AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.Bullion, size = 52.dp, highlighted = true)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("YOUR BULLION", style = Aurum.type.overline, color = c.goldText)
                Text("%,d".format(bullion), style = Aurum.type.numberHero, color = c.textPrimary, modifier = Modifier.testTag("store-bullion"))
            }
            Box(Modifier.clickable(onClickLabel = "What is Bullion?") { explain = true }.padding(Space.sm)) {
                AbtIcon(AbtIcons.Info, "What is Bullion?", tint = c.textTertiary, size = 20.dp)
            }
        }
        if (s.store.boosted || store.vipActive(s)) {
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (s.store.boosted) AurumChip("Boosted", icon = AbtIcons.Bolt, tint = c.gain, modifier = Modifier.testTag("boosted-badge"))
                if (store.vipActive(s)) AurumChip("VIP", icon = AbtIcons.Crown, tint = c.goldText)
            }
        }
        Spacer(Modifier.height(Space.sm))
        HairlineDivider()
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Top) {
            AbtIcon(AbtIcons.Shield, null, tint = c.goldText, size = 16.dp)
            Spacer(Modifier.width(Space.xs))
            Text(
                "Demo store. Nothing here ever costs real money.",
                style = Aurum.type.bodySmall, color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun ActiveNow(lines: List<Pair<ImageVector, String>>) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth().testTag("store-active")) {
        Text("ACTIVE NOW", style = Aurum.type.overline, color = c.goldText)
        Spacer(Modifier.height(Space.xs))
        lines.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                AbtIcon(icon, null, tint = c.gain, size = 16.dp)
                Spacer(Modifier.width(Space.sm))
                Text(text, style = Aurum.type.bodySmall, color = c.textPrimary)
            }
        }
    }
}

/** Bullion is earned free in play: show how much is waiting today, and take the player there. */
@Composable
private fun EarnFree(vm: GameViewModel, s: GameState, onOpenGoals: () -> Unit) {
    val c = Aurum.colors
    val m = vm.dynastyMapper
    val ladder = remember(s.meta, s.simTimeMs / 60_000) { m.ladder(s) }
    val daily = remember(s.meta, s.simTimeMs / 10_000) { m.daily(s) }
    val weekly = remember(s.meta, s.simTimeMs / 10_000) { m.weekly(s) }
    val achievements = remember(s.meta) { m.achievements(s) }
    val dailyLeft = daily.filter { !it.claimed }.sumOf { it.bullion }
    val weeklyLeft = weekly.filter { !it.claimed }.sumOf { it.bullion }
    val toClaim = achievements.filter { it.state == 1 }
    val rows = buildList {
        add(Triple(AbtIcons.Gift, "Daily reward", when { !ladder.available -> "Claimed today · back tomorrow"; ladder.bullion > 0 -> "+${"%,d".format(ladder.bullion)} Bullion ready"; else -> "Ready to claim" }))
        add(Triple(AbtIcons.Check, "Today's goals", if (dailyLeft > 0) "${"%,d".format(dailyLeft)} Bullion still to earn" else "All earned today"))
        add(Triple(AbtIcons.Star, "This week's goals", if (weeklyLeft > 0) "${"%,d".format(weeklyLeft)} Bullion still to earn" else "All earned this week"))
        if (toClaim.isNotEmpty()) add(Triple(AbtIcons.Trophy, "Achievements", "${toClaim.size} to claim · ${"%,d".format(toClaim.sumOf { it.bullion })} Bullion"))
    }
    AurumCard(Modifier.fillMaxWidth().testTag("store-earn")) {
        Text("EARN BULLION FREE", style = Aurum.type.overline, color = c.goldText)
        Spacer(Modifier.height(Space.xs))
        rows.forEach { (icon, title, line) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                AbtIcon(icon, null, tint = c.goldText, size = 18.dp)
                Spacer(Modifier.width(Space.sm))
                Text(title, style = Aurum.type.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(line, style = Aurum.type.label, color = c.textSecondary, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(Space.sm))
        PillAction("Go to your goals", true, onOpenGoals, style = PillStyle.Glass, modifier = Modifier.testTag("store-goals"))
    }
}

@Composable
private fun ProductCard(p: ProductDef, ctx: StoreCtx, bullion: Long, onDemo: (ProductDef) -> Unit, featured: Boolean = false) {
    val c = Aurum.colors
    val blocker = ctx.store.blocker(ctx.s, p) ?: if (p.grants.seasonPass > 0 && ctx.store.seasonPassActive(ctx.s, ctx.season)) "Active this season" else null
    val owned = ctx.store.owned(ctx.s, p.id)
    AurumCard(Modifier.fillMaxWidth().testTag(if (featured) "store-featured" else "product-${p.id}"), highlighted = featured) {
        if (featured) {
            Text("FEATURED", style = Aurum.type.overline, color = c.goldText)
            Spacer(Modifier.height(Space.xs))
        }
        Row(verticalAlignment = Alignment.Top) {
            com.arnavgames.tycoon.ui.common.ArtOr(com.arnavgames.tycoon.ui.common.Art.product(p.id), Modifier.size(if (featured) 72.dp else 56.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small)) { IconTile(iconFor(p), size = if (featured) 56.dp else 44.dp, highlighted = featured || owned > 0) }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = if (featured) Aurum.type.title else Aurum.type.titleSmall, color = c.textPrimary)
                Text(p.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                ctx.effectNow(p)?.let { Text(it, style = Aurum.type.label, color = c.goldText, modifier = Modifier.padding(top = 2.dp)) }
                if (owned > 0 && p.limit != 1) Text("Bought ×$owned" + if (p.limit > 1) " of ${p.limit}" else "", style = Aurum.type.label, color = c.textTertiary)
            }
        }
        Spacer(Modifier.height(Space.sm))
        BuyRow(p, blocker, bullion, ctx, onDemo)
    }
}

@Composable
private fun BuyRow(p: ProductDef, blocker: String?, bullion: Long, ctx: StoreCtx, onDemo: (ProductDef) -> Unit) {
    val c = Aurum.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        if (blocker != null) {
            AbtIcon(AbtIcons.Check, null, tint = c.gain, size = 16.dp)
            Text(blocker, style = Aurum.type.label, color = c.textSecondary)
        } else {
            // Bullion earned in play leads (gold when affordable); the demo price is the quiet option.
            val canBullion = p.bullionPrice?.let { bullion >= it } == true
            p.bullionPrice?.let { price ->
                PillAction("Buy · ${"%,d".format(price)} Bullion", canBullion, { ctx.vm.dispatch(Command.BuyWithBullion(p.id)) }, style = if (canBullion) PillStyle.Gold else PillStyle.Glass,
                    modifier = Modifier.testTag("buy-${p.id}"))
            }
            p.demoPrice?.let { price ->
                PillAction("Demo $price", true, { onDemo(p) }, style = if (canBullion || p.bullionPrice != null) PillStyle.Glass else PillStyle.Gold, modifier = Modifier.testTag("demo-${p.id}"))
            }
            if (!canBullion && p.bullionPrice != null) {
                Text("${"%,d".format(p.bullionPrice!! - bullion)} more", style = Aurum.type.label, color = c.textTertiary)
            }
        }
    }
}

/** A cosmetic with a preview of exactly what you'd see: your avatar with the frame, the emblem, or the title. */
@Composable
private fun CosmeticTile(p: ProductDef, ctx: StoreCtx, bullion: Long, modifier: Modifier) {
    val c = Aurum.colors
    val id = p.grants.cosmetic ?: return
    val def = ctx.vm.engine.content.store.cosmeticById[id] ?: return
    val owned = id in ctx.s.store.cosmetics
    val equipped = ctx.s.store.title == id || ctx.s.store.frame == id || ctx.s.store.avatar == id
    AurumCard(modifier.testTag("cosmetic-$id"), highlighted = equipped) {
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp), contentAlignment = Alignment.Center) {
            when (def.kind) {
                "frame" -> AvatarBadge(ctx.s.store.avatar, id, 68.dp)
                "avatar" -> AvatarBadge(id, ctx.s.store.frame, 68.dp)
                else -> Text("“${def.name}”", style = Aurum.type.title, color = c.goldText, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(def.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1)
        Text(when (def.kind) { "frame" -> "Frame"; "avatar" -> "Avatar"; else -> "Title" }, style = Aurum.type.label, color = c.textTertiary)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Space.sm))
        when {
            owned -> PillAction(if (equipped) "Equipped" else "Equip", !equipped, { ctx.vm.dispatch(Command.EquipCosmetic(id, def.kind)) }, style = PillStyle.Glass, modifier = Modifier.fillMaxWidth())
            else -> {
                val price = p.bullionPrice ?: 0
                PillAction("${"%,d".format(price)} Bullion", bullion >= price, { ctx.vm.dispatch(Command.BuyWithBullion(p.id)) }, style = if (bullion >= price) PillStyle.Gold else PillStyle.Glass,
                    modifier = Modifier.fillMaxWidth().testTag("buy-${p.id}"))
            }
        }
    }
}
