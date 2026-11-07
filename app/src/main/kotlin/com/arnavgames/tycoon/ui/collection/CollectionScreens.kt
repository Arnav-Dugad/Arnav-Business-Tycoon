package com.arnavgames.tycoon.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
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
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.estates.EstatesMapper
import com.arnavgames.tycoon.ui.locked.ComingSoonScreen
import java.util.Locale

/** A bundled photo (museum works) or, until the photo pipeline covers a category, an illustrated card. */
@Composable
fun ItemImage(image: String?, icon: String, name: String, modifier: Modifier, iconSize: Dp = 28.dp) {
    val c = Aurum.colors
    if (image != null) {
        AsyncImage(model = "file:///android_asset/$image", contentDescription = name, contentScale = ContentScale.Crop, modifier = modifier.background(c.separator))
    } else {
        val seed = name.hashCode()
        val hue = ((seed and 0x7FFFFFFF) % 360).toFloat()
        Box(
            modifier.background(Brush.linearGradient(listOf(Color.hsl(hue, 0.30f, if (c.isDark) 0.28f else 0.70f), Color.hsl((hue + 40) % 360, 0.35f, if (c.isDark) 0.18f else 0.55f))))
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) { AbtIcon(AbtIcons.named(icon), null, tint = Color.White.copy(alpha = 0.85f), size = iconSize) }
    }
}

private fun pts(x: Double) = String.format(Locale.US, "%,.0f", x)

