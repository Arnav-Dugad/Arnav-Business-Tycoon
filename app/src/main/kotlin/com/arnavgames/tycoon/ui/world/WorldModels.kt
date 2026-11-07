package com.arnavgames.tycoon.ui.world

import com.arnavgames.tycoon.core.content.RivalDef
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

data class RivalChipUi(val id: String, val name: String, val portrait: String?, val archetype: String)

data class OutcomeUi(val percent: Int, val text: String)

data class CardOptionUi(
    val index: Int, val label: String, val detail: String, val outcomes: List<OutcomeUi>, val maxCost: Money,
    val enabled: Boolean, val reason: String?,
)

data class CardUi(
    val id: Long, val kind: String, val title: String, val text: String, val businessName: String?, val rival: RivalChipUi?,
    val options: List<CardOptionUi>, val pressure: String?,
)

data class ModifierUi(val title: String, val scope: String, val pct: Double, val endsInMs: Long)

data class LogUi(val title: String, val text: String, val good: Boolean?, val agoMs: Long)

data class DeskUi(
    val unlocked: Boolean, val phase: String, val centralRate: Double, val cards: List<CardUi>, val modifiers: List<ModifierUi>,
    val log: List<LogUi>, val nextCardInMs: Long?, val cash: Money, val maxQueued: Int = 3,
)

data class FortuneRowUi(
    val rank: Int, val id: String?, val name: String, val netWorth: Money, val archetype: String, val city: String, val portrait: String?,
    val isPlayer: Boolean, val isNemesis: Boolean, val relation: Int,
)

data class FortuneUi(val unlocked: Boolean, val unlockChapter: Int, val playerRank: Int, val total: Int, val rows: List<FortuneRowUi>, val nemesis: FortuneRowUi?)

data class RivalUi(
    val id: String, val name: String, val archetype: String, val archetypeText: String, val age: Int, val city: String, val source: String,
    val sectors: List<String>, val netWorth: Money, val rank: Int, val relation: Int, val portrait: String?, val nemesisChapter: Int?,
    val history: List<Double>, val gapLog10: Double,
)

data class LoanUi(val id: Long, val principal: Money, val installment: Money, val paymentsLeft: Int, val ratePerDay: Double)

data class TaxFilingUi(
    val quarter: Long, val income: Money, val withheld: Money, val deductions: List<Pair<String, Double>>, val legitShare: Double,
    val refund: Money, val aggressiveRefund: Money, val aggressiveExtra: Double, val auditChance: Double, val dueInMs: Long,
)

data class FinanceUi(
    val score: Int, val rating: String, val nextRating: String?, val nextRatingAt: Int?, val maxLtv: Double, val loansUnlocked: Boolean,
    val loansChapter: Int, val borrowLimit: Money, val creditLineLimit: Money, val creditLine: Money, val loans: List<LoanUi>,
    val debtService6h: Money, val hourlyIncome: Money, val loanRate: Double, val terms: List<Int>, val taxesUnlocked: Boolean,
    val taxesChapter: Int, val taxRate: Double, val quarter: Long, val quarterEndsInMs: Long?, val pending: TaxFilingUi?,
    val autoFile: Boolean, val lastResult: String?, val canRestructure: Boolean, val phoenix: Boolean, val negativeForMs: Long?,
    val onTime: Long, val missed: Long, val audits: Int, val cash: Money, val totalDebt: Money,
)

/** Maps engine state to the Desk, Fortune List, rival and Finance screens (docs/GDD.md §9–11). */
class WorldMapper(private val engine: GameEngine, private val portraitIds: Set<String>) {
    private val w = engine.content.world
    private val math = engine.world
    private val credit = engine.credit

    fun portrait(id: String): String? = if (id in portraitIds) "img/rivals/$id.webp" else null

    private fun chip(r: RivalDef) = RivalChipUi(r.id, r.name, portrait(r.id), w.archetypeById[r.archetype]?.name ?: r.archetype)

    /** Plain-language effects: "Costs $1.2M (half an hour of income) · tax-deductible", "+4% Retail Stores income for a day". */
    fun describe(effects: List<com.arnavgames.tycoon.core.content.CardEffectDef>, s: GameState, cs: com.arnavgames.tycoon.core.state.CardState): List<String> {
        val hourly = engine.desk.hourly(s)
        fun money(h: Double) = com.arnavgames.tycoon.core.money.MoneyFormat.compact(hourly * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(kotlin.math.abs(h)))
        val biz = cs.business?.let { engine.content.businessById[it]?.name } ?: "your business"
        val rival = cs.rival?.let { w.rivalById[it]?.name } ?: "the rival"
        return effects.map { e ->
            when (e.type) {
                // Costs show the amount on the right of the option, so the text says it in time instead.
                "cash" -> if (e.hours >= 0) "Gain ${money(e.hours)} (${hoursText(e.hours)})" else "Costs ${hoursText(e.hours)}"
                "donation" -> "Donates ${hoursText(e.hours)} · tax-deductible"
                "rd" -> "Invests ${hoursText(e.hours)} in research · R&D tax credit"
                "income" -> {
                    val who = when (e.target) { "all" -> "every business"; "business" -> biz; else -> e.target }
                    val sign = if (e.pct >= 0) "+" else "−"
                    "$sign${kotlin.math.round(kotlin.math.abs(e.pct) * 100).toInt()}% income at $who for ${durationText(e.hours)}"
                }
                "credit" -> "${if (e.points >= 0) "+" else "−"}${kotlin.math.abs(e.points)} credit score"
                "relation" -> "${if (e.points >= 0) "+" else "−"}${kotlin.math.abs(e.points)} relationship with $rival"
                "sell" -> "Sell $biz for ${trimDouble(e.multiple)}× its value"
                else -> ""
            }
        }.filter { it.isNotBlank() }
    }

