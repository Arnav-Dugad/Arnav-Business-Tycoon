package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.ManagerPolicy
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.mechanics.MechCtx
import com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry
import com.arnavgames.tycoon.core.engine.mechanics.ViewControl
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModulesTest {
    private val hour = 3_600_000L
    private val moduleBusinesses = engine.content.businesses.filter { it.mechanic is MechanicDef.Module }

    private fun ctx(id: String, minute: Long, owned: Set<String> = emptySet()) =
        MechCtx(id, 30, minute * 60_000, 99L, owned + id, Money.ofDollars(1000))

    @Test
    fun everyModuleKindIsUsedOnceAndHasContent() {
        assertEquals(15, moduleBusinesses.size)
        assertEquals(MechanicRegistry.modules.keys, moduleBusinesses.map { (it.mechanic as MechanicDef.Module).kind }.toSet())
    }

    /** Random actions and minutes for three game-days per module: never throws, efficiency always finite and sane. */
    @Test
    fun fuzzEveryModule() {
        for (def in moduleBusinesses) {
            val m = def.mechanic as MechanicDef.Module
            val module = MechanicRegistry.of(m.kind)
            val rng = GameRandom.stream(5, "fuzz:${m.kind}")
            var st = module.initial(m, ctx(def.id, 0))
            for (minute in 1L..(3 * 24 * 60)) {
                val c = ctx(def.id, minute, setOf("retail", "media", "tech", "energy", "airline", "mining", "logistics"))
                st = module.minute(m, st, c)
                if (rng.chance(0.05)) {
                    val view = module.view(m, st, c)
                    val actions = view.controls.flatMap { ctrl ->
                        when (ctrl) {
                            is ViewControl.Choice -> ctrl.options.map { ctrl.actionId to it.id }
                            is ViewControl.Action -> listOf(ctrl.actionId to ctrl.value)
                            is ViewControl.Progress -> emptyList()
                        }
                    }
                    if (actions.isNotEmpty()) {
                        val (a, v) = rng.pick(actions)
                        module.act(m, st, a, v, c).onSuccess { st = it.state; assertTrue(!it.cost.isNegative) }
                    }
                }
                if (minute % 97 == 0L) {
                    val r = module.manage(m, st, c, 0.9, ManagerPolicy.entries[(minute % 3).toInt()], Money.ofDollars(1_000_000))
                    st = r.state
                }
                val e = module.efficiency(m, st, c)
                assertTrue(e.isFinite() && e > 0.05 && e < 4.0, "${m.kind} efficiency $e at minute $minute")
            }
            assertNotNull(module.view(m, st, ctx(def.id, 4321)))
        }
    }

    @Test
    fun modulesAreDeterministic() {
        for (def in moduleBusinesses) {
            val m = def.mechanic as MechanicDef.Module
            val module = MechanicRegistry.of(m.kind)
            fun play(): MechanicState.Module {
                var st = module.initial(m, ctx(def.id, 0))
                for (minute in 1L..2000) st = module.minute(m, st, ctx(def.id, minute))
                return st
            }
            assertEquals(play(), play(), m.kind)
        }
    }

    @Test
    fun invalidActionsAreRejected() {
        for (def in moduleBusinesses) {
            val m = def.mechanic as MechanicDef.Module
            val module = MechanicRegistry.of(m.kind)
            val st = module.initial(m, ctx(def.id, 0))
            assertTrue(module.act(m, st, "definitely_not_an_action", "x", ctx(def.id, 1)).isFailure, m.kind)
        }
    }

    // ------------------------------------------------------------------ engine integration

    /** As many businesses as there are slots (14 before Dynasty perks), half managed, at chapter 8. */
    private fun empireState(): GameState {
        var s = newGame(11).withHugeCash().let { it.copy(progress = it.progress.copy(chapter = 8), hq = it.hq.copy(level = 25)) }
        val slots = engine.economy.slots(s)
        for ((i, def) in engine.content.businesses.take(slots).withIndex()) {
            s = s.run(Command.OpenBusiness(def.id)).run(Command.BuyLevels(def.id, 30))
            if (i % 2 == 0) s = s.run(Command.HireManager(def.id, def.managers.first().id))
        }
        return s
    }

    @Test
    fun advanceStaysSplitInvariantWithAllModules() {
        val start = empireState()
        val whole = start.adv(2 * hour + 17_000)
        repeat(6) { trial ->
            val rng = GameRandom.stream(trial.toLong(), "split-modules")
            var s = start
            var left = 2 * hour + 17_000
            while (left > 0) {
                val step = minOf(left, 1 + rng.nextLong().mod(25 * 60_000L))
                s = s.adv(step)
                left -= step
            }
            assertEquals(whole, s, "trial $trial")
        }
        assertEquals(Money.ZERO, Ledger.trialBalance(whole.ledger))
    }

    @Test
    fun offlineEqualsLiveWithAllModules() {
        val start = empireState()
        var live = start
        repeat(3 * 3600) { live = live.adv(1000) }
        assertEquals(start.adv(3 * hour), live)
    }

    @Test
    fun mechanicActionsChargeTheirCostThroughTheLedger() {
        var s = empireState()
        val cashBefore = s.cash
        s = s.run(Command.MechanicAction("factory", "upgrade", "assembly"))
        assertTrue(s.cash < cashBefore)
        assertEquals(1L, (s.businesses.getValue("factory").mechanic as MechanicState.Module).n("assembly"))
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.MechanicAction("factory", "upgrade", "nope")))
        assertEquals(Money.ZERO, Ledger.trialBalance(s.ledger))
    }

    @Test
    fun conglomeratesRequireTiersAndAreExclusive() {
        var s = empireState()
        // Level 31 is below Scale Tier 3 (level 50): blocked.
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.FormConglomerate("main_street")))
        for (id in listOf("retail", "restaurants", "farmland", "energy", "mining")) s = s.run(Command.BuyLevels(id, 30))
        val before = engine.economy.businessRate(s, "retail").net
        s = s.run(Command.FormConglomerate("main_street"))
        assertTrue("main_street" in s.conglomerates)
        assertTrue(engine.economy.businessRate(s, "retail").net > before, "conglomerate raises member income")
        // Farmland already belongs to Main Street, so Heartland is blocked.
        val err = s.reject(Command.FormConglomerate("heartland"))
        assertInstanceOf(GameError.Locked::class.java, err)
        assertTrue((err as GameError.Locked).reason.contains("already belongs"))
        // Holding company needs three conglomerates.
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.FormHolding("Arnav Holdings")))
    }

    @Test
    fun chaptersAddBusinessSlots() {
        val s = newGame()
        val base = engine.economy.slots(s)
        val later = s.copy(progress = s.progress.copy(chapter = 5))
        assertEquals(base + 4, engine.economy.slots(later))
    }

    @Test
    fun farmlandBoostsRestaurantsAndMediaCampaignsBoostTargets() {
        var s = newGame().withHugeCash().let { it.copy(progress = it.progress.copy(chapter = 8), hq = it.hq.copy(level = 25)) }
        s = s.run(Command.OpenBusiness("restaurants"))
        val plain = engine.economy.businessRate(s, "restaurants").gross
        s = s.run(Command.OpenBusiness("farmland"))
        assertTrue(engine.economy.businessRate(s, "restaurants").gross > plain)
        s = s.run(Command.OpenBusiness("media"))
        val beforeCampaign = engine.economy.businessRate(s, "restaurants").gross
        s = s.run(Command.MechanicAction("media", "campaign", "restaurants"))
        assertTrue(engine.economy.businessRate(s, "restaurants").gross > beforeCampaign)
        s = s.adv(121 * 60_000L)
        assertEquals(beforeCampaign.microCentsPerSecond.signum(), engine.economy.businessRate(s, "restaurants").gross.microCentsPerSecond.signum())
    }
}