/** The Collection tab (docs/GDD.md §8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(vm: GameViewModel, onOpenCategory: (String) -> Unit, onOpenAuction: () -> Unit) {
    val ui by vm.collection.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val col = ui ?: return
    if (!col.unlocked) {
        ComingSoonScreen(icon = AbtIcons.Collection, title = "Collection", chapter = col.unlockChapter,
            lines = listOf("Cars, jets, yachts, islands and masterpieces", "Live auctions against rival billionaires", "Showrooms and set bonuses"))
        return
    }
    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val c = Aurum.colors
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = with(density) { headerPx.toDp() } + Space.md, bottom = BottomBarReserve + navInset + Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content),
        ) {
            item(key = "intro") { com.arnavgames.tycoon.ui.common.FirstVisitTip(com.arnavgames.tycoon.ui.common.Intro.Collection, AbtIcons.Collection) }
            item(key = "auction") {
                val a = col.auction
                AurumCard(Modifier.fillMaxWidth(), highlighted = a != null, onClick = if (a != null) onOpenAuction else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Timer, null, tint = c.goldText, size = 18.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text(if (a != null) "Live auction" else "Auction house", style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.weight(1f))
                        if (a != null) AurumChip("Closes in ${secs(a.closesInMs)}", tint = c.warning)
                    }
                    Spacer(Modifier.height(Space.sm))
                    when {
                        a != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemImage(a.image, a.icon, a.name, Modifier.size(72.dp).clip(AurumShapes.small))
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 2)
                                Text(a.maker, style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 1)
                                Text("${fmt(a.bid)} · ${a.leader}", style = Aurum.type.numberSmall, color = if (a.playerLeads) c.gain else c.goldText)
                            }
                            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 16.dp)
                        }
                        !col.auctionsUnlocked -> Text("Live auctions open in chapter ${col.auctionsChapter}.", style = Aurum.type.body, color = c.textSecondary)
                        else -> Text("Next auction in ${EstatesMapper.mins(col.nextAuctionInMs ?: 0)}. Rare pieces only, against rival billionaires.", style = Aurum.type.body, color = c.textSecondary)
                    }
                }
            }
            item(key = "rooms-h") { SectionHeader("Showrooms") }
            item(key = "rooms") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm), maxItemsInEachRow = 2) {
                    col.showrooms.forEach { r ->
                        Column(Modifier.weight(1f).glass(AurumShapes.medium, GlassWeight.Thin, blur = false).padding(Space.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AbtIcon(AbtIcons.named(r.icon), null, tint = c.goldText, size = 16.dp)
                                Spacer(Modifier.width(Space.xs))
                                Text(r.name, style = Aurum.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                                Text("${r.displayed}/${r.capacity}", style = Aurum.type.label, color = c.textTertiary,
                                    modifier = Modifier.semantics { contentDescription = "${r.displayed} of ${r.capacity} display spaces used" })
                            }
                            Spacer(Modifier.height(Space.sm))
                            // One tile per display slot (up to four): pieces on show, then free slots.
                            val slots = r.capacity.coerceIn(1, 4)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (0 until 4).forEach { i ->
                                    val p = r.previews.getOrNull(i)
                                    when {
                                        p != null -> ItemImage(p.first, p.second, "", Modifier.weight(1f).aspectRatio(1f).clip(AurumShapes.tiny), iconSize = 14.dp)
                                        i < slots -> Box(Modifier.weight(1f).aspectRatio(1f).clip(AurumShapes.tiny).background(c.separator))
                                        else -> Spacer(Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                            }
                            val stored = r.owned - r.displayed
                            if (stored > 0) {
                                Spacer(Modifier.height(Space.xs))
                                Text("$stored stored, earning no Status", style = Aurum.type.label, color = c.goldText)
                            }
                            Spacer(Modifier.height(Space.sm))
                            PillAction("Expand · ${MoneyFormat.compact(r.upgradeCost)}", enabled = col.cash >= r.upgradeCost, onClick = { vm.dispatch(Command.UpgradeShowroom(r.id)) }, style = PillStyle.Glass, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item(key = "cats-h") { SectionHeader("Shop") }
            item(key = "cats") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    col.categories.forEachIndexed { i, cat ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenCategory(cat.id) }.heightIn(min = Space.touch + 8.dp)
                                .padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(AbtIcons.named(cat.icon), size = 36.dp, highlighted = cat.owned > 0, tint = if (cat.unlocked) c.goldText else c.textTertiary)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, style = Aurum.type.titleSmall, color = if (cat.unlocked) c.textPrimary else c.textTertiary)
                                Text(if (cat.unlocked) "${cat.owned} of ${cat.total} owned" else "Opens in chapter ${cat.chapter}", style = Aurum.type.bodySmall, color = c.textTertiary)
                            }
                            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 16.dp)
                        }
                    }
                }
            }
            item(key = "sets-h") { SectionHeader("Collection sets") { Text("${col.sets.count { it.complete }}/${col.sets.size}", style = Aurum.type.label, color = c.textTertiary) } }
            item(key = "sets") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    col.sets.take(12).forEachIndexed { i, st ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = Space.md))
                        Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {}) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(st.name, style = Aurum.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                                if (st.complete) AurumChip("Complete", icon = AbtIcons.Check, tint = c.gain) else Text("${st.have}/${st.total}", style = Aurum.type.label, color = c.textTertiary)
                            }
                            Text(st.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(Space.xxs))
                            GoldProgress(st.have.toFloat() / st.total, label = "${st.name} progress")
                        }
                    }
                }
            }
        }
        CollectionHeader(col, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
    }
}

private fun secs(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s" }

@Composable
private fun CollectionHeader(col: CollectionUi, fmt: (Money) -> String, modifier: Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter).padding(top = Space.md, bottom = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var explain by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (explain) com.arnavgames.tycoon.ui.common.GlossarySheet(com.arnavgames.tycoon.ui.common.Term.Status) { explain = false }
                Text("STATUS", style = Aurum.type.overline, color = c.textTertiary,
                    modifier = Modifier.weight(1f).clickable(onClickLabel = "What is Status?") { explain = true })
                AurumChip(col.tier, icon = AbtIcons.Crown, tint = c.goldText)
            }
            Text(pts(col.status), style = Aurum.type.numberHero, color = c.textPrimary)
            Spacer(Modifier.height(Space.xxs))
            GoldProgress(col.tierProgress, label = "Progress to ${col.nextTier ?: "top tier"}")
            Spacer(Modifier.height(Space.xxs))
            Row {
                Text(col.nextTier?.let { "${it} at ${pts((col.nextTierPoints ?: 0).toDouble())}" } ?: "Top tier reached", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text("Collection ${fmt(col.value)}", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
            if (col.discount > 0) Text("Dealers give you ${(col.discount * 100).toInt()}% off", style = Aurum.type.bodySmall, color = c.gain)
        }
        HairlineDivider()
    }
}

/** Everything a dealer sells in one category. */
@Composable
fun CategoryScreen(vm: GameViewModel, categoryId: String, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val items = remember(s.luxury.owned, s.simTimeMs / 60_000, s.progress.chapter) { vm.luxuryCategory(s, categoryId) }
    val c = Aurum.colors
    GlassScaffold(title = vm.luxuryCategoryName(categoryId), onBack = onBack) {
        item(key = "list") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                items.forEachIndexed { i, it ->
                    if (i > 0) HairlineDivider(Modifier.padding(start = 84.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenItem(it.id) }.padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemImage(it.image, it.icon, it.name, Modifier.size(56.dp).clip(AurumShapes.small))
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(it.maker, style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${pts(it.status)} Status" + if (it.rarity != "common") " · ${it.rarity.replaceFirstChar { ch -> ch.uppercase() }}" else "", style = Aurum.type.bodySmall, color = c.goldText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(it.price), style = Aurum.type.numberSmall, color = c.textPrimary)
                            if (it.owned) AurumChip("Owned", icon = AbtIcons.Check, tint = c.gain)
                        }
                    }
                }
            }
        }
    }
}