    private fun trimDouble(d: Double) = if (d == kotlin.math.floor(d)) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)

    /** 0.5 → "half an hour of income", 0.25 → "15 minutes of income", 2 → "2 hours of income". */
    private fun hoursText(h: Double): String {
        val a = kotlin.math.abs(h)
        return when {
            a < 1.0 && a == 0.5 -> "half an hour of income"
            a < 1.0 -> "${kotlin.math.round(a * 60).toInt()} minutes of income"
            a == 1.0 -> "1 hour of income"
            else -> "${trimDouble(a)} hours of income"
        }
    }

    /** 24 → "a day", 48 → "2 days", 6 → "6 hours". */
    private fun durationText(hours: Double): String = when {
        hours >= 24 && hours % 24 == 0.0 -> if (hours == 24.0) "a day" else "${(hours / 24).toInt()} days"
        hours == 1.0 -> "an hour"
        else -> "${trimDouble(hours)} hours"
    }

    fun desk(s: GameState): DeskUi {
        val desk = engine.desk
        val cards = s.world.cards.sortedBy { it.id }.mapNotNull { cs ->
            val def = w.cardById[cs.card] ?: return@mapNotNull null
            // An option that only costs money is really buying its way out of the others' income hit: say so.
            fun hurtsIncome(o: com.arnavgames.tycoon.core.content.CardOptionDef) = o.outcomes.any { oc -> oc.effects.any { it.type == "income" && it.pct < 0 } }
            fun touchesIncome(o: com.arnavgames.tycoon.core.content.CardOptionDef) = o.outcomes.any { oc -> oc.effects.any { it.type == "income" } }
            val othersHurt = def.options.count(::hurtsIncome)
            val options = def.options.mapIndexed { i, o ->
                val cost = desk.maxCost(s, o)
                val ally = desk.optionAvailable(s, cs, o)
                // Say what each choice really does, from its effects (always complete, in plain words).
                // Effects every outcome shares (usually the cost) are said once; each outcome lists only what differs.
                val perOutcome = o.outcomes.map { describe(it.effects, s, cs) }
                val common = perOutcome.firstOrNull().orEmpty().filter { phrase -> perOutcome.all { phrase in it } }
                val avoids = if (othersHurt > 0 && !touchesIncome(o) && o.outcomes.size == 1) listOf("no income hit") else emptyList()
                val detail = when {
                    o.outcomes.size == 1 -> (common + avoids).ifEmpty { listOf("Nothing changes.") }.joinToString(" · ").replaceFirstChar { it.uppercase() }
                    common.isNotEmpty() -> common.joinToString(" · ")
                    else -> ""
                }
                CardOptionUi(
                    i, desk.fill(o.label, s, cs), detail,
                    if (o.outcomes.size == 1) emptyList() else o.outcomes.mapIndexed { k, oc ->
                        val fx = perOutcome[k].filter { it !in common }.joinToString(" · ")
                        OutcomeUi((oc.p * 100).toInt(), listOf(desk.fill(oc.text, s, cs), fx).filter { it.isNotBlank() }.joinToString(" — "))
                    },
                    cost, ally && cost <= s.cash,
                    when { !ally -> "Needs an ally (relationship ${w.config.cards.allyRelation}+)"; cost > s.cash -> "Not enough cash"; else -> null },
                )
            }
            CardUi(
                cs.id, def.kind, desk.fill(def.title, s, cs), desk.fill(def.text, s, cs), cs.business?.let { engine.content.businessById[it]?.name },
                cs.rival?.let { w.rivalById[it] }?.let(::chip), options,
                if (def.kind == "takeover") "${engine.content.businessById[cs.business]?.name ?: "This business"} earns ${(w.config.cards.takeoverPenalty * 100).toInt()}% less until you respond." else null,
            )
        }
        val mods = s.world.modifiers.filter { it.untilSim > s.simTimeMs }.sortedBy { it.untilSim }.map { m ->
            val scope = when {
                m.target == "all" -> "All businesses"
                m.target.startsWith("business:") -> engine.content.businessById[m.target.removePrefix("business:")]?.name ?: "A business"
                else -> (engine.content.markets.sectors.firstOrNull { it.id == m.target.removePrefix("sector:") }?.name ?: "Sector") + " businesses"
            }
            ModifierUi(m.title, scope, m.pct, m.untilSim - s.simTimeMs)
        }
        val log = s.world.log.reversed().map { LogUi(it.title, it.text, it.good, s.simTimeMs - it.time) }
        return DeskUi(
            desk.cardsUnlocked(s), engine.market.phaseName(s), engine.economy.centralRate(s), cards, mods, log,
            if (desk.cardsUnlocked(s) && s.world.nextCardAt > s.simTimeMs) s.world.nextCardAt - s.simTimeMs else null, s.cash, desk.maxQueued(s),
        )
    }

    fun fortune(s: GameState, limit: Int = 250): FortuneUi {
        val nw = engine.economy.netWorth(s)
        val list = math.fortuneList(s, nw)
        val nemesisId = w.nemesisByChapter[s.progress.chapter]?.rival
        val rows = list.take(limit).map { e ->
            val rid = e.rivalId
            if (rid == null) FortuneRowUi(e.rank, null, s.holdingName ?: "You", nw, "Founder", "", null, true, false, 0)
            else {
                val r = w.rivalById.getValue(rid)
                FortuneRowUi(e.rank, r.id, r.name, math.rivalNetWorth(s, r), w.archetypeById[r.archetype]?.name ?: "", r.city, portrait(r.id),
                    false, r.id == nemesisId, math.relation(s, r.id))
            }
        }
        val playerRank = list.first { it.rivalId == null }.rank
        return FortuneUi(math.isUnlocked(s, "fortune"), math.unlockChapter("fortune"), playerRank, list.size, rows, rows.firstOrNull { it.isNemesis })
    }

    fun rival(s: GameState, id: String): RivalUi? {
        val r = w.rivalById[id] ?: return null
        val nw = engine.economy.netWorth(s)
        val day = 86_400_000L
        val history = (0..28).map { i -> math.rivalLog10(s, r, (s.simTimeMs - (28 - i) * day / 4).coerceAtLeast(0)) }
        val rank = math.fortuneList(s, nw).first { it.rivalId == id }.rank
        return RivalUi(
            r.id, r.name, w.archetypeById[r.archetype]?.name ?: r.archetype, w.archetypeById[r.archetype]?.description ?: "", r.age, r.city, r.source,
            r.sectors.map { sid -> engine.content.markets.sectors.firstOrNull { it.id == sid }?.name ?: sid }, math.rivalNetWorth(s, r), rank,
            math.relation(s, id), portrait(id), w.nemeses.firstOrNull { it.rival == id }?.chapter, history,
            math.rivalLog10(s, r) - com.arnavgames.tycoon.core.world.WorldMath.log10(nw),
        )
    }

    fun finance(s: GameState): FinanceUi {
        val cfg = w.config.finance
        val score = credit.score(s)
        val rating = credit.rating(s)
        val better = cfg.ratings.lastOrNull { it.min > score }
        val t = s.finance.tax
        val qEnds = if (t.startMinute >= 0) {
            val q = cfg.quarterHours * 60L
            val minute = s.simTimeMs / 60_000
            ((t.startMinute + ((minute - t.startMinute) / q + 1) * q) * 60_000 - s.simTimeMs)
        } else null
        val pending = t.pending?.let { f ->
            val q = engine.finance.quote(f)
            TaxFilingUi(f.quarter, f.income, f.withheld, f.deductions.toList(), q.legit, q.refund, q.aggressiveRefund, f.aggressiveExtra, f.auditChance, f.dueSim - s.simTimeMs)
        }
        return FinanceUi(
            score, rating.name, better?.name, better?.min, credit.maxLtv(s), math.isUnlocked(s, "loans"), math.unlockChapter("loans"),
            credit.borrowLimit(s), credit.creditLineLimit(s), s.finance.creditLine,
            s.finance.loans.map { LoanUi(it.id, it.principal, it.installment, it.paymentsLeft, it.ratePerDay) },
            credit.debtService6h(s), credit.hourlyIncome(s), credit.loanRatePerDay(s), cfg.loanTermsDays,
            math.isUnlocked(s, "taxes"), math.unlockChapter("taxes"), t.rate, t.quarter, qEnds, pending, t.autoFile, t.lastResult,
            credit.canRestructure(s), credit.phoenixAvailable(s),
            s.finance.negativeSince.takeIf { it >= 0 }?.let { s.simTimeMs - it },
            s.finance.onTimePayments, s.finance.missedPayments, s.finance.audits, s.cash, credit.totalDebt(s),
        )
    }

    companion object {
        val DEDUCTION_NAMES = mapOf("rd" to "R&D credit", "depreciation" to "Property depreciation", "donations" to "Charitable donations", "green" to "Green-energy credit")
    }
}
