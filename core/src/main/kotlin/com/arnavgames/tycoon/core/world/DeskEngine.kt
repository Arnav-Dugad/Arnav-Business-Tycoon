package com.arnavgames.tycoon.core.world

import com.arnavgames.tycoon.core.content.CardDef
import com.arnavgames.tycoon.core.content.CardOptionDef
import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.RivalDef
import com.arnavgames.tycoon.core.engine.Economy
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.finance.FinanceEngine
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.market.MarketEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.random.Pcg32
import com.arnavgames.tycoon.core.state.CardState
import com.arnavgames.tycoon.core.state.DeskEntry
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.ModifierState
import com.arnavgames.tycoon.core.state.TimerKind

/**
 * The Desk (docs/GDD.md §9–10): decision cards with visible odds (never resolved automatically), world and sector
 * events, disasters, rival offers and takeovers, and the chapter nemesis story beats. All draws come from seeded
 * streams keyed by a counter, on minute ticks, so offline catch-up equals live play.
 */
class DeskEngine(private val content: ContentPack, private val economy: Economy, private val finance: FinanceEngine, private val market: MarketEngine) {
    private val w get() = content.world
    private val cc get() = w.config.cards
    private val ec get() = w.config.events
    private val math get() = economy.world
    private val sellCards: Set<String> = w.cards.filter { c -> c.options.any { o -> o.outcomes.any { oc -> oc.effects.any { it.type == "sell" } } } }.map { it.id }.toSet()

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    fun cardsUnlocked(s: GameState) = math.isUnlocked(s, "cards") && (s.progress.onboardingStep >= 5 || s.progress.chapter >= 2) && !economy.dynasty.forbids(s, "noCards")

    // =====================================================================================
    // Tick
    // =====================================================================================

    fun tick(state: GameState, minute: Long, events: MutableList<GameEvent>): GameState {
        var s = state
        val now = s.simTimeMs
        if (cardsUnlocked(s)) {
            if (s.world.nextCardAt == 0L) s = s.copy(world = s.world.copy(nextCardAt = maxOf(now + 60_000, cc.firstAtMinutes * 60_000L)))
            else if (now >= s.world.nextCardAt) {
                val r = GameRandom.stream(s.worldSeed, "card", s.world.cardDraws)
                if (s.world.cards.size < cc.maxQueued) s = draw(s, r, events)
                val gap = (cc.everyHours * r.nextDouble(cc.jitterMin, cc.jitterMax) * 3_600_000).toLong()
                s = s.copy(world = s.world.copy(nextCardAt = now + gap, cardDraws = s.world.cardDraws + 1))
            }
        }
        if (math.isUnlocked(s, "events")) {
            if (s.world.nextEventAt == 0L) s = s.copy(world = s.world.copy(nextEventAt = now + (ec.firstAfterHours * 3_600_000).toLong()))
            else if (now >= s.world.nextEventAt) {
                val r = GameRandom.stream(s.worldSeed, "world_event", s.world.eventDraws)
                s = worldEvent(s, r, events)
                val gap = (r.nextDouble(ec.everyHoursMin, ec.everyHoursMax) * 3_600_000).toLong()
                s = s.copy(world = s.world.copy(nextEventAt = now + gap, eventDraws = s.world.eventDraws + 1))
            }
        }
        if (minute % 5 == 0L) s = nemesisBeats(s, events)
        return s
    }

    private fun log(s: GameState, title: String, text: String, good: Boolean?): GameState =
        s.copy(world = s.world.copy(log = (s.world.log + DeskEntry(s.simTimeMs, title, text, good)).takeLast(30)))

    private fun eligibleBusinesses(s: GameState, card: CardDef): List<String> {
        val owned = s.businesses.values.filter { !it.mothballed }.map { it.id }.sorted()
        val base = when {
            card.requires == "biz:any" -> owned
            card.requires.startsWith("biz:") -> owned.filter { it == card.requires.removePrefix("biz:") }
            else -> return listOf("")
        }
        if (card.id !in sellCards) return base
        // A business can be sold only if it is not part of a conglomerate, and never the player's last one.
        if (owned.size < 2) return emptyList()
        return base.filter { economy.conglomerateOf(s, it) == null }
    }

