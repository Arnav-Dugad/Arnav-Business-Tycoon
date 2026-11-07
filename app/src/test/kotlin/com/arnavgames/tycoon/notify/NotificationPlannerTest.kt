package com.arnavgames.tycoon.notify

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.notify.NotificationPlanner.Raw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationPlannerTest {
    private val loaded = ContentLoader.loadFromDirectory(File("../content"))
    private val engine = GameEngine(loaded.pack)
    private val utc = ZoneId.of("UTC")
    private val clock = object : GameClock {
        override fun wallMillis() = 1_800_000_000_000L
        override fun monotonicMillis() = 0L
        override fun bootId() = "test"
    }

    private fun ok(s: GameState, c: Command) = (engine.apply(s, c) as CommandResult.Ok).state
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, 7, h, m, 0, 0, utc).toInstant().toEpochMilli()

    /** A store at level 40 with its one-hour Flagship Arcade expansion under way. */
    private val building: GameState by lazy {
        var s = ok(engine.newGame(5, loaded.hash, clock), Command.OpenBusiness("retail"))
        s = s.copy(ledger = Grants.grant(s.ledger, s.simTimeMs, "test", Money.ofCents(java.math.BigInteger.TEN.pow(30)), 0, 0))
        s = ok(s, Command.BuyLevels("retail", 39))
        for (e in listOf("market_stall", "second_storefront")) {
            s = ok(s, Command.StartExpansion("retail", e))
            s = engine.advance(s, 3_600_000L).state
        }
        ok(s, Command.StartExpansion("retail", "flagship_arcade"))
    }

    @Test
    fun aFinishedExpansionIsAnnouncedWhenItReallyFinishes() {
        val t0 = System.nanoTime()
        val plan = NotificationPlanner.plan(engine, building, at(12), 8 * 3_600_000L, NotifyPrefs(), utc)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("planned ${plan.size} notifications for an 8 h absence in $ms ms: $plan")
        val exp = plan.first { it.title.endsWith("is open") }
        assertEquals(NotifyCategory.Timers, exp.category)
        // One hour away, give or take one simulation step.
        assertTrue(exp.atWallMs - at(13) in 0..NotificationPlanner.STEP_MS)
        assertTrue(exp.body.isNotBlank())
        assertTrue("planning must stay quick ($ms ms)", ms < 5_000)
    }

    @Test
    fun switchedOffCategoriesStaySilent() {
        val plan = NotificationPlanner.plan(engine, building, at(12), 8 * 3_600_000L, NotifyPrefs(timers = false), utc)
        assertTrue(plan.none { it.category == NotifyCategory.Timers })
    }

    @Test
    fun quietHoursWaitForTheMorning() {
        val found = listOf(Raw(60 * 60_000L, NotifyCategory.Timers, "Done", "Body"))
        val night = NotificationPlanner.arrange(found, at(21, 30), NotifyPrefs(), utc)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 8, 0, 0, 0, utc).toInstant().toEpochMilli(), night.single().atWallMs)
        val noQuiet = NotificationPlanner.arrange(found, at(21, 30), NotifyPrefs(quietHours = false), utc)
        assertEquals(at(22, 30), noQuiet.single().atWallMs)
    }

    @Test
    fun closeEventsMergeAndNoDayGetsMoreThanThree() {
        val close = listOf(
            Raw(10 * 60_000L, NotifyCategory.Timers, "A is open", "x"),
            Raw(20 * 60_000L, NotifyCategory.Auctions, "Hammer falls", "y"),
        )
        val merged = NotificationPlanner.arrange(close, at(9), NotifyPrefs(), utc)
        assertEquals(1, merged.size)
        assertEquals("2 updates from your empire", merged.single().title)
        assertEquals("A is open · Hammer falls", merged.single().body)

        val many = (1..6).map { Raw(it * 60 * 60_000L, NotifyCategory.Timers, "T$it", "b") }
        val capped = NotificationPlanner.arrange(many, at(9), NotifyPrefs(), utc)
        assertEquals(NotificationPlanner.MAX_PER_DAY, capped.size)
        assertEquals(listOf("T1", "T2", "T3"), capped.map { it.title })
    }

    @Test
    fun nothingImminentOrAlreadyKnown() {
        val found = listOf(Raw(30_000L, NotifyCategory.Timers, "Too soon", "b"))
        assertTrue(NotificationPlanner.arrange(found, at(9), NotifyPrefs(), utc).isEmpty())
    }
}
