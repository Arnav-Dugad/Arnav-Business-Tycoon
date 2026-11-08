package com.arnavgames.tycoon.ui.empire

import kotlinx.coroutines.launch
import com.arnavgames.tycoon.ui.common.shortPersonName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.BuyQty
import com.arnavgames.tycoon.ui.BusinessUi
import com.arnavgames.tycoon.ui.EmpireUi
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.HqUi
import com.arnavgames.tycoon.ui.HustleUi
import com.arnavgames.tycoon.ui.MissionUi
import com.arnavgames.tycoon.ui.OpenableUi
import com.arnavgames.tycoon.ui.common.countdownText
import kotlinx.coroutines.delay
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassCard
import com.arnavgames.tycoon.designsystem.theme.glassSource

/**
 * The Empire tab: an overview of where things stand and what to do next. The tapping counter has its own full-screen
 * page ([CounterScreen]) and the businesses their own tab ([BusinessesScreen]); this screen links to both.
 */
@Composable
fun EmpireScreen(
    vm: GameViewModel, onOpenBusiness: (String) -> Unit, onOpenDesk: () -> Unit = {}, onOpenFortune: () -> Unit = {}, onOpenFinance: () -> Unit = {},
    onSwitchTab: (String) -> Unit = {}, onOpenCounter: () -> Unit = {},
) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val e = ui ?: return
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }

    val mentorLine by vm.mentor.line.collectAsStateWithLifecycle()
    val guide = guideText(e, GuideWhere.Empire)
    val guiding = guide != null
    // Tapping leads in Chapter 1; after that the businesses do.
    val hustleFirst = (e.mission?.chapterNumber ?: e.chapter) <= 1
    androidx.compose.runtime.LaunchedEffect(e.mission?.chapterNumber, guiding) { if (!guiding) vm.mentorTip(e.mission?.chapterNumber ?: 8) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A new line is inserted above the first card; lazy lists keep the old first item in place, so reveal it.
    androidx.compose.runtime.LaunchedEffect(mentorLine?.id) { if (mentorLine != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0) }

    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        // Content scrolls beneath the frosted header and tab bar (it is the glass "content" layer).
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = with(density) { headerPx.toDp() } + Space.md,
                bottom = BottomBarReserve + navInset + Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("empire-list"),
        ) {
            guide?.let { g -> item(key = "guide") { GuideBanner(g) } }
            if (!guiding) mentorLine?.let { l -> item(key = "mentor") { MentorCard(vm.mentor.name, l.text, onDismiss = { vm.mentor.dismiss() }) } }
            e.mission?.let { m ->
                item(key = "mission") {
                    // "Show me": open the business or the counter, or switch to the tab where the goal is played.
                    val go: (() -> Unit)? = m.goTo?.let { target ->
                        {
                            when {
                                target.startsWith("business:") -> onOpenBusiness(target.removePrefix("business:"))
                                target == "counter" -> onOpenCounter()
                                target.startsWith("tab:") -> onSwitchTab(target.removePrefix("tab:"))
                                target.startsWith("open:") -> onSwitchTab("businesses:$target")
                                target == "hq" -> onSwitchTab("businesses:hq")
                                else -> onSwitchTab("businesses")
                            }
                        }
                    }
                    NextMoveCard(m, onClaim = { vm.dispatch(Command.ClaimMission(m.id)) }, onGo = go)
                }
            }
            e.crown?.let { cr -> item(key = "crown") { CrownReadyCard(cr, onOpen = { onSwitchTab("dynasty") }) } }
            if (hustleFirst) item(key = "counter") { CounterEntryCard(e.hustle, fmt, highlighted = e.onboardingStep == 1, onOpen = onOpenCounter) }
            item(key = "businesses") { BusinessesSummaryCard(e, onOpen = { onSwitchTab("businesses") }) }
            item(key = "world") { com.arnavgames.tycoon.ui.world.WorldStrip(vm, onOpenDesk, onOpenFortune, onOpenFinance) }
            if (!hustleFirst) item(key = "counter") { CounterEntryCard(e.hustle, fmt, highlighted = false, onOpen = onOpenCounter) }
        }
        EmpireHeader(e, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
        // Celebrations appear just below the header so they never cover the numbers.
        MomentOverlay(vm, Modifier.align(Alignment.TopCenter).padding(top = with(density) { headerPx.toDp() }))
    }
}