    private fun kindUnlocked(s: GameState, kind: String) = when (kind) {
        "acquisition", "jv" -> math.isUnlocked(s, "acquisitions")
        "takeover" -> math.isUnlocked(s, "takeovers")
        "rival" -> math.isUnlocked(s, "fortune")
        "nemesis" -> false
        else -> true
    }

    private fun draw(s: GameState, r: Pcg32, events: MutableList<GameEvent>): GameState {
        val recent = s.world.recentCards.toSet()
        val pending = s.world.cards.map { it.card }.toSet()
        val pool = w.cards.filter { c ->
            c.weight > 0 && c.chapter <= s.progress.chapter && kindUnlocked(s, c.kind) && c.id !in recent && c.id !in pending &&
                eligibleBusinesses(s, c).isNotEmpty()
        }
        if (pool.isEmpty()) return s
        val total = pool.sumOf { it.weight }
        var x = r.nextDouble() * total
        val card = pool.firstOrNull { x -= it.weight; x < 0 } ?: pool.last()
        val biz = eligibleBusinesses(s, card).let { it[r.nextInt(it.size)] }.ifEmpty { null }
        val rival = if (card.text.contains("{rival}") || card.kind in RIVAL_KINDS) pickRival(s, card, biz, r) else null
        val cs = CardState(s.world.nextCardId, card.id, biz, rival?.id, s.simTimeMs)
        events += GameEvent.CardDrawn(cs.id)
        return s.copy(world = s.world.copy(
            cards = s.world.cards + cs, nextCardId = cs.id + 1,
            recentCards = (s.world.recentCards + card.id).takeLast(cc.recentWindow),
        ))
    }

    /** Hostile cards prefer aggressive rivals in the business's sectors; friendly ones prefer gentle rivals. Rivals with portraits come up more often. */
    private fun pickRival(s: GameState, card: CardDef, biz: String?, r: Pcg32): RivalDef {
        val sectors = biz?.let { math.sectorsOf(it) } ?: emptySet()
        val hostile = card.kind in setOf("takeover", "acquisition")
        val weights = w.rivals.map { rv ->
            var wt = if (hostile) 0.2 + rv.aggression else 1.2 - rv.aggression
            if (sectors.isNotEmpty() && rv.sectors.any { it in sectors }) wt *= 3
            if (rv.portrait) wt *= 2.5
            wt
        }
        var x = r.nextDouble() * weights.sum()
        for (i in w.rivals.indices) { x -= weights[i]; if (x < 0) return w.rivals[i] }
        return w.rivals.last()
    }

    private fun worldEvent(s0: GameState, r: Pcg32, events: MutableList<GameEvent>): GameState {
        var s = s0
        val phase = s.market.macro.phase
        val activeSources = s.world.modifiers.map { it.source }.toSet()
        val activeEvents = s.world.modifiers.count { it.source.startsWith("event:") }
        val citiesOpen = content.estates.cities.filter { economy.estate.cityUnlocked(s, it.id) }.map { it.id }.toSet()
        val pool = w.events.filter { e ->
            "event:${e.id}" !in activeSources && (e.scope != "city" || e.target in citiesOpen) && (e.scope == "city" || activeEvents < ec.maxActive)
        }
        if (pool.isEmpty()) return s
        val weights = pool.map { it.weights[phase] ?: 1.0 }
        var x = r.nextDouble() * weights.sum()
        val e = pool.indices.firstOrNull { x -= weights[it]; x < 0 }?.let { pool[it] } ?: pool.last()
        return applyEvent(s, e, r, events)
    }

