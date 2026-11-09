package com.arnavgames.tycoon.ui.world

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.AurumToggle
import com.arnavgames.tycoon.designsystem.components.GhostButton
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
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.durationText
import java.util.Locale

private fun pct(x: Double, digits: Int = 0) = String.format(Locale.US, "%.${digits}f%%", x * 100)
private fun signedPct(x: Double) = (if (x > 0) "+" else "") + pct(x)

private val ARCH_HUES = mapOf("Shark" to 205f, "Old Money" to 40f, "Disruptor" to 280f, "Collector" to 330f, "Philanthropist" to 140f, "Gambler" to 10f, "Recluse" to 230f, "Heir" to 50f, "Founder" to 45f)

/** A rival's caricature portrait, or a monogram on the archetype's colours when there is no portrait. */
@Composable
fun RivalAvatar(portrait: String?, name: String, archetype: String, size: Dp, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val base = modifier.size(size).clip(CircleShape)
    if (portrait != null) {
        AsyncImage(model = "file:///android_asset/$portrait", contentDescription = "$name portrait", contentScale = ContentScale.Crop, modifier = base.background(c.separator))
    } else {
        val hue = ARCH_HUES[archetype] ?: 45f
        val initials = name.split(" ", "-").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        Box(
            base.background(Brush.linearGradient(listOf(Color.hsl(hue, 0.35f, if (c.isDark) 0.34f else 0.62f), Color.hsl((hue + 30) % 360, 0.40f, if (c.isDark) 0.20f else 0.46f))))
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) { Text(initials, style = if (size >= 64.dp) Aurum.type.headline else Aurum.type.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

/** Three glass tiles on the Empire tab: the Desk, the Fortune List and your credit rating. */
@Composable
fun WorldStrip(vm: GameViewModel, onOpenDesk: () -> Unit, onOpenFortune: () -> Unit, onOpenFinance: () -> Unit) {
    val desk by vm.desk.collectAsStateWithLifecycle()
    val fortune by vm.fortuneRank.collectAsStateWithLifecycle()
    val fin by vm.financeSummary.collectAsStateWithLifecycle()
    val d = desk ?: return
    if (!d.unlocked) return
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
        val deskValue = when {
            d.cards.isNotEmpty() -> "${d.cards.size} waiting"
            d.nextCardInMs != null -> "Next in ${shortDuration(d.nextCardInMs)}"
            else -> "All clear"
        }
        add { m -> StripTile(AbtIcons.Cards, "Desk", deskValue, d.cards.isNotEmpty(), onOpenDesk, m) }
        add { m -> StripTile(AbtIcons.Globe, "Economy", d.phase, false, onOpenDesk, m) }
        fortune?.let { (unlocked, rank) -> if (unlocked) add { m -> StripTile(AbtIcons.Trophy, "Fortune", if (rank <= 220) "#$rank" else "Unranked", false, onOpenFortune, m) } }
        fin?.let { (unlocked, rating) -> if (unlocked) add { m -> StripTile(AbtIcons.Gauge, "Credit", rating, false, onOpenFinance, m) } }
    }
    // Two per row, so labels like "5 waiting" and "Expansion" are never cut off; one per row at large font sizes.
    val perRow = if (stacked) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        tiles.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "1h 20m", "45m", "2d 3h". */
private fun shortDuration(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(1)
    return when {
        m < 60 -> "${m}m"
        m < 24 * 60 -> "${m / 60}h ${m % 60}m"
        else -> "${m / 1440}d ${(m % 1440) / 60}h"
    }
}

@Composable
private fun StripTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, alert: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Aurum.colors
    AurumCard(modifier, onClick = onClick, highlighted = alert, contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(icon, null, tint = if (alert) c.goldText else c.textSecondary, size = 18.dp)
            Spacer(Modifier.width(Space.xs))
            Text(title, style = Aurum.type.label, color = c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(Space.xxs))
        Text(value, style = Aurum.type.titleSmall, color = if (alert) c.goldText else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ================================================================== Desk

/** Decision cards, active world effects and recent outcomes (docs/GDD.md §9). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeskScreen(vm: GameViewModel, onBack: () -> Unit, onOpenRival: (String) -> Unit) {
    val ui by vm.desk.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val d = ui ?: return
    val c = Aurum.colors
    var negotiating by remember { mutableStateOf<Pair<CardUi, Int>?>(null) }
    Box(Modifier.fillMaxSize()) {
    GlassScaffold(title = "The Desk", onBack = onBack) {
        item(key = "economy") {
            var explain by remember { mutableStateOf(false) }
            if (explain) com.arnavgames.tycoon.ui.common.GlossarySheet(com.arnavgames.tycoon.ui.common.Term.Economy) { explain = false }
            AurumCard(Modifier.fillMaxWidth(), onClick = { explain = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(AbtIcons.Globe, size = 40.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("ECONOMY", style = Aurum.type.overline, color = c.goldText)
                        Text(d.phase, style = Aurum.type.headline, color = c.textPrimary)
                        Text("Central bank rate ${pct(d.centralRate, 2)} a day", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    AbtIcon(AbtIcons.Info, "What does the economy do?", tint = c.textTertiary, size = 18.dp)
                }
            }
        }
        item(key = "cards-h") {
            SectionHeader("Decisions") {
                d.nextCardInMs?.let { Text(if (d.cards.size >= d.maxQueued) "Desk full" else "Next in ${durationText(it)}", style = Aurum.type.label, color = c.textTertiary) }
            }
        }
        if (d.cards.isEmpty()) item(key = "no-cards") {
            // An empty desk is a calm moment, not a blank: say so, and say when the next decision comes.
            AurumCard(Modifier.fillMaxWidth().testTag("desk-clear")) {
                com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.property("office_floor"), Modifier.fillMaxWidth().height(110.dp).clip(AurumShapes.medium))
                Spacer(Modifier.height(Space.md))
                Text("Desk clear", style = Aurum.type.titleSmall, color = c.textPrimary)
                Text((d.nextCardInMs?.let { "The next decision arrives in about ${durationText(it)}. " } ?: "") + "Up to three wait for you, and none is ever decided without you.",
                    style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        items(d.cards, key = { "card-${it.id}" }) { card -> DecisionCard(card, fmt, onChoose = { vm.resolveCard(card.id, it) }, onOpenRival = onOpenRival, onNegotiate = { negotiating = card to it }) }
        if (d.modifiers.isNotEmpty()) {
            item(key = "mods-h") { SectionHeader("In effect") }
            item(key = "mods") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
                    d.modifiers.forEachIndexed { i, m ->
                        if (i > 0) HairlineDivider(Modifier.padding(vertical = Space.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                                Text("${m.scope} · ${durationText(m.endsInMs)} left", style = Aurum.type.bodySmall, color = c.textSecondary)
                            }
                            Text(signedPct(m.pct), style = Aurum.type.number, color = if (m.pct >= 0) c.gain else c.loss)
                        }
                    }
                }
            }
        }
        if (d.log.isNotEmpty()) {
            item(key = "log-h") { SectionHeader("Recent") }
            items(d.log.take(12), key = { "log-${it.agoMs}-${it.title}" }) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(when (e.good) { true -> c.gain; false -> c.loss; null -> c.textTertiary }))
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text(e.text, style = Aurum.type.bodySmall, color = c.textSecondary)
                        Text("${durationText(e.agoMs)} ago", style = Aurum.type.label, color = c.textTertiary)
                    }
                }
            }
        }
    }
    negotiating?.let { (card, option) ->
        NegotiationDialog(card.rival?.name ?: "The other side", card.rival?.archetype, card.options[option].label,
            onDone = { edge -> negotiating = null; vm.resolveCard(card.id, option, edge) }, onCancel = { negotiating = null })
    }
    }
}

@Composable
private fun DecisionCard(card: CardUi, fmt: (Money) -> String, onChoose: (Int) -> Unit, onOpenRival: (String) -> Unit, onNegotiate: (Int) -> Unit = {}) {
    val c = Aurum.colors
    val kind = when (card.kind) {
        "takeover" -> "Takeover"; "acquisition" -> "Offer"; "jv" -> "Partnership"; "rival" -> "Rival"; "nemesis" -> "Nemesis"
        "world" -> "World"; "personal" -> "Personal"; else -> card.businessName ?: "Business"
    }
    AurumCard(Modifier.fillMaxWidth().testTag("decision-card"), highlighted = card.kind == "takeover" || card.kind == "nemesis") {
        card.businessId?.let { id ->
            com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.business(id), Modifier.fillMaxWidth().height(96.dp).clip(AurumShapes.medium))
            Spacer(Modifier.height(Space.md))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AurumChip(kind, icon = if (card.rival != null) AbtIcons.Rivals else AbtIcons.Cards, tint = if (card.kind == "takeover") c.loss else c.goldText)
            Spacer(Modifier.weight(1f))
            card.rival?.let { r ->
                Row(Modifier.clip(AurumShapes.pill).clickable(role = Role.Button, onClick = { onOpenRival(r.id) }).padding(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = Aurum.type.label, color = c.textSecondary, maxLines = 1)
                    Spacer(Modifier.width(Space.xs))
                    RivalAvatar(r.portrait, r.name, r.archetype, 32.dp)
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(card.title, style = Aurum.type.headline, color = c.textPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(card.text, style = Aurum.type.body, color = c.textSecondary)
        card.pressure?.let {
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Warning, null, tint = c.loss, size = 16.dp)
                Spacer(Modifier.width(Space.xs))
                Text(it, style = Aurum.type.bodySmall, color = c.loss)
            }
        }
        Spacer(Modifier.height(Space.md))
        card.options.forEachIndexed { i, o ->
            if (i > 0) Spacer(Modifier.height(Space.sm))
            Column(
                Modifier.fillMaxWidth().testTag(if (o.enabled) "card-option" else "card-option-disabled").clip(AurumShapes.medium).background(c.textPrimary.copy(alpha = 0.06f))
                    .clickable(enabled = o.enabled, role = Role.Button, onClickLabel = o.label, onClick = { onChoose(o.index) })
                    .padding(Space.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.label, style = Aurum.type.titleSmall, color = if (o.enabled) c.textPrimary else c.textTertiary, modifier = Modifier.weight(1f))
                    if (o.maxCost.isPositive) Text(fmt(o.maxCost), style = Aurum.type.number, color = if (o.enabled) c.goldText else c.textTertiary)
                }
                if (o.detail.isNotBlank()) Text(o.detail, style = Aurum.type.bodySmall, color = c.textSecondary)
                if (o.outcomes.size > 1) {
                    o.outcomes.forEach { oc -> Text("${oc.percent}% chance · ${oc.text}", style = Aurum.type.bodySmall, color = c.textTertiary) }
                }
                o.reason?.let { Text(it, style = Aurum.type.label, color = c.loss) }
                if (o.enabled && o.outcomes.size > 1 && (o.label.startsWith("Negotiate") || o.label.startsWith("Counter") || o.label.startsWith("Call"))) {
                    Spacer(Modifier.height(Space.xs))
                    PillAction("Negotiate first (mini-game)", true, { onNegotiate(o.index) }, style = PillStyle.Glass)
                }
            }
        }
    }
}

// ================================================================== Fortune List

/** The Fortune List of 220 parody billionaires and you (docs/GDD.md §10). */
@Composable
fun FortuneScreen(vm: GameViewModel, onBack: () -> Unit, onOpenRival: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val ui = remember(s.simTimeMs / 60_000, s.businesses.size) { vm.fortune(s) }
    val c = Aurum.colors
    GlassScaffold(title = "Fortune List", onBack = onBack) {
        item(key = "you") {
            AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                Text("YOUR RANK", style = Aurum.type.overline, color = c.goldText)
                Text(if (ui.playerRank <= 220) "#${ui.playerRank} of ${ui.total}" else "Not on the list yet", style = Aurum.type.numberHero, color = c.textPrimary)
                val next = ui.rows.getOrNull(ui.playerRank - 2)
                Text(
                    next?.let { "Next to pass: ${it.name} (${fmt(it.netWorth)})" } ?: "You are the richest person in the world.",
                    style = Aurum.type.bodySmall, color = c.textSecondary,
                )
            }
        }
        ui.nemesis?.let { n ->
            item(key = "nemesis") {
                AurumCard(Modifier.fillMaxWidth(), onClick = { n.id?.let(onOpenRival) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RivalAvatar(n.portrait, n.name, n.archetype, 56.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text("THIS CHAPTER'S NEMESIS", style = Aurum.type.overline, color = c.loss)
                            Text(n.name, style = Aurum.type.headline, color = c.textPrimary)
                            Text("#${n.rank} · ${fmt(n.netWorth)}", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        item(key = "list-h") { SectionHeader("The world's richest") }
        items(ui.rows, key = { "r-${it.id ?: "you"}" }) { r ->
            Row(
                Modifier.fillMaxWidth().clip(AurumShapes.medium)
                    .background(if (r.isPlayer) c.gold.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(enabled = r.id != null, role = Role.Button) { r.id?.let(onOpenRival) }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${r.rank}", style = Aurum.type.number, color = if (r.rank <= 10) c.goldText else c.textTertiary, modifier = Modifier.width(40.dp))
                if (r.isPlayer) Box(Modifier.size(40.dp).clip(CircleShape).background(c.gold), contentAlignment = Alignment.Center) { AbtIcon(AbtIcons.Crown, null, tint = Color.Black, size = 20.dp) }
                else RivalAvatar(r.portrait, r.name, r.archetype, 40.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (r.isPlayer) "Founder · your empire" else "${r.archetype} · ${r.city}", style = Aurum.type.bodySmall, color = if (r.isNemesis) c.loss else c.textSecondary, maxLines = 1)
                }
                Text(fmt(r.netWorth), style = Aurum.type.number, color = c.textPrimary)
            }
        }
    }
}

@Composable
fun RivalScreen(vm: GameViewModel, rivalId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val r = remember(s.simTimeMs / 60_000, s.world.relations) { vm.rival(s, rivalId) } ?: return
    val c = Aurum.colors
    GlassScaffold(title = r.name, onBack = onBack) {
        item(key = "hero") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RivalAvatar(r.portrait, r.name, r.archetype, 96.dp)
                    Spacer(Modifier.width(Space.lg))
                    Column(Modifier.weight(1f)) {
                        Text("#${r.rank} ON THE FORTUNE LIST", style = Aurum.type.overline, color = c.goldText)
                        Text(fmt(r.netWorth), style = Aurum.type.numberHero, color = c.textPrimary)
                        Text(if (r.gapLog10 > 0) "${String.format(Locale.US, "%.1f", Math.pow(10.0, r.gapLog10))}× richer than you" else "You are richer", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(Space.md))
                Sparkline(r.history, Modifier.fillMaxWidth().height(56.dp))
                Text("Net worth over the last 7 days", style = Aurum.type.label, color = c.textTertiary)
            }
        }
        item(key = "about") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AurumChip(r.archetype, icon = AbtIcons.Rivals, tint = c.goldText)
                    r.nemesisChapter?.let { Spacer(Modifier.width(Space.xs)); AurumChip("Nemesis · Chapter $it", tint = c.loss) }
                }
                Spacer(Modifier.height(Space.xs))
                Text(r.archetypeText, style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                Fact("Fortune", r.source)
                Fact("Home", r.city)
                Fact("Age", "${r.age}")
                Fact("Sectors", r.sectors.joinToString(", "))
            }
        }
        item(key = "relation") {
            AurumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Relationship", style = Aurum.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text((if (r.relation > 0) "+" else "") + r.relation, style = Aurum.type.number, color = when { r.relation > 0 -> c.gain; r.relation < 0 -> c.loss; else -> c.textSecondary })
                }
                Spacer(Modifier.height(Space.xs))
                GoldProgress(((r.relation + 100) / 200f), height = 4.dp, label = "Relationship")
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (r.relation >= 40) "An ally: they will act as your white knight against a hostile takeover."
                    else "Deals, dinners and charity win friends. Declined offers and defended takeovers make enemies. Allies (40+) can rescue you from a takeover.",
                    style = Aurum.type.bodySmall, color = c.textSecondary,
                )
            }
        }
        item(key = "note") {
            Text("Rivals are parodies (GDD §1.1). Their portraits are caricatures of invented people.", style = Aurum.type.label, color = c.textTertiary, modifier = Modifier.padding(horizontal = Space.xs))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = Aurum.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
        Text(label, style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.width(120.dp), maxLines = 1)
        Text(value, style = Aurum.type.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    val gold = Aurum.colors.gold
    Canvas(modifier.semantics { contentDescription = "Net worth trend" }) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max().let { if (it - lo < 1e-9) lo + 1 else it }
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(size.width * i / (values.size - 1), size.height * (1 - ((v - lo) / (hi - lo)).toFloat()))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, gold, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ================================================================== Finance

/** Credit rating, loans, the credit line, taxes and the never-stuck options (docs/GDD.md §11). */
@Composable
fun FinanceScreen(vm: GameViewModel, onBack: () -> Unit, onOpenTaxes: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val f = remember(s.simTimeMs / 10_000, s.finance, s.cash) { vm.finance(s) }
    val c = Aurum.colors
    var share by rememberSaveable { mutableIntStateOf(50) }
    var term by rememberSaveable { mutableIntStateOf(f.terms.getOrElse(1) { 3 }) }
    var confirm by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        GlassScaffold(title = "Finance", onBack = onBack) {
            if (f.phoenix || f.canRestructure) item(key = "rescue") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Text(if (f.phoenix) "PHOENIX PROTOCOL" else "RESTRUCTURING", style = Aurum.type.overline, color = c.loss)
                    Text(
                        if (f.phoenix) "Your net worth has been below zero for a while. An orderly liquidation sells everything, clears every debt, keeps your businesses' managers and 10% of their levels, and gives you a ×3 comeback boost for 2 hours."
                        else "Your loan payments are larger than your income. You can stretch every loan to twice its length with smaller payments; your credit rating drops.",
                        style = Aurum.type.body, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Space.sm))
                    GoldButton(if (f.phoenix) "Start the Phoenix protocol" else "Restructure loans", onClick = { confirm = if (f.phoenix) "phoenix" else "restructure" })
                }
            }
            item(key = "credit") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CREDIT SCORE", style = Aurum.type.overline, color = c.goldText)
                            Text("${f.score}", style = Aurum.type.numberHero, color = c.textPrimary)
                        }
                        AurumChip(f.rating, icon = AbtIcons.Gauge, tint = c.goldText)
                    }
                    GoldProgress(((f.score - 300) / 550f).coerceIn(0f, 1f), height = 4.dp, label = "Credit score")
                    Spacer(Modifier.height(Space.xs))
                    // The headline first; how the score moves is one tap away (progressive disclosure).
                    var howOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.nextRating?.let { next -> f.nextRatingAt?.let { at -> "${at - f.score} points to $next ($at)" } } ?: "The best rating there is", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(if (howOpen) "Hide" else "How it works", style = Aurum.type.label, color = c.goldText,
                            modifier = Modifier.clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.pill).clickable { howOpen = !howOpen }.padding(horizontal = Space.sm, vertical = Space.sm))
                    }
                    if (howOpen) Text(
                        "On-time payments raise your score; missed payments, foreclosures, audits and restructuring lower it. Heavy debt relative to income also counts against you.",
                        style = Aurum.type.bodySmall, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Fact("Mortgages", "Up to ${pct(f.maxLtv)} loan-to-value")
                    Fact("Payments", "${f.onTime} on time · ${f.missed} missed")
                    Fact("Total debt", fmt(f.totalDebt))
                }
            }
            item(key = "loans-h") { SectionHeader("Borrowing") }
            item(key = "loans") {
                AurumCard(Modifier.fillMaxWidth()) {
                    if (!f.loansUnlocked) {
                        Text("Loans and a credit line open in chapter ${f.loansChapter}.", style = Aurum.type.body, color = c.textSecondary)
                        return@AurumCard
                    }
                    Fact("You can borrow", fmt(f.borrowLimit))
                    Fact("Interest", "${pct(f.loanRate, 2)} a day")
                    Spacer(Modifier.height(Space.sm))
                    Text("How much (of what you can borrow)", style = Aurum.type.label, color = c.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        listOf(25, 50, 100).forEach { p -> PillAction("$p%", true, { share = p }, style = if (share == p) PillStyle.Gold else PillStyle.Glass) }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text("For how long", style = Aurum.type.label, color = c.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        f.terms.forEach { t -> PillAction("$t day" + if (t > 1) "s" else "", true, { term = t }, style = if (term == t) PillStyle.Gold else PillStyle.Glass) }
                    }
                    val amount = f.borrowLimit * Multiplier.ofDouble(share / 100.0)
                    Spacer(Modifier.height(Space.sm))
                    GoldButton("Borrow ${fmt(amount)} for $term day" + if (term > 1) "s" else "", enabled = amount.isPositive, onClick = { vm.dispatch(Command.TakeLoan(amount, term)) }, modifier = Modifier.fillMaxWidth())
                    if (amount.isPositive) {
                        // The whole cost up front: payment, count and total interest.
                        val payment = vm.loanInstallment(amount, f.loanRate, term)
                        val total = payment * (term * 4L)
                        Text("You pay back ${fmt(payment)} every 6 hours, ${term * 4} times: ${fmt(total)} in total, of which ${fmt(total - amount)} is interest.",
                            style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("loan-quote"))
                    }
                    Text("Payments are taken automatically. A missed payment adds to the loan and hurts your score.", style = Aurum.type.label, color = c.textTertiary)
                    if (f.loans.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        f.loans.forEach { l ->
                            HairlineDivider(Modifier.padding(vertical = Space.xs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Loan · ${fmt(l.principal)} left", style = Aurum.type.titleSmall, color = c.textPrimary)
                                    Text("${fmt(l.installment)} every 6 h · ${l.paymentsLeft} payments left · ${pct(l.ratePerDay, 2)} a day", style = Aurum.type.bodySmall, color = c.textSecondary)
                                }
                                PillAction("Repay", f.cash >= l.principal, { vm.dispatch(Command.RepayLoan(l.id)) }, style = PillStyle.Glass)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    HairlineDivider()
                    Spacer(Modifier.height(Space.sm))
                    Text("Credit line", style = Aurum.type.titleSmall, color = c.textPrimary)
                    Text("${fmt(f.creditLine)} drawn of ${fmt(f.creditLineLimit)}. Interest is charged every 6 hours on what you have drawn.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        val room = f.creditLineLimit - f.creditLine
                        PillAction("Draw ${fmt(room * Multiplier.of("0.5"))}", room.isPositive, { vm.dispatch(Command.DrawCredit(room * Multiplier.of("0.5"))) }, style = PillStyle.Glass)
                        PillAction("Repay all", f.creditLine.isPositive && f.cash.isPositive, { vm.dispatch(Command.RepayCredit(f.creditLine.min(f.cash))) }, style = PillStyle.Glass)
                    }
                }
            }
            item(key = "tax-h") { SectionHeader("Taxes") }
            item(key = "taxes") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = f.pending != null) {
                    if (!f.taxesUnlocked) {
                        Text("Income tax starts in chapter ${f.taxesChapter}.", style = Aurum.type.body, color = c.textSecondary)
                        return@AurumCard
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(AbtIcons.Receipt, size = 40.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text("Withholding ${pct(f.taxRate, 1)}", style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("Quarter ${f.quarter + 1}" + (f.quarterEndsInMs?.let { " ends in ${durationText(it)}" } ?: ""), style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text("Tax is taken from business income as you earn it, at the rate of your bracket (15–35% by income per hour). The Hustle is never taxed.", style = Aurum.type.bodySmall, color = c.textSecondary)
                    f.pending?.let { p ->
                        Spacer(Modifier.height(Space.sm))
                        GoldButton("File quarter ${p.quarter + 1} · claim up to ${fmt(p.refund)}", onClick = onOpenTaxes, modifier = Modifier.fillMaxWidth())
                        Text("Filed automatically in ${durationText(p.dueInMs)} if you don't.", style = Aurum.type.label, color = c.textTertiary)
                    }
                    f.lastResult?.let { Spacer(Modifier.height(Space.xs)); Text("Last return: $it", style = Aurum.type.label, color = c.textTertiary) }
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always file automatically", style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("Always correct, no audit risk, no savings.", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        AurumToggle(f.autoFile, onCheckedChange = { vm.dispatch(Command.SetAutoFile(it)) })
                    }
                }
            }
        }
        when (confirm) {
            "phoenix" -> AurumDialog(title = "Start the Phoenix protocol?", onDismiss = { confirm = null }) {
                Text("Every investment, property and collectible is sold and your debts are cleared. Businesses keep their managers and 10% of their levels. This cannot be undone.", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Rise again", onClick = { confirm = null; vm.dispatch(Command.AcceptPhoenix) }, modifier = Modifier.fillMaxWidth())
                GhostButton("Not now", onClick = { confirm = null }, modifier = Modifier.fillMaxWidth())
            }
            "restructure" -> AurumDialog(title = "Restructure your loans?", onDismiss = { confirm = null }) {
                Text("Every loan runs twice as long with smaller payments. Your credit score drops by 40 points.", style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Restructure", onClick = { confirm = null; vm.dispatch(Command.Restructure) }, modifier = Modifier.fillMaxWidth())
                GhostButton("Cancel", onClick = { confirm = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Self-filing a tax return: claim the deductions you earned, optionally an aggressive interpretation (risk shown up front). */
@Composable
fun TaxFilingScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val s = state ?: return
    val f = remember(s.finance, s.simTimeMs / 60_000) { vm.finance(s) }
    val c = Aurum.colors
    var aggressive by rememberSaveable { mutableStateOf(false) }
    val p = f.pending
    GlassScaffold(title = "Tax return", onBack = onBack) {
        if (p == null) {
            item(key = "none") { Text(f.lastResult ?: "No return is due.", style = Aurum.type.body, color = c.textSecondary) }
            return@GlassScaffold
        }
        item(key = "summary") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("QUARTER ${p.quarter + 1}", style = Aurum.type.overline, color = c.goldText)
                Fact("Business income", fmt(p.income))
                Fact("Tax withheld", fmt(p.withheld))
                Fact("Window", "${durationText(p.dueInMs)} left")
            }
        }
        item(key = "deductions") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("Deductions you earned", style = Aurum.type.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xs))
                if (p.deductions.isEmpty()) Text("None this quarter. Own a tech, pharma, space or energy business, own property, or make donations and R&D choices on the Desk to earn them.", style = Aurum.type.bodySmall, color = c.textSecondary)
                p.deductions.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
                        Text(WorldMapper.DEDUCTION_NAMES[k] ?: k, style = Aurum.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(pct(v, 1), style = Aurum.type.number, color = c.gain)
                    }
                }
                HairlineDivider(Modifier.padding(vertical = Space.xs))
                Row {
                    Text("Refund", style = Aurum.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(fmt(p.refund), style = Aurum.type.number, color = c.textPrimary)
                }
            }
        }
        item(key = "aggressive") {
            AurumCard(Modifier.fillMaxWidth(), highlighted = aggressive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aggressive interpretation", style = Aurum.type.titleSmall, color = c.textPrimary)
                        Text("Claim ${pct(p.aggressiveExtra, 1)} more (${fmt(p.aggressiveRefund)} in total).", style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    AurumToggle(aggressive, onCheckedChange = { aggressive = it })
                }
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbtIcon(AbtIcons.Warning, null, tint = c.loss, size = 16.dp)
                    Spacer(Modifier.width(Space.xs))
                    Text("Audit chance ${pct(p.auditChance)}. If audited you repay the extra twice over and lose 40 credit points.", style = Aurum.type.bodySmall, color = c.loss)
                }
            }
        }
        item(key = "file") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                GoldButton("File and claim ${fmt(if (aggressive) p.aggressiveRefund else p.refund)}", onClick = { vm.dispatch(Command.FileTaxes(aggressive)) { onBack() } }, modifier = Modifier.fillMaxWidth())
                Text("Not filing is fine: the return is filed automatically when the window closes, with no savings and no risk.", style = Aurum.type.label, color = c.textTertiary)
            }
        }
    }
}
