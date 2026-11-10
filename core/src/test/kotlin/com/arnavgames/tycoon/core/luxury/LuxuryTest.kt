package com.arnavgames.tycoon.core.luxury

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withCash
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuxuryTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val lux = engine.luxury
    private val c = engine.content.luxury

    private fun atChapter(s: GameState, ch: Int) = s.copy(progress = s.progress.copy(chapter = ch))
    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")
    /** Auctions start at a seeded minute inside each 2-hour slot; step a minute at a time until one is live. */
    private fun untilAuction(start: GameState): GameState {
        var s = start.adv(60_000)
        var guard = 0
        while (s.luxury.auction == null && guard++ < 200) s = s.adv(60_000)
        return s
    }
    private fun cheapest(cat: String) = c.items.filter { it.category == cat }.minBy { it.price }

    @Test
    fun buyAndSellThroughTheDealer() {
        val car = cheapest("car")
        val s0 = atChapter(newGame(1).withCash(10_000_000), 2)
        val s1 = s0.run(Command.BuyLuxury(car.id))
        trial(s1)
        assertEquals(s0.cash - lux.buyCost(s0, car), s1.cash)
        assertTrue(car.id in s1.luxury.owned)
        assertInstanceOf(GameError.AlreadyDone::class.java, s1.reject(Command.BuyLuxury(car.id)))
        val s2 = s1.run(Command.SellLuxury(car.id))
        trial(s2)
        assertTrue(car.id !in s2.luxury.owned)
        assertTrue(s2.cash < s0.cash && s2.cash > s0.cash - lux.buyCost(s0, car) * 2L, "dealer spread and fee only")
    }

    @Test
    fun categoriesUnlockByChapter() {
        val s = newGame(2).withHugeCash()
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.BuyLuxury(cheapest("car").id)))
        assertInstanceOf(GameError.Locked::class.java, atChapter(s, 2).reject(Command.BuyLuxury(cheapest("yacht").id)))
        atChapter(s, 3).run(Command.BuyLuxury(cheapest("yacht").id))
        assertInstanceOf(GameError.Locked::class.java, atChapter(s, 3).reject(Command.BuyLuxury(cheapest("island").id)))
    }

    @Test
    fun carsDepreciateClassicsAppreciateArtCycles() {
        var s = atChapter(newGame(3).withHugeCash(), 4)
        val modern = c.items.first { it.category == "car" && !it.classic }
        val classic = c.items.first { it.category == "car" && it.classic }
        s = s.run(Command.BuyLuxury(modern.id)).run(Command.BuyLuxury(classic.id))
        val m0 = lux.ownedValue(s, modern.id)
        val c0 = lux.ownedValue(s, classic.id)
        s = s.adv(10 * day)
        assertTrue(lux.ownedValue(s, modern.id) < m0)
        assertTrue(lux.ownedValue(s, classic.id) > c0)
        val art = c.items.first { it.category == "painting" }
        val prices = (0..12).map { lux.marketPrice(art, it * day / 2).toDollarsDouble() }
        assertTrue(prices.max() / prices.min() > 1.15, "art prices cycle")
    }

    @Test
    fun statusComesFromDisplayedItemsAndUnlocksTiers() {
        var s = atChapter(newGame(4).withHugeCash(), 4)
        assertEquals(0.0, lux.statusPoints(s))
        val cars = c.items.filter { it.category == "car" }.sortedByDescending { it.statusPoints }
        cars.take(8).forEach { s = s.run(Command.BuyLuxury(it.id)) }
        val capacity = lux.capacity(s, "garage")
        assertEquals(capacity, lux.displayed(s).size, "only the garage's capacity is on display")
        val before = lux.statusPoints(s)
        s = s.run(Command.UpgradeShowroom("garage"))
        assertTrue(lux.capacity(s, "garage") > capacity)
        assertTrue(lux.statusPoints(s) > before)
        assertTrue(lux.tierIndex(s) >= 1, "a garage of supercars earns at least Bronze")
        assertTrue(lux.dealerPrice(s, cheapest("watch")) < cheapest("watch").price)
    }

    @Test
    fun completingASetGivesItsBonus() {
        val set = c.sets.first { it.bonusType == "income" && it.bonusTarget == "retail" }
        var s = atChapter(newGame(5).withHugeCash(), 8).run(Command.OpenBusiness("retail")).run(Command.BuyLevels("retail", 50))
        val r0 = engine.economy.businessRate(s, "retail").gross
        set.items.forEach { s = s.run(Command.BuyLuxury(it)) }
        assertTrue(engine.economy.businessRate(s, "retail").gross > r0)
        assertEquals(set.bonusValue, lux.incomeBonus(s, "retail"), 1e-9)
    }

    @Test
    fun upkeepIsChargedForYachtsAndJets() {
        var s = atChapter(newGame(6).withHugeCash(), 3).run(Command.BuyLuxury(cheapest("yacht").id))
        s = s.adv(13 * hour)
        assertTrue(s.ledger.balance(com.arnavgames.tycoon.core.ledger.Accounts.expense("luxury_upkeep")).isPositive)
        trial(s)
    }

    @Test
    fun auctionsRunWithRivalsAndCloseAfterQuiet() {
        var s = atChapter(newGame(7).withCash(1_000), 3)
        val events = mutableListOf<GameEvent>()
        // Within the first auction slot after unlock an auction appears; let it play out without the player.
        repeat(5 * 60) { val r = engine.advance(s, 60_000); s = r.state; events += r.events }
        assertTrue(events.any { it is GameEvent.AuctionStarted }, "an auction started")
        assertTrue(s.luxury.auction == null || s.luxury.auction!!.closeSim > s.simTimeMs)
        assertEquals(0, s.luxury.auctionsWon)
    }

    @Test
    fun playerCanWinAnAuction() {
        var s = untilAuction(atChapter(newGame(8).withHugeCash(), 3))
        assertNotNull(s.luxury.auction, "an auction is live")
        val item = s.luxury.auction!!.item
        // Keep bidding whenever outbid, every 5 seconds, until the hammer falls.
        var guard = 0
        while (s.luxury.auction != null && guard++ < 5_000) { // a sale runs 4.5 hours (3,240 steps of 5 s)
            if (s.luxury.auction!!.leader != LuxuryEngine.PLAYER) s = s.run(Command.BidAuction)
            s = s.adv(5_000)
        }
        assertTrue(item in s.luxury.owned, "won the lot")
        assertEquals(1, s.luxury.auctionsWon)
        trial(s)
    }

    @Test
    fun proxyBidWinsWhileThePlayerIsAway() {
        var s = untilAuction(atChapter(newGame(12).withHugeCash(), 3))
        val a = s.luxury.auction!!
        val estimate = lux.marketPrice(c.itemById.getValue(a.item), s.simTimeMs)
        s = s.run(Command.SetMaxBid(estimate * 3L))
        assertEquals(LuxuryEngine.PLAYER, s.luxury.auction!!.leader)
        s = s.adv(6 * hour) // away for the whole 4.5-hour sale: the proxy answers every rival
        assertTrue(a.item in s.luxury.owned, "won by proxy")
        trial(s)
    }

    @Test
    fun auctionsAreIdenticalLiveAndOffline() {
        val base = atChapter(newGame(9).withHugeCash(), 3)
        val a = base.adv(9 * hour)
        var b = base
        repeat(9 * 60) { b = b.adv(60_000) }
        assertEquals(a.luxury, b.luxury)
        assertEquals(a.ledger.balances, b.ledger.balances)
    }

    @Test
    fun netWorthCountsTheCollection() {
        val s0 = atChapter(newGame(10).withHugeCash(), 4)
        val island = cheapest("island")
        val s1 = s0.run(Command.BuyLuxury(island.id))
        val drop = engine.economy.netWorth(s0) - engine.economy.netWorth(s1)
        assertEquals(lux.buyCost(s0, island) - lux.ownedValue(s1, island.id), drop, "only the dealer fee and discount gap are lost")
    }

    @Test
    fun missionsCountLuxuryAndAuctions() {
        var s = atChapter(newGame(11).withHugeCash(), 2)
        c.items.filter { it.category == "watch" }.take(3).forEach { s = s.run(Command.BuyLuxury(it.id)) }
        assertEquals(1.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.LuxuryOwned(3)))
        assertEquals(0.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.AuctionsWon(1)))
    }
}