    /** Applies a world event: a timed income modifier, or (disasters) damage to uninsured properties in the city. */
    fun applyEvent(s0: GameState, e: com.arnavgames.tycoon.core.content.WorldEventDef, r: Pcg32, events: MutableList<GameEvent>): GameState {
        var s = s0
        events += GameEvent.WorldEventStarted(e.id)
        if (e.scope == "city") {
            var hit = 0
            val props = s.estate.properties.toMutableMap()
            for (p in props.values.filter { content.estates.districtById[it.district]?.city == e.target }.sortedBy { it.id }) {
                if (!p.insured) props[p.id] = p.copy(condition = (p.condition - r.nextInt(ec.disasterDamageMin, ec.disasterDamageMax + 1)).coerceAtLeast(200))
                events += GameEvent.Disaster(p.id, p.insured)
                hit++
            }
            s = s.copy(estate = s.estate.copy(properties = props))
            val city = content.estates.cityById[e.target]?.name ?: e.target
            val text = e.text + if (hit > 0) " $hit of your properties in $city were hit; insured ones are repaired by the insurer." else " None of your properties were affected."
            s = log(s, e.title, text, if (hit > 0) false else null)
            return market.postNews(s, e.text, "world", e.target)
        }
        val target = if (e.scope == "all") "all" else "sector:${e.target}"
        val mod = ModifierState("event:${e.id}", target, e.pct, s.simTimeMs + e.hours * 3_600_000L, e.title)
        s = s.copy(world = s.world.copy(modifiers = s.world.modifiers + mod))
        val who = if (e.scope == "all") "all businesses" else (content.markets.sectors.firstOrNull { it.id == e.target }?.name ?: e.target).lowercase() + " businesses"
        s = log(s, e.title, "${e.text} Income of $who ${if (e.pct > 0) "+" else ""}${(e.pct * 100).toInt()}% for ${e.hours} h.", e.pct > 0)
        return market.postNews(s, e.text, "world", if (e.scope == "sector") e.target else "")
    }

    private fun nemesisBeats(s0: GameState, events: MutableList<GameEvent>): GameState {
        if (!math.isUnlocked(s0, "fortune") && s0.progress.chapter < 2) return s0
        var s = s0
        for (n in w.nemeses) {
            val rival = w.rivalById[n.rival] ?: continue
            val ch = s.progress.chapter
            fun done(b: Int) = "${n.chapter}:$b" in s.world.beats
            fun mark(b: Int) { s = s.copy(world = s.world.copy(beats = s.world.beats + "${n.chapter}:$b")); events += GameEvent.NemesisBeat(n.chapter, b) }
            if (n.chapter == ch && !done(0) && s.world.cards.size < cc.maxQueued) {
                val cs = CardState(s.world.nextCardId, "nemesis_${n.chapter}", null, rival.id, s.simTimeMs)
                s = s.copy(world = s.world.copy(cards = s.world.cards + cs, nextCardId = cs.id + 1))
                mark(0)
                s = market.postNews(s, n.beats[0], "rival", rival.id)
            }
            if (n.chapter == ch && done(0) && !done(1)) {
                val missions = content.chapters.firstOrNull { it.number == ch }?.missions ?: emptyList()
                if (missions.isNotEmpty() && missions.count { it.id in s.progress.completedMissions } * 2 >= missions.size) {
                    mark(1)
                    s = log(s, "Nemesis: ${rival.name}", n.beats[1], true)
                    s = market.postNews(s, n.beats[1], "rival", rival.id)
                }
            }
            if (n.chapter < ch && !done(2)) {
                mark(2)
                // The chapter is over: its intro card, if still waiting, has nothing left to say.
                s = s.copy(world = s.world.copy(cards = s.world.cards.filterNot { it.card == "nemesis_${n.chapter}" }))
                s = s.copy(world = s.world.copy(relations = s.world.relations + (rival.id to ((s.world.relations[rival.id] ?: 0) - 10).coerceIn(-100, 100))))
                s = log(s, "Nemesis defeated: ${rival.name}", n.beats[2], true)
                s = market.postNews(s, n.beats[2], "rival", rival.id)
            }
        }
        return s
    }

    // =====================================================================================
    // Resolving cards
    // =====================================================================================

    fun hourly(s: GameState): Money = economy.totalNetRate(s).over(3600)