/** The way into the full-screen counter: what a customer pays, and the frenzy state. */
@Composable
private fun CounterEntryCard(h: HustleUi, fmt: (Money) -> String, highlighted: Boolean, onOpen: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth().testTag("counter-entry"), onClick = onOpen, highlighted = highlighted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(c.goldFillTop, c.goldFillBottom, c.goldDeep)))
                    .border(1.dp, Brush.verticalGradient(listOf(c.goldBright, c.goldDeep)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { AbtIcon(AbtIcons.Hand, null, tint = c.textOnGold, size = 28.dp) }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Work the counter", style = Aurum.type.title, color = c.textPrimary)
                Text("${fmt(h.perTap)} per customer", style = Aurum.type.bodySmall, color = c.textSecondary)
                Text(
                    when {
                        h.frenzyActive -> "Frenzy ×3 running · ${countdownText(h.frenzyEndsAtSim - h.simNow)}"
                        h.frenzyCharges > 0 -> "${h.frenzyCharges} ${if (h.frenzyCharges == 1) "frenzy" else "frenzies"} ready"
                        else -> "Serve customers for cash"
                    },
                    style = Aurum.type.label, color = if (h.frenzyActive || h.frenzyCharges > 0) c.goldText else c.textTertiary,
                )
            }
            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 20.dp)
        }
    }
}

