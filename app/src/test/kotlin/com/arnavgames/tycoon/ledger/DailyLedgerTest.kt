package com.arnavgames.tycoon.ledger

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.ui.ledger.DailyLedger
import com.arnavgames.tycoon.ui.ledger.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DailyLedgerTest {
    private val loaded = ContentLoader.loadFromDirectory(File("../content"))
    private val engine = GameEngine(loaded.pack)
    private val clock = object : GameClock {
        override fun wallMillis() = 1_800_000_000_000L
        override fun monotonicMillis() = 0L
        override fun bootId() = "test"
    }
    private val start = (engine.apply(engine.newGame(5, loaded.hash, clock), Command.OpenBusiness("retail")) as CommandResult.Ok).state

    @Test
    fun problemsComeFirstThenWinsAndAtMostFiveStories() {
        val events = listOf(
            GameEvent.ExpansionCompleted("retail", "market_stall"),
            GameEvent.MissionCompleted("c1_hustle"),
            GameEvent.PaymentMissed("loan", Money.ofDollars(100)),
            GameEvent.AuctionLost("heli_1", "Someone"),
            GameEvent.TaxFiled(Money.ZERO, audited = false, auto = true),
            GameEvent.WorldEventStarted(engine.content.world.events.first().id),
        )
        val stories = DailyLedger.build(engine, start, engine.advance(start, 3_600_000L).state, events)
        assertTrue(stories.size <= 5)
        assertEquals(Tone.Bad, stories.first().tone)
        assertTrue(stories.first().headline.contains("payment"))
        assertTrue(stories.any { it.headline.contains("Weekend") || it.headline.contains("opened") })
        assertTrue(stories.any { it.headline.startsWith("Goal reached") })
        assertEquals(stories.sortedByDescending { it.weight }, stories)
    }

    @Test
    fun aQuietAbsenceStillReadsWell() {
        val after = engine.advance(start, 2 * 3_600_000L).state
        val stories = DailyLedger.build(engine, start, after, emptyList())
        stories.forEach { assertTrue(it.headline.isNotBlank() && it.detail.isNotBlank()) }
    }
}