    /** Business valuation used for sale offers: what it adds to net worth. */
    fun valuation(s: GameState, businessId: String): Money {
        val b = s.businesses[businessId] ?: return Money.ZERO
        return economy.investedIn(content.business(businessId), b.level) * Multiplier.of("0.6") + b.stored
    }

    fun optionAvailable(s: GameState, card: CardState, option: CardOptionDef): Boolean =
        option.requires != "ally" || math.hasAlly(s, except = card.rival)

    /** The most cash an option could cost (all outcomes considered), so the player can always afford what they pick. */
    fun maxCost(s: GameState, option: CardOptionDef): Money {
        val h = hourly(s)
        return option.outcomes.maxOfOrNull { oc ->
            oc.effects.filter { (it.type == "cash" && it.hours < 0) || it.type == "donation" || it.type == "rd" }
                .fold(Money.ZERO) { a, e -> a + h * Multiplier.ofDouble(kotlin.math.abs(e.hours)) }
        } ?: Money.ZERO
    }

    fun fill(text: String, s: GameState, card: CardState): String {
        val biz = card.business?.let { content.businessById[it]?.name } ?: "your business"
        val rival = card.rival?.let { w.rivalById[it]?.name } ?: "a rival"
        fun possessive(name: String) = if (name.endsWith("s")) "$name'" else "$name's"
        return text.replace("{business}'s", possessive(biz)).replace("{rival}'s", possessive(rival))
            .replace("{business}", biz).replace("{rival}", rival)
    }

    fun resolve(s0: GameState, cardId: Long, optionIndex: Int, events: MutableList<GameEvent>, edge: Double = 0.0): Result {
        if (edge !in -0.15..0.15) return Result.Err(GameError.Invalid("Negotiation edge out of range"))
        val card = s0.world.cards.firstOrNull { it.id == cardId } ?: return Result.Err(GameError.NotFound("card"))
        val def = w.cardById[card.card] ?: return Result.Err(GameError.NotFound("card definition"))
        val option = def.options.getOrNull(optionIndex) ?: return Result.Err(GameError.Invalid("No such option"))
        if (!optionAvailable(s0, card, option)) return Result.Err(GameError.Locked("You need an ally: a rival whose relationship with you is ${cc.allyRelation} or more"))
        val cost = maxCost(s0, option)
        if (cost > s0.cash) return Result.Err(GameError.InsufficientFunds(cost, s0.cash))
        if (card.business != null && card.business !in s0.businesses) {
            // The business is gone (sold or never re-opened): the card simply lapses.
            return Result.Ok(s0.copy(world = s0.world.copy(cards = s0.world.cards - card)))
        }
        val r = GameRandom.stream(s0.worldSeed, "card_resolve", card.id)
        var x = r.nextDouble()
        // A negotiation moves probability toward (or away from) the first outcome; the rest scale to fit.
        val ps = option.outcomes.map { it.p }.toMutableList()
        if (edge != 0.0 && ps.size > 1) {
            val first = (ps[0] + edge).coerceIn(0.0, 1.0)
            val restOld = 1.0 - ps[0]
            val restNew = 1.0 - first
            for (i in 1 until ps.size) ps[i] = if (restOld <= 0) restNew / (ps.size - 1) else ps[i] * restNew / restOld
            ps[0] = first
        }
        val idx = ps.indices.firstOrNull { x -= ps[it]; x < 0 } ?: ps.lastIndex
        val outcome = option.outcomes[idx]
        var s = s0.copy(world = s0.world.copy(cards = s0.world.cards - card))
        val h = hourly(s0)
        var good: Boolean? = null
        for (e in outcome.effects) {
            when (e.type) {
                "cash" -> {
                    val amt = h * Multiplier.ofDouble(kotlin.math.abs(e.hours))
                    if (!amt.isPositive) continue
                    s = if (e.hours > 0) {
                        good = good ?: true
                        post(s, TxnCategory.Income, def.title, listOf(Posting(Accounts.CASH, amt), Posting(Accounts.revenue("deals"), -amt)))
                    } else post(s, TxnCategory.Purchase, def.title, listOf(Posting(Accounts.CASH, -amt), Posting(Accounts.expense("decisions"), amt)))
                }
                "donation", "rd" -> {
                    val amt = h * Multiplier.ofDouble(e.hours)
                    if (!amt.isPositive) continue
                    s = post(s, TxnCategory.Purchase, def.title, listOf(Posting(Accounts.CASH, -amt), Posting(Accounts.expense(if (e.type == "rd") "research" else "donations"), amt)))
                    s = s.copy(world = if (e.type == "rd") s.world.copy(rdThisQuarter = s.world.rdThisQuarter + amt) else s.world.copy(donationsThisQuarter = s.world.donationsThisQuarter + amt))
                }
                "income" -> {
                    val target = when (e.target) {
                        "business" -> "business:${card.business}"
                        "sector" -> math.sectorsOf(card.business ?: "").firstOrNull()?.let { "sector:$it" } ?: "business:${card.business}"
                        else -> "all"
                    }
                    if (e.pct > 0) good = good ?: true else if (e.pct < 0) good = false
                    s = s.copy(world = s.world.copy(modifiers = s.world.modifiers + ModifierState("card:${def.id}:${card.id}", target, e.pct, s.simTimeMs + (e.hours * 3_600_000).toLong(), def.title)))
                }
                "credit" -> s = finance.applyScore(s, e.points)
                "relation" -> card.rival?.let { id ->
                    s = s.copy(world = s.world.copy(relations = s.world.relations + (id to ((s.world.relations[id] ?: 0) + e.points).coerceIn(-100, 100))))
                }
                "sell" -> s = sellBusiness(s, card.business!!, e.multiple, card.rival, events)
            }
        }
        val result = fill(outcome.text.ifEmpty { option.label + "." }, s0, card)
        events += GameEvent.CardResolved(card.id, fill(def.title, s0, card), result)
        return Result.Ok(log(s, fill(def.title, s0, card), "${fill(option.label, s0, card)}: $result", good))
    }