/** One item: photo, story, Status, value and the dealer. */
@Composable
fun ItemScreen(vm: GameViewModel, itemId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.luxury.owned, s.simTimeMs / 30_000, s.cash) { vm.luxuryItem(s, itemId) } ?: return
    val c = Aurum.colors
    GlassScaffold(title = ui.name, onBack = onBack) {
        item(key = "hero") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                ItemImage(ui.image, ui.icon, ui.name, Modifier.fillMaxWidth().aspectRatio(if (ui.image != null) 1.25f else 2f).clip(AurumShapes.medium), iconSize = 56.dp)
                Column(Modifier.padding(Space.lg)) {
                    Text(ui.maker, style = Aurum.type.bodySmall, color = c.textSecondary)
                    Text("${ui.category}" + if (ui.location.isNotBlank()) " · ${ui.location}" else "", style = Aurum.type.bodySmall, color = c.textTertiary)
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(ui.owned.let { if (it) ui.value!! else ui.price }), style = Aurum.type.numberHero, color = c.textPrimary, modifier = Modifier.weight(1f))
                        AurumChip("${pts(ui.status)} Status", icon = AbtIcons.Crown, tint = c.goldText)
                    }
                    if (ui.description.isNotBlank()) { Spacer(Modifier.height(Space.xs)); Text(ui.description, style = Aurum.type.body, color = c.textSecondary) }
                    ui.credit?.let { Spacer(Modifier.height(Space.xs)); Text(it, style = Aurum.type.bodySmall, color = c.textTertiary) }
                }
            }
        }
        item(key = "deal") {
            AurumCard(Modifier.fillMaxWidth()) {
                when {
                    ui.lockedReason != null -> Text(ui.lockedReason, style = Aurum.type.body, color = c.textSecondary)
                    ui.atAuction -> Text("This piece is at auction right now. Win it on the Collection tab.", style = Aurum.type.body, color = c.warning)
                    ui.owned -> {
                        Text("In your collection", style = Aurum.type.title, color = c.textPrimary)
                        Spacer(Modifier.height(Space.xs))
                        Text("Paid ${fmt(ui.paid!!)} · worth ${fmt(ui.value!!)} now", style = Aurum.type.bodySmall, color = c.textSecondary)
                        ui.upkeepPerDay?.let { Text("Upkeep ${fmt(it)} a day", style = Aurum.type.bodySmall, color = c.warning) }
                        Spacer(Modifier.height(Space.md))
                        PillAction("Sell to the dealer · ${fmt(ui.sellQuote!!)}", enabled = true, onClick = { vm.dispatch(Command.SellLuxury(ui.id)) }, style = PillStyle.Glass, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        ui.upkeepPerDay?.let { Text("Upkeep about ${fmt(it)} a day once you own it.", style = Aurum.type.bodySmall, color = c.warning); Spacer(Modifier.height(Space.sm)) }
                        GoldButton("Buy · ${fmt(ui.buyCost)}", enabled = ui.cash >= ui.buyCost, onClick = { vm.dispatch(Command.BuyLuxury(ui.id)) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Space.xs))
                        Text("Includes the dealer's 2% fee. Dealers buy back at 85% of value.", style = Aurum.type.bodySmall, color = c.textTertiary)
                    }
                }
            }
        }
        if (ui.sets.isNotEmpty()) {
            item(key = "sets-h") { SectionHeader("Part of") }
            item(key = "sets") { AurumCard(Modifier.fillMaxWidth()) { ui.sets.forEach { Text(it, style = Aurum.type.body, color = c.textSecondary) } } }
        }
    }
}

