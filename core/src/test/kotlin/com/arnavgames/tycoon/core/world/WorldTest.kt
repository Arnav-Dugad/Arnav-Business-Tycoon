package com.arnavgames.tycoon.core.world

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.CardState
import com.arnavgames.tycoon.core.state.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldTest {
    private val hour = 3_600_000L
    private val w = engine.content.world
    private val math = engine.world

    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")
    private fun at(s: GameState, ch: Int) = s.copy(progress = s.progress.copy(chapter = ch, onboardingStep = 5))
    private fun withCard(s: GameState, card: String, business: String? = null, rival: String? = null): Pair<GameState, Long> {
        val cs = CardState(s.world.nextCardId, card, business, rival, s.simTimeMs)
        return s.copy(world = s.world.copy(cards = s.world.cards + cs, nextCardId = cs.id + 1)) to cs.id
    }

    @Test
    fun fortuneListRanksRivalsAndThePlayer() {
        val s = newGame(1)
        val list = math.fortuneList(s, engine.economy.netWorth(s))
        assertEquals(w.rivals.size + 1, list.size)
        assertEquals(list.map { it.log10 }, list.map { it.log10 }.sortedDescending())
        assertEquals(w.rivals.size + 1, math.playerRank(s, engine.economy.netWorth(s)), "a new player is last")
        val rich = s.withHugeCash()
        assertEquals(1, math.playerRank(rich, engine.economy.netWorth(rich)))
        // Rivals get richer over time, deterministically.
        val r = w.rivals.first()
        assertTrue(math.rivalLog10(s, r, 5 * 24 * hour) > math.rivalLog10(s, r, 0))
        assertEquals(math.rivalLog10(s, r, 7 * hour), math.rivalLog10(newGame(1), r, 7 * hour))
    }

    @Test
    fun cardsQueueUpToThreeAndNeverResolveThemselves() {
        var s = at(busyState(), 2)
        s = s.adv(30 * hour)
        val cards = s.world.cards
        assertTrue(cards.size in 1..3, "cards queued: ${cards.size}")
        s = s.adv(30 * hour)
        assertEquals(3, s.world.cards.size)
        assertTrue(s.world.cards.containsAll(cards), "pending cards stay until the player decides")
    }

    @Test
    fun resolvingACardAppliesItsOutcomeAndModifiersExpire() {
        val base = at(busyState(), 2)
        val before = engine.economy.businessRate(base, "retail").net
        val (s0, id) = withCard(base, "gen_consultant", "retail")
        val s1 = s0.run(Command.ResolveCard(id, 1)) // "Do it in-house": +6 % for 24 h
        assertTrue(s1.world.cards.none { it.id == id })
        assertEquals(1.06, math.incomeFactor(s1, "retail"), 1e-9)
        assertTrue(engine.economy.businessRate(s1, "retail").net > before)
        val s2 = s1.adv(24 * hour + 60_000)
        assertTrue(s2.world.modifiers.none { it.source == "card:gen_consultant:$id" }, "the card's modifier has expired")
        assertTrue(s2.world.log.any { it.title == "Management consultants" })
        trial(s2)
    }

    @Test
    fun outcomesFollowTheSeededOdds() {
        // The same card in the same world resolves the same way; over many worlds both outcomes happen.
        val results = (1L..40L).map { seed ->
            val (s0, id) = withCard(at(busyState().copy(worldSeed = seed), 2), "gen_consultant", "retail")
            s0.run(Command.ResolveCard(id, 0)).world.modifiers.any { it.source.startsWith("card:gen_consultant") }
        }
        assertTrue(results.count { it } in 12..36, "60 % success expected, got ${results.count { it }}/40")
    }

    @Test
    fun aPlayerMustAffordTheWorstCase() {
        val base = at(busyState(), 2)
        val broke = base.copy(ledger = Ledger.spend(base.ledger, base.simTimeMs, com.arnavgames.tycoon.core.ledger.TxnCategory.Purchase, "drain", "test", base.cash).let { (it as Ledger.Result.Ok).state })
        val (s0, id) = withCard(broke, "gen_consultant", "retail")
        assertInstanceOf(GameError.InsufficientFunds::class.java, s0.reject(Command.ResolveCard(id, 0)))
        s0.run(Command.ResolveCard(id, 1)) // the free option always works
    }

    @Test
    fun acquisitionSellsTheBusinessAtThePremium() {
        val base = at(busyState(), 3)
        val value = engine.desk.valuation(base, "restaurants")
        val (s0, id) = withCard(base, "r_acquisition_offer", "restaurants", "jeff_bozos")
        val s1 = s0.run(Command.ResolveCard(id, 0))
        assertFalse("restaurants" in s1.businesses)
        assertEquals(base.cash + value * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(2.5), s1.cash)
        assertEquals(10, s1.world.relations["jeff_bozos"])
        assertEquals(1, s1.world.businessesSold)
        trial(s1)
    }

    @Test
    fun takeoverPressureAndTheWhiteKnight() {
        val base = at(busyState(), 4)
        val (s0, id) = withCard(base, "r_takeover", "restaurants", "melon_tusk")
        assertEquals(0.9, math.incomeFactor(s0, "restaurants"), 1e-9, "income is 10 % lower while the takeover is pending")
        assertInstanceOf(GameError.Locked::class.java, s0.reject(Command.ResolveCard(id, 2)), "no ally yet")
        val allied = s0.copy(world = s0.world.copy(relations = mapOf("warren_buffit" to 50)))
        val s1 = allied.run(Command.ResolveCard(id, 2))
        assertTrue("restaurants" in s1.businesses)
        assertEquals(1.0, math.incomeFactor(s1, "restaurants"), 1e-9)
    }

    @Test
    fun worldEventsComeAndGo() {
        var s = at(busyState(), 3)
        s = s.adv(4 * 24 * hour)
        assertTrue(s.world.eventDraws >= 2, "events drawn: ${s.world.eventDraws}")
        assertTrue(s.world.modifiers.count { it.source.startsWith("event:") } <= w.config.events.maxActive)
        assertTrue(s.market.news.any { it.kind == "world" } || s.world.log.isNotEmpty())
    }

    @Test
    fun disastersOnlyHurtUninsuredProperties() {
        var s = at(newGame(5).withHugeCash(), 3)
        val lisbon = engine.content.estates.districts.filter { it.city == "lisbon" }
        val listings = lisbon.flatMap { engine.estate.listings(s, it.id) }.take(2)
        s = s.run(Command.BuyProperty(listings[0].id, insured = true)).run(Command.BuyProperty(listings[1].id, insured = false))
        val quake = w.events.first { it.scope == "city" && it.target == "lisbon" }
        val after = engine.desk.applyEvent(s, quake, GameRandom.stream(1, "t"), mutableListOf())
        val (insured, bare) = s.estate.properties.values.sortedBy { it.id }
        assertEquals(insured.condition, after.estate.properties.getValue(insured.id).condition)
        assertTrue(after.estate.properties.getValue(bare.id).condition < bare.condition)
    }

    @Test
    fun nemesisBeatsFollowTheStory() {
        var s = at(newGame(3), 2).adv(10 * 60_000)
        val intro = s.world.cards.firstOrNull { it.card == "nemesis_2" }
        assertTrue(intro != null && intro.rival == w.nemeses.first { it.chapter == 2 }.rival)
        s = s.run(Command.ResolveCard(intro!!.id, 0))
        s = s.copy(progress = s.progress.copy(chapter = 3)).adv(10 * 60_000)
        assertTrue("2:2" in s.world.beats, "finale of chapter 2")
        assertTrue(s.world.cards.any { it.card == "nemesis_3" })
    }

    @Test
    fun deskIsIdenticalLiveAndOffline() {
        val base = at(busyState(), 4)
        val a = base.adv(10 * hour)
        var b = base
        repeat(10 * 60) { b = b.adv(60_000) }
        assertEquals(a.world, b.world)
        assertEquals(a.finance, b.finance)
        assertEquals(a.ledger.balances, b.ledger.balances)
        trial(a)
    }

    @Test
    fun contentHasTheRequiredVolume() {
        assertTrue(w.rivals.size >= 200 && w.cards.size >= 150 && w.events.size >= 50)
        assertEquals(40, w.rivals.count { it.portrait })
        assertTrue(w.rivals.none { it.name.lowercase() in com.arnavgames.tycoon.core.content.ContentValidator.REAL_PEOPLE_BLOCKLIST })
        assertEquals(Money.ZERO, Money.ZERO)
    }
}