    private fun post(s: GameState, category: TxnCategory, memo: String, postings: List<Posting>): GameState =
        when (val r = Ledger.post(s.ledger, s.simTimeMs, category, memo, postings)) {
            is Ledger.Result.Ok -> s.copy(ledger = r.state)
            is Ledger.Result.Err -> when (r.error) { is LedgerError.InsufficientFunds -> s }
        }

    /** Sells a business to a rival at [multiple] × its valuation. Its property is freed and its timers cancelled. */
    fun sellBusiness(s: GameState, businessId: String, multiple: Double, rivalId: String?, events: MutableList<GameEvent>): GameState {
        val b = s.businesses[businessId] ?: return s
        val price = valuation(s, businessId) * Multiplier.ofDouble(multiple)
        val propertyId = s.estate.hosted[businessId]
        val props = propertyId?.let { pid -> s.estate.properties[pid]?.let { p -> s.estate.properties + (pid to p.copy(hostedBusiness = null)) } } ?: s.estate.properties
        val buyer = rivalId?.let { w.rivalById[it]?.name } ?: "A rival"
        var ns = post(s, TxnCategory.Income, "sale of ${content.business(businessId).name}", listOf(Posting(Accounts.CASH, price), Posting(Accounts.revenue("business_sales"), -price)))
        ns = ns.copy(
            businesses = ns.businesses - b.id,
            timers = ns.timers.filterNot { it.kind == TimerKind.Expansion && it.targetId == businessId },
            estate = ns.estate.copy(hosted = ns.estate.hosted - businessId, properties = props),
            world = ns.world.copy(
                businessesSold = ns.world.businessesSold + 1,
                cards = ns.world.cards.filterNot { it.business == businessId },
                modifiers = ns.world.modifiers.filterNot { it.target == "business:$businessId" },
            ),
        )
        events += GameEvent.BusinessSold(businessId, price)
        return market.postNews(ns, "$buyer buys ${content.business(businessId).name} from you.", "rival", rivalId ?: "")
    }

    companion object {
        val RIVAL_KINDS = setOf("acquisition", "jv", "takeover", "rival", "nemesis")
    }
}
