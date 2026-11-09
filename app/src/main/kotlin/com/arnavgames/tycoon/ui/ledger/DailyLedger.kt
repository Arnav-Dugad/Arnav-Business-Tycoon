package com.arnavgames.tycoon.ui.ledger

import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.state.GameState

/** How a story reads: good news, bad news that needs you, or just news. */
enum class Tone { Good, Bad, Neutral }

/** One item on the front page. [icon] names an AbtIcons entry. */
data class Story(val icon: String, val headline: String, val detail: String, val tone: Tone, val weight: Int, val image: String? = null)

/**
 * "The Daily Ledger" (docs/PLAN_2.0.md D1): what happened while the player was away, as a short front page built
 * from the offline period's real events and the before/after state. Pure, so it is unit-tested. At most five
 * stories: problems first, then wins, then the world, then markets, then what's waiting.
 */
object DailyLedger {
    const val MIN_ABSENCE_MS = 30 * 60_000L
    private const val MAX_STORIES = 5

    fun build(engine: GameEngine, before: GameState, after: GameState, events: List<GameEvent>): List<Story> {
        val c = engine.content
        val out = mutableListOf<Story>()
        fun money(m: Money) = MoneyFormat.compact(m)

        // Problems that need the player.
        events.filterIsInstance<GameEvent.PaymentMissed>().takeIf { it.isNotEmpty() }?.let { missed ->
            out += Story("Warning", if (missed.size == 1) "A payment was missed" else "${missed.size} payments were missed",
                "Your credit score took a hit. Open Finance to catch up or restructure.", Tone.Bad, 100)
        }
        events.filterIsInstance<GameEvent.Foreclosure>().takeIf { it.isNotEmpty() }?.let { f ->
            out += Story("House", if (f.size == 1) "A property was repossessed" else "${f.size} properties were repossessed",
                "Mortgage payments fell too far behind.", Tone.Bad, 95)
        }
        events.filterIsInstance<GameEvent.Disaster>().takeIf { it.isNotEmpty() }?.let { d ->
            val uninsured = d.count { !it.insured }
            val where = d.firstNotNullOfOrNull { e -> after.estate.properties[e.propertyId]?.let { p -> c.estates.districtById[p.district]?.name } }
            out += Story("Umbrella", "Storm damage${where?.let { " in $it" } ?: ""}",
                if (uninsured == 0) "Insurance covered everything." else "$uninsured uninsured ${if (uninsured == 1) "property" else "properties"} lost condition. Renovate to restore rent.",
                if (uninsured == 0) Tone.Neutral else Tone.Bad, 90)
        }

        // Wins.
        events.filterIsInstance<GameEvent.AuctionWon>().forEach { w ->
            val name = c.luxury.itemById[w.itemId]?.name ?: "the lot"
            out += Story("Gem", "Hammer falls: $name is yours", "Won for ${money(w.price)}. It's in your Collection.", Tone.Good, 80)
        }
        events.filterIsInstance<GameEvent.AuctionLost>().take(1).forEach { l ->
            val name = c.luxury.itemById[l.itemId]?.name ?: "the lot"
            out += Story("Gem", "Outbid on $name", "${l.winner} took it. New lots arrive every two hours.", Tone.Neutral, 40)
        }
        events.filterIsInstance<GameEvent.ExpansionCompleted>().takeIf { it.isNotEmpty() }?.let { done ->
            val first = done.first()
            val def = c.businessById[first.businessId]
            val expName = def?.expansions?.firstOrNull { it.id == first.expansionId }?.name ?: "An expansion"
            out += Story("Crane", if (done.size == 1) "$expName opened" else "${done.size} expansions finished",
                if (done.size == 1) "${def?.name ?: "Your business"} earns more from now on." else "Your businesses earn more from now on.", Tone.Good, 70)
        }
        val goals = events.filterIsInstance<GameEvent.MissionCompleted>().mapNotNull { e -> c.chapters.flatMap { it.missions }.firstOrNull { it.id == e.missionId } }
        if (goals.isNotEmpty()) {
            out += Story("Trophy", if (goals.size == 1) "Goal reached: ${goals.first().title}" else "${goals.size} goals reached",
                "Claim the reward on the Next Move card.", Tone.Good, 75)
        }

        // The world.
        events.filterIsInstance<GameEvent.WorldEventStarted>().lastOrNull()?.let { e ->
            c.world.eventById[e.eventId]?.let { ev -> out += Story("Globe", ev.title, ev.text, Tone.Neutral, 50, image = "img/event/${ev.id}.webp") }
        }
        events.filterIsInstance<GameEvent.NemesisBeat>().lastOrNull()?.let { e ->
            val nem = c.world.nemesisByChapter[e.chapter]
            val rival = nem?.let { c.world.rivalById[it.rival]?.name } ?: "Your nemesis"
            val text = nem?.beats?.getOrNull(e.beat)?.replace("{rival}", rival)
            if (text != null) out += Story("Rivals", "$rival makes a move", text, Tone.Neutral, 55)
        }
        events.filterIsInstance<GameEvent.TaxFiled>().lastOrNull()?.let { t ->
            out += Story("Receipt", if (t.audited) "The tax office audited you" else "Taxes filed for the quarter",
                if (t.audited) "Check Finance for the result." else if (t.auto) "Filed automatically. Self-filing can save more." else "All done.", if (t.audited) Tone.Bad else Tone.Neutral, if (t.audited) 85 else 30)
        }

        // Markets: your portfolio's move, or the biggest headline.
        val pBefore = engine.market.math.portfolioValue(before)
        val pAfter = engine.market.math.portfolioValue(after)
        if (pBefore.isPositive && pAfter.isPositive) {
            val change = (pAfter - pBefore).ratioTo(pBefore)
            if (kotlin.math.abs(change) >= 0.02) {
                val pct = String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(change) * 100)
                out += Story("Markets", "Your portfolio ${if (change > 0) "rose" else "fell"} $pct%",
                    "Now worth ${money(pAfter)}.", if (change > 0) Tone.Good else Tone.Bad, 45)
            }
        }
        after.market.news.filter { n -> before.market.news.none { it.id == n.id } }.lastOrNull()?.let { n ->
            out += Story("News", n.headline, "From the market wire.", Tone.Neutral, 20)
        }

        // What's waiting.
        val waiting = after.world.cards.size
        if (waiting > 0) out += Story("Cards", if (waiting == 1) "A decision is waiting on your Desk" else "$waiting decisions are waiting on your Desk",
            "They never decide themselves. Open the Desk when you're ready.", Tone.Neutral, 60)
        val tenants = after.estate.events.size - before.estate.events.size
        if (tenants > 0) out += Story("Key", if (tenants == 1) "A tenant needs an answer" else "$tenants tenants need answers",
            "Rent is lower until you decide. See Estates.", Tone.Neutral, 58)

        return out.sortedByDescending { it.weight }.take(MAX_STORIES)
    }
}
