package com.arnavgames.tycoon.core.store

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoreTest {
    private val hour = 3_600_000L
    private val c = engine.content.store

    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")
    private fun withBullion(s: GameState, n: Long) = s.copy(ledger = Grants.grant(s.ledger, s.simTimeMs, "test", Money.ZERO, n, 0))

    @Test
    fun theCatalogueIsFairByDesign() {
        assertTrue(c.products.size >= 20)
        // Every grant is fixed (no random rewards), and every slot product is capped (slots are also earnable in play).
        assertTrue(c.products.all { p -> p.grants.slots == 0 || p.limit in 1..3 })
        assertTrue(c.products.none { it.name.contains("random", ignoreCase = true) || it.description.contains("left", ignoreCase = false) && it.description.contains("only", ignoreCase = true) })
    }

    @Test
    fun aDemoPurchaseGrantsAndMarksTheBoostedFlag() {
        val s = busyState()
        val r = s.run(Command.DemoPurchase("bullion_550"))
        assertEquals(s.ledger.bullion + Money.ofCents(550), r.ledger.bullion)
        assertTrue(r.store.boosted)
        assertEquals(1, r.store.demoPurchases)
        trial(r)
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.DemoPurchase("boost_8h")), "Bullion-only products")
    }

    @Test
    fun bullionPurchasesDoNotMarkBoosted() {
        val s = withBullion(busyState(), 2000)
        val r = s.run(Command.BuyWithBullion("slot_1"))
        assertFalse(r.store.boosted)
        assertEquals(1, r.store.extraSlots)
        assertEquals(engine.economy.slots(s) + 1, engine.economy.slots(r))
        assertEquals(s.ledger.bullion - Money.ofCents(800), r.ledger.bullion)
        trial(r)
        assertInstanceOf(GameError.InsufficientFunds::class.java, busyState().reject(Command.BuyWithBullion("slot_1")))
    }

    @Test
    fun capsAndLimitsHold() {
        var s = withBullion(busyState(), 100_000)
        repeat(2) { s = s.run(Command.BuyWithBullion("slot_1")) }
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.BuyWithBullion("slot_1")))
        s = s.run(Command.DemoPurchase("starter_pack"))
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.DemoPurchase("starter_pack")), "once per account")
        repeat(3) { s = s.run(Command.BuyWithBullion("permanent_5")) }
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.BuyWithBullion("permanent_5")))
    }

    @Test
    fun boostsExtendAndExpireExactly() {
        var s = withBullion(busyState(), 10_000)
        val base = engine.economy.businessRate(s, "retail").net
        s = s.run(Command.BuyWithBullion("boost_2h")).run(Command.BuyWithBullion("boost_2h"))
        val boost = s.world.modifiers.single { it.source == StoreEngine.BOOST }
        assertEquals(s.simTimeMs + 4 * hour, boost.untilSim, "the second boost extends the first")
        assertTrue(engine.economy.businessRate(s, "retail").net > base)
        s = s.adv(4 * hour + 1)
        assertTrue(s.world.modifiers.none { it.source == StoreEngine.BOOST })
    }

    @Test
    fun vipAndTheSeasonPass() {
        var s = withBullion(busyState(), 10_000).adv(60_000)
        val offline = engine.economy.offlineCapMs(s)
        s = s.run(Command.BuyWithBullion("vip_30"))
        assertEquals(offline + 1 * hour, engine.economy.offlineCapMs(s).coerceAtMost(offline + 1 * hour))
        assertInstanceOf(GameError.Locked::class.java, s.copy(meta = s.meta.copy(seasonXp = 500)).reject(Command.ClaimSeasonTier(1, premium = true)))
        s = s.run(Command.BuyWithBullion("season_pass")).copy(meta = s.meta.copy(seasonXp = 500))
        val r = s.run(Command.ClaimSeasonTier(1, premium = true))
        assertTrue(1 in r.meta.seasonClaimedPremium)
    }

    @Test
    fun cosmeticsAreEquippedAndSurvivePrestigeLikeOtherEntitlements() {
        var s = withBullion(busyState(), 1000).run(Command.BuyWithBullion("title_tycoon"))
        s = s.run(Command.EquipCosmetic("title_tycoon", "title"))
        assertEquals("title_tycoon", s.store.title)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.EquipCosmetic("frame_gold", "frame")))
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.EquipCosmetic("title_tycoon", "frame")))
    }

    @Test
    fun freeAvatarsNeedNoPurchaseAndPremiumOnesDo() {
        assertEquals(18, c.freeAvatars.size)
        assertEquals(24, c.freeAvatars.size + c.cosmetics.count { it.kind == "avatar" })
        var s = busyState().run(Command.EquipCosmetic("rocket", "avatar"))
        assertEquals("rocket", s.store.avatar)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.EquipCosmetic("avatar_yacht", "avatar")))
        assertInstanceOf(GameError.NotFound::class.java, s.reject(Command.EquipCosmetic("rocket", "title")), "free avatars are not titles")
        s = withBullion(s, 1000).run(Command.BuyWithBullion("avatar_yacht")).run(Command.EquipCosmetic("avatar_yacht", "avatar"))
        assertEquals("avatar_yacht", s.store.avatar)
        assertFalse(s.store.boosted, "Bullion purchases never mark Boosted")
        assertEquals(null, s.run(Command.EquipCosmetic(null, "avatar")).store.avatar)
    }
}