/** A glance at the businesses: income, what needs a tap (full tills), the best buy, and what you can open. */
@Composable
private fun BusinessesSummaryCard(e: EmpireUi, onOpen: () -> Unit) {
    val c = Aurum.colors
    val income = e.header.income
    val fullTills = e.businesses.count { it.managerName == null && it.storageCap.isPositive && it.stored >= it.storageCap }
    val best = e.businesses.firstOrNull { it.bestValue != null }
    val canOpen = e.openable.count { it.lockedReason == null && it.canAfford }
    AurumCard(Modifier.fillMaxWidth().testTag("businesses-summary"), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR BUSINESSES", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
            Text("${e.hq.slotsUsed}/${e.hq.slots} slots", style = Aurum.type.label, color = c.textTertiary)
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(MoneyFormat.perSecond(income.perSecond()), style = Aurum.type.headline, color = c.textPrimary)
            Spacer(Modifier.width(Space.xs))
            Text(
                "from ${e.businesses.size} ${if (e.businesses.size == 1) "business" else "businesses"}",
                style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        val notes = buildList {
            if (fullTills > 0) add(AbtIcons.Cash to (if (fullTills == 1) "1 till is full: collect it" else "$fullTills tills are full: collect them"))
            best?.let { b -> add(AbtIcons.StarFilled to "${b.name}: ${b.bestValue!!.replaceFirstChar { ch -> ch.lowercase() }}") }
            if (canOpen > 0) add(AbtIcons.Plus to (if (canOpen == 1) "You can open a new business" else "You can open $canOpen new businesses"))
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            notes.forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    AbtIcon(icon, null, tint = c.goldText, size = 14.dp)
                    Spacer(Modifier.width(Space.xs))
                    Text(text, style = Aurum.type.bodySmall, color = c.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        GoldButton("Manage businesses", onClick = onOpen, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Store)
    }
}

/** After the last chapter: the next move is the succession. Shows what the heir gains, and that waiting grows it. */
@Composable
private fun CrownReadyCard(cr: com.arnavgames.tycoon.ui.CrownReadyUi, onOpen: () -> Unit) {
    val c = Aurum.colors
    fun x(m: Double) = "×" + String.format(java.util.Locale.US, if (m < 10) "%.2f" else "%.1f", m)
    AurumCard(Modifier.fillMaxWidth().testTag("crown-ready"), highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR STORY IS COMPLETE", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
            AbtIcon(AbtIcons.Crown, null, tint = c.goldText, size = 20.dp)
        }
        Spacer(Modifier.height(Space.xs))
        Text("The crown is ready", style = Aurum.type.headline, color = c.textPrimary)
        Text(
            "Pass it to an heir of generation ${cr.generation}. They start again from a kiosk, but every business earns " +
                "${x(cr.multiplierAfter)} instead of ${x(cr.multiplierNow)}, for good. You'd gain ${"%,d".format(cr.legacyGain)} Legacy; " +
                "it grows while you keep building.",
            style = Aurum.type.bodySmall, color = c.textSecondary,
        )
        Spacer(Modifier.height(Space.sm))
        GoldButton("Choose your heir", onClick = onOpen, leadingIcon = AbtIcons.Crown, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun EmpireHeader(e: EmpireUi, fmt: (Money) -> String, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.md, bottom = Space.md),
    ) {
        var explain by remember { mutableStateOf<com.arnavgames.tycoon.ui.common.Term?>(null) }
        explain?.let { com.arnavgames.tycoon.ui.common.GlossarySheet(it) { explain = null } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NET WORTH", style = Aurum.type.overline, color = c.textTertiary,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "What is net worth?") { explain = com.arnavgames.tycoon.ui.common.Term.NetWorth })
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CurrencyPill(AbtIcons.Bullion, e.header.bullion.toString(), "Bullion", c.bullion) { explain = com.arnavgames.tycoon.ui.common.Term.Bullion }
                CurrencyPill(AbtIcons.Favor, e.header.favor.toString(), "Favor", c.goldText) { explain = com.arnavgames.tycoon.ui.common.Term.Favor }
            }
        }
        RollingMoney(e.header.netWorth, e.header.income, format = fmt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Cash, null, tint = c.goldText, size = 16.dp)
            Spacer(Modifier.width(Space.xs))
            RollingMoney(e.header.cash, e.header.income, style = Aurum.type.number, color = c.textPrimary, format = fmt)
            Spacer(Modifier.width(Space.xs))
            Text("cash", style = Aurum.type.bodySmall, color = c.textTertiary,
                modifier = Modifier.clickable(onClickLabel = "What is cash?") { explain = com.arnavgames.tycoon.ui.common.Term.Cash })
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

@Composable
internal fun CurrencyPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.clip(AurumShapes.pill).clickable(onClickLabel = "What is $label?", onClick = onClick)
            .glass(AurumShapes.pill, GlassWeight.Thin, blur = false).padding(horizontal = Space.sm + 2.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(icon, null, tint = tint, size = 14.dp)
        Spacer(Modifier.width(Space.xs))
        Text(value, style = Aurum.type.numberSmall, color = c.textPrimary)
    }
}

// ------------------------------------------------------------------ onboarding

/** Where a guide line is shown: each screen words the step for what you can do from there. */
internal enum class GuideWhere { Empire, Businesses, Counter }

/** The one instruction a new player sees: a waiting reward comes first, then the current step. */
internal fun guideText(e: EmpireUi, where: GuideWhere): String? = when {
    e.onboardingStep !in 1..4 -> null
    e.mission?.claimable == true ->
        if (where == GuideWhere.Empire) "You've earned a reward. Tap Claim on the card below." else "You've earned a reward: claim it on the Empire tab."
    else -> when (e.onboardingStep) {
        1 -> if (where == GuideWhere.Counter) "Tap the counter to serve customers. Every customer pays cash."
        else "Open the counter below and serve customers. Every customer pays cash."
        2 -> if (where == GuideWhere.Businesses) "Buy a level for your kiosk: it earns more every second, even without you."
        else "Nice! Now open Businesses in the bar below and buy a level for your kiosk."
        3 -> if (where == GuideWhere.Businesses) "Your kiosk fills its till while it works. Tap Collect to bank the cash."
        else "Your kiosk's till is filling up. Open Businesses and tap Collect."
        else -> if (where == GuideWhere.Businesses) "Keep growing. Open the kiosk and hire a manager so it runs without you."
        else "Keep growing. A manager will run the kiosk for you: the card below shows how."
    }
}

/** Advances the onboarding guide when the player has done what it asks. */
@Composable
internal fun OnboardingDriver(e: EmpireUi, vm: GameViewModel) {
    val step = e.onboardingStep
    val retail = e.businesses.firstOrNull { it.id == "retail" }
    val next = when {
        step == 1 && e.hustle.totalTaps >= 8 -> 2
        step == 2 && (retail?.level ?: 0) >= 2 -> 3
        step == 3 && (retail?.level ?: 0) >= 5 -> 4
        step == 4 && e.businesses.any { it.managerName != null } -> 5
        else -> null
    }
    LaunchedEffect(next) { if (next != null) vm.dispatch(Command.AdvanceOnboarding(next)) }
}

@Composable
internal fun GuideBanner(text: String) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().glassCard(AurumShapes.medium, highlighted = true).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 20.dp)
        Spacer(Modifier.width(Space.md))
        Text(text, style = Aurum.type.body, color = c.textPrimary)
    }
}