/** The live auction: current bid, who leads, the hammer countdown and a bid button. */
@Composable
fun AuctionScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val a = vm.auction(s)
    val c = Aurum.colors
    GlassScaffold(title = "Live auction", onBack = onBack) {
        if (a == null) {
            item(key = "none") { AurumCard(Modifier.fillMaxWidth()) { Text("The hammer has fallen. The next auction starts soon.", style = Aurum.type.body, color = c.textSecondary) } }
            return@GlassScaffold
        }
        item(key = "lot") {
            AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                ItemImage(a.image, a.icon, a.name, Modifier.fillMaxWidth().aspectRatio(if (a.image != null) 1.4f else 2.6f).clip(AurumShapes.medium), iconSize = 48.dp)
                Column(Modifier.padding(Space.lg)) {
                    Text(a.name, style = Aurum.type.title, color = c.textPrimary)
                    Text("${a.maker} · estimate ${fmt(a.estimate)}", style = Aurum.type.bodySmall, color = c.textSecondary)
                }
            }
        }
        item(key = "bid") {
            AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CURRENT BID", style = Aurum.type.overline, color = c.textTertiary)
                        Text(fmt(a.bid), style = Aurum.type.numberHero, color = c.textPrimary)
                        Text(a.leader, style = Aurum.type.titleSmall, color = if (a.playerLeads) c.gain else c.goldText)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("HAMMER IN", style = Aurum.type.overline, color = c.textTertiary)
                        Text(secs(a.closesInMs), style = Aurum.type.number, color = if (a.closesInMs < 20_000) c.warning else c.textPrimary)
                        Text("${a.bidders} rivals in", style = Aurum.type.bodySmall, color = c.textTertiary)
                    }
                }
                Spacer(Modifier.height(Space.md))
                GoldButton(
                    text = if (a.playerLeads) "You lead" else "Bid ${fmt(a.nextBid)}",
                    enabled = a.canBid,
                    onClick = { vm.dispatch(Command.BidAuction) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                Text("Every bid adds time. When the room goes quiet for 30 seconds after the auction's end time, the hammer falls.", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        item(key = "proxy") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Leave a maximum bid", style = Aurum.type.title, color = c.textPrimary)
                Text(
                    if (a.playerMax.isPositive) "Your agent will bid up to ${fmt(a.playerMax)} for you, even while you're away."
                    else "Your agent bids for you, one step at a time, up to your maximum, even while you're away.",
                    style = Aurum.type.bodySmall, color = c.textSecondary,
                )
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf("Estimate" to 1.0, "+25%" to 1.25, "+50%" to 1.5).forEach { (label, k) ->
                        val max = a.estimate * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(k)
                        PillAction(label, enabled = max >= a.nextBid || a.playerLeads, onClick = { vm.dispatch(Command.SetMaxBid(max)) }, style = PillStyle.Glass, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item(key = "log-h") { SectionHeader("The room") }
        item(key = "log") {
            AurumCard(Modifier.fillMaxWidth()) {
                a.log.take(8).forEach { line -> Text(line, style = Aurum.type.body, color = if (line.startsWith("You")) c.gain else c.textSecondary) }
            }
        }
    }
}
