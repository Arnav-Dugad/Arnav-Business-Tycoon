package com.arnavgames.tycoon.core.save

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.state.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Release gate G13 (docs/PLAN_2.0.md §5): saves written by every released version must keep working in today's
 * engine and content.
 *
 * The fixtures in `src/test/resources/saves/golden/<version>/` were written by that release's own engine from its
 * own debug scenarios (fresh game through Chapter 8 and a second generation). After each release, add its folder.
 * Each save goes through the app's real load path (decode, then offline time), then a burst of common commands and
 * a full day of play, and must keep its progress and a balanced ledger throughout.
 */
class ReleaseSaveCompatibilityTest {
    private val root = File("src/test/resources/saves/golden")
    private val hour = 3_600_000L

    private fun GameState.tryRun(c: Command): GameState = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this

    private fun balanced(s: GameState, what: String) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "$what: ledger out of balance")

    @TestFactory
    fun everyReleasedSaveStillPlays(): List<DynamicTest> {
        val files = root.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }
            .flatMap { v -> v.listFiles().orEmpty().filter { it.extension == "abtsave" }.sortedBy { it.name } }
        assertTrue(files.size >= 16, "expected golden saves for 1.0.0 and 1.1.0, found ${files.size}")
        return files.map { f ->
            DynamicTest.dynamicTest("${f.parentFile.name}/${f.nameWithoutExtension}") { check(f) }
        }
    }

    private fun check(f: File) {
        val label = "${f.parentFile.name}/${f.name}"
        val decoded = SaveCodec.decode(f.readBytes())
        val before = decoded.state
        balanced(before, "$label as saved")

        // The app's load path: decode, then credit offline time (the cap applies in the app; an hour is typical).
        var s = engine.advance(before, hour).state
        balanced(s, "$label after offline time")

        // Nothing the player built is lost.
        assertEquals(before.businesses.keys, s.businesses.keys, "$label: businesses")
        before.businesses.forEach { (id, b) -> assertTrue(s.businesses.getValue(id).level >= b.level, "$label: $id lost levels") }
        assertTrue(s.progress.chapter >= before.progress.chapter, "$label: chapter went backwards")
        assertTrue(s.progress.completedMissions.containsAll(before.progress.completedMissions), "$label: missions lost")
        assertEquals(before.dynasty.generation, s.dynasty.generation, "$label: generation")
        assertEquals(before.dynasty.legacy, s.dynasty.legacy, "$label: legacy")
        // (An open auction may be won while away, so the collection can only grow.)
        assertTrue(s.luxury.owned.keys.containsAll(before.luxury.owned.keys), "$label: collection items lost")
        assertEquals(before.ledger.bullion, s.ledger.bullion, "$label: bullion")

        // Today's systems accept the old state: the commands a returning player makes first.
        s = s.tryRun(Command.CollectAll)
        engine.content.chapters.flatMap { it.missions }.forEach { s = s.tryRun(Command.ClaimMission(it.id)) }
        s.businesses.keys.sorted().forEach { id -> s = s.tryRun(Command.BuyLevels(id, 1)) }
        s = s.tryRun(Command.EquipCosmetic("rocket", "avatar"))
        balanced(s, "$label after commands")

        // A full day of play: timers, cycles, auctions, rent, loans and taxes all tick over the old state.
        s = engine.advance(s, 24 * hour).state
        balanced(s, "$label after a day")
        assertTrue(engine.economy.netWorth(s).log10Dollars().isFinite(), "$label: net worth")

        // And it saves again in today's format.
        val again = SaveCodec.decode(SaveCodec.encode(s, 1, "test", 0, engine.economy.netWorth(s).log10Dollars()))
        assertEquals(s, again.state, "$label: re-encode round trip")
    }
}