/** The Chairman's line: one at a time, dismissible. */
@Composable
private fun MentorCard(name: String, text: String, onDismiss: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().glassCard(AurumShapes.medium).padding(Space.md).testTag("mentor-card"),
        verticalAlignment = Alignment.Top,
    ) {
        // The Chairman's face (AI caricature of an invented person; docs/ART_PIPELINE.md §3a).
        coil3.compose.AsyncImage(
            model = "file:///android_asset/img/mentor/chairman.webp", contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, c.goldText.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                .background(c.separator),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = Aurum.type.overline, color = c.goldText)
            // Typewriter reveal: the unrevealed part is drawn transparent so the card never changes size, and screen
            // readers get the whole line at once. Reduced motion shows it immediately.
            val line = "“$text”"
            val reduced = Aurum.reducedMotion
            var shown by remember(line) { mutableIntStateOf(if (reduced) line.length else 0) }
            androidx.compose.runtime.LaunchedEffect(line, reduced) {
                if (reduced) { shown = line.length; return@LaunchedEffect }
                while (shown < line.length) { delay(16); shown = (shown + 1).coerceAtMost(line.length) }
            }
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(line.substring(0, shown))
                    withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent)) { append(line.substring(shown)) }
                },
                style = Aurum.type.body, color = c.textPrimary,
            )
        }
        androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            AbtIcon(AbtIcons.Close, "Dismiss", tint = c.textTertiary, size = 16.dp)
        }
    }
}

// ------------------------------------------------------------------ next move

@Composable
private fun NextMoveCard(m: MissionUi, onClaim: () -> Unit, onGo: (() -> Unit)? = null) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), highlighted = m.claimable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CHAPTER ${m.chapterNumber} · ${m.chapterTitle.uppercase()}", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
            Text("${m.done}/${m.total}", style = Aurum.type.numberSmall, color = c.textTertiary)
        }
        Spacer(Modifier.height(Space.sm))
        Text(m.title, style = Aurum.type.headline, color = c.textPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(m.description, style = Aurum.type.body, color = c.textSecondary)
        Spacer(Modifier.height(Space.md))
        if (m.claimable) {
            GoldButton("Claim ${m.reward}", onClick = onClaim, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Gift)
        } else {
            GoldProgress(m.progress, label = "Mission progress ${(m.progress * 100).toInt()} percent")
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Gift, null, tint = c.textTertiary, size = 14.dp)
                Spacer(Modifier.width(Space.xs))
                Text("Reward: ${m.reward}", style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                if (onGo != null) PillAction("Show me", true, onGo, style = PillStyle.Glass, modifier = Modifier.testTag("show-me"))
            }
        }
    }
}

