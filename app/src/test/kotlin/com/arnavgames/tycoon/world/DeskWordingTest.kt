package com.arnavgames.tycoon.world

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.state.CardState
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.ui.world.WorldMapper
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Desk choices say what they do in plain words, including what a cost-only choice buys you. */
class DeskWordingTest {
    private val loaded = ContentLoader.loadFromDirectory(File("../content"))
    private val engine = GameEngine(loaded.pack)
    private val clock = object : GameClock {
        override fun wallMillis() = 1_800_000_000_000L
        override fun monotonicMillis() = 0L
        override fun bootId() = "test"
    }

    @Test
    fun everyChoiceIsDescribedAndCostOnlyChoicesSayTheyAvoidTheHit() {
        val start = (engine.apply(engine.newGame(5, loaded.hash, clock), Command.OpenBusiness("retail")) as CommandResult.Ok).state
        val mapper = WorldMapper(engine, emptySet())
        var checkedAvoid = 0
        for (def in engine.content.world.cards) {
            val s = start.copy(world = start.world.copy(cards = listOf(CardState(1, def.id, business = "retail", rival = engine.content.world.rivals.first().id, createdSim = 0))))
            val card = mapper.desk(s).cards.single()
            card.options.forEach { o -> assertTrue("${def.id}/${o.label} has no description", o.detail.isNotBlank() || o.outcomes.isNotEmpty()) }
            val hurts = def.options.any { o -> o.outcomes.any { oc -> oc.effects.any { it.type == "income" && it.pct < 0 } } }
            def.options.forEachIndexed { i, o ->
                val touches = o.outcomes.any { oc -> oc.effects.any { it.type == "income" } }
                if (hurts && !touches && o.outcomes.size == 1) {
                    assertTrue("${def.id}: '${card.options[i].detail}'", card.options[i].detail.contains("no income hit", ignoreCase = true))
                    checkedAvoid++
                }
            }
        }
        assertTrue("content has cards with a cost-only way out", checkedAvoid > 0)
    }
}