// ------------------------------------------------------------------ business cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessCard(b: BusinessUi, fmt: (Money) -> String, onOpen: () -> Unit, onBuy: () -> Unit, onCollect: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.named(b.icon), highlighted = b.managerName != null)
            Spacer(Modifier.width(Space.md))
            // At large font sizes the income moves under the name so long names never break mid-word.
            val stacked = LocalDensity.current.fontScale > 1.35f
            val rateText = MoneyFormat.perSecond(b.netRate.perSecond())
            Column(Modifier.weight(1f)) {
                Text(b.name, style = Aurum.type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${b.tierName} · Lv ${b.level}", style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stacked) Text(if (b.mothballed) "Mothballed" else rateText, style = Aurum.type.number, color = if (b.mothballed) c.textTertiary else c.textPrimary)
            }
            if (!stacked) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(rateText, style = Aurum.type.number, color = if (b.mothballed) c.textTertiary else c.textPrimary)
                    Text(if (b.mothballed) "mothballed" else "per second", style = Aurum.type.label, color = c.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        GoldProgress(b.tierProgress, height = 4.dp, label = "Progress to next tier")
        Spacer(Modifier.height(Space.xs))
        Text(
            b.nextTierLevel?.let { "Next tier at level $it" } ?: "Top tier reached",
            style = Aurum.type.label, color = c.textTertiary,
        )
        b.bestValue?.let {
            Spacer(Modifier.height(Space.sm))
            AurumChip(it, icon = AbtIcons.StarFilled, tint = c.goldText, modifier = Modifier.testTag("best-value"))
        }
        b.hint?.let {
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Sparkle, null, tint = c.warning, size = 14.dp)
                Spacer(Modifier.width(Space.xs))
                Text(it, style = Aurum.type.bodySmall, color = c.warning)
            }
        }
        b.timer?.let { t ->
            Spacer(Modifier.height(Space.sm))
            AurumChip("${t.label} · building", icon = AbtIcons.Timer, tint = c.info)
        }
        Spacer(Modifier.height(Space.md))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (b.managerName == null) {
                Column(Modifier.weight(1f).widthIn(min = 96.dp)) {
                    val frac = if (b.storageCap.isPositive) b.stored.ratioTo(b.storageCap).toFloat() else 0f
                    GoldProgress(frac, height = 4.dp, brush = Brush.horizontalGradient(listOf(c.gain.copy(alpha = 0.7f), c.gain)), label = "Till ${(frac * 100).toInt()} percent full")
                    Spacer(Modifier.height(Space.xs))
                    Text(if (frac >= 1f) "Full · ${fmt(b.stored)}" else "Till ${fmt(b.stored)}", style = Aurum.type.label, color = if (frac >= 1f) c.gain else c.textTertiary, maxLines = 1)
                }
                SmallAction("Collect", enabled = b.stored.isPositive, onClick = onCollect, gold = false)
            } else {
                AurumChip("Auto · ${shortPersonName(b.managerName)}", icon = AbtIcons.Manager, tint = c.goldText)
                Spacer(Modifier.weight(1f))
            }
            SmallAction("Buy ×${b.buyCount} · ${fmt(b.buyCost)}", enabled = b.canAfford, onClick = onBuy, gold = true)
        }
    }
}

@Composable
internal fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit, gold: Boolean) =
    PillAction(text, enabled, onClick, style = if (gold) PillStyle.Gold else PillStyle.Positive)

@Composable
internal fun OpenableCard(o: OpenableUi, fmt: (Money) -> String, onOpen: () -> Unit) {
    val c = Aurum.colors
    val locked = o.lockedReason != null
    AurumCard(Modifier.fillMaxWidth().alpha(if (locked) 0.7f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (locked) AbtIcons.Lock else AbtIcons.named(o.icon), tint = if (locked) c.textTertiary else c.goldText)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = Aurum.type.title, color = c.textPrimary)
                Text(o.tagline, style = Aurum.type.bodySmall, color = c.textSecondary)
                o.lockedReason?.let { Text(it, style = Aurum.type.label, color = c.textTertiary) }
            }
            if (!locked) {
                Spacer(Modifier.width(Space.sm))
                SmallAction(if (o.cost.isZero) "Open · Free" else "Open · ${fmt(o.cost)}", enabled = o.canAfford, onClick = onOpen, gold = true)
            }
        }
    }
}

@Composable
internal fun HqCard(hq: HqUi, fmt: (Money) -> String, onUpgrade: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.Empire)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(hq.name, style = Aurum.type.title, color = c.textPrimary)
                Text("Level ${hq.level} · ${hq.slots} slots · ${"%.1f".format(hq.offlineCapHours).removeSuffix(".0")}h offline earnings", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        if (hq.nextName != null && hq.nextCost != null) {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next: ${hq.nextName}", style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                SmallAction("Upgrade · ${fmt(hq.nextCost)}", enabled = hq.canAfford, onClick = onUpgrade, gold = true)
            }
        }
    }
}

// ------------------------------------------------------------------ moments

private data class Moment(val title: String, val body: String)

/** Short, celebratory banner for milestones (tier reached, mission complete, chapter complete). */
@Composable
internal fun MomentOverlay(vm: GameViewModel, modifier: Modifier) {
    val c = Aurum.colors
    var moment by remember { mutableStateOf<Moment?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val m = when (ev) {
                is GameEvent.TierReached -> Moment("New tier: ${ev.tierName}", "Output multiplied at level ${ev.level}.")
                is GameEvent.MissionCompleted -> Moment("Goal complete", "Claim your reward on the Next Move card.")
                is GameEvent.ChapterCompleted -> Moment("Chapter ${ev.chapter} complete", "A new chapter of your story begins.")
                is GameEvent.ExpansionCompleted -> Moment("Expansion complete", "Your business just got bigger.")
                is GameEvent.ManagerHired -> Moment("Manager hired", "This business now runs itself, even offline.")
                is GameEvent.ConglomerateFormed -> Moment("Conglomerate formed", "Its members now share a lasting synergy.")
                is GameEvent.HoldingFormed -> Moment("${ev.name} is born", "Every business you own now earns more.")
                is GameEvent.ShortCovered -> if (ev.forced) Moment("Short position closed", "The price rose too far, so your broker covered it with your collateral.") else null
                is GameEvent.AuctionWon -> Moment("Sold to you!", "The hammer fell in your favour. Find it in your showroom.")
                is GameEvent.AuctionLost -> Moment("Outbid", if (ev.winner.isNotEmpty()) "${ev.winner} took the lot this time." else "The lot went to another bidder.")
                is GameEvent.Foreclosure -> Moment("Property repossessed", "The mortgage outgrew the property's value, so the bank sold it.")
                is GameEvent.TowerCompleted -> Moment("Skyscraper complete", "Floors lease one an hour, and the district's prices just rose.")
                is GameEvent.Disaster -> if (!ev.insured) Moment("Disaster damage", "One of your properties was damaged. Repair it on the Estates tab.") else null
                is GameEvent.EarningsSeason -> Moment("Earnings season", "${ev.beats} companies beat estimates, ${ev.misses} missed. Check the Markets tab.")
                else -> null
            }
            if (m != null) {
                moment = m
                delay(2_400)
                if (moment == m) moment = null
            }
        }
    }
    AnimatedVisibility(
        visible = moment != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding().padding(top = Space.sm, start = Space.gutter, end = Space.gutter),
    ) {
        val m = moment ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .glass(AurumShapes.large, GlassWeight.Thick)
                .padding(Space.md)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(AbtIcons.Sparkle, size = 40.dp, highlighted = true)
            Spacer(Modifier.width(Space.md))
            Column {
                Text(m.title, style = Aurum.type.title, color = c.textPrimary)
                Text(m.body, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
    }
}
