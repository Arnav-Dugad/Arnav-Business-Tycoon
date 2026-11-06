package com.arnavgames.tycoon.game

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import java.math.BigInteger

/** Debug-only scenarios for screenshots and manual QA. Built only from real engine commands (no state hacking). */
internal object DebugScenarios {
    /** Left without managers in "lategame" so their hands-on controls can be screenshotted. */
    private val UNMANAGED = setOf("fashion", "energy", "bank", "airline")

    fun build(engine: GameEngine, base: GameState, name: String): GameState? = when (name) {
        "midgame" -> midGame(engine, base)
        "lategame" -> lateGame(engine, base)
        else -> null
    }

    private fun midGame(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", Money.ofDollars(3_000_000), 25, 40))
        listOf(
            Command.AdvanceOnboarding(5), Command.OpenBusiness("retail"), Command.BuyLevels("retail", 34),
            Command.HireManager("retail", "retail_mira"), Command.OpenBusiness("restaurants"), Command.BuyLevels("restaurants", 26),
            Command.SetRestaurantQuality("restaurants", "fine"), Command.OpenBusiness("logistics"), Command.BuyLevels("logistics", 21),
            Command.HireManager("restaurants", "rest_amara"), Command.StartExpansion("retail", "second_storefront"),
            Command.UpgradeHq, Command.UpgradeHq,
        ).forEach { s = s.run(it) }
        return engine.advance(s, 90_000).state
    }

    /**
     * Plays the story forward with a large grant: claims missions, opens and levels businesses, hires one manager
     * each, upgrades HQ and forms the first two conglomerates. Leaves the holding company unformed so its card shows.
     */
    private fun lateGame(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        val grant = Money.ofCents(BigInteger.TEN.pow(26))
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", grant, 500, 400))
        s = s.run(Command.AdvanceOnboarding(5))
        val content = engine.content
        repeat(40) { round ->
            s = s.run(Command.HustleTap(20))
            content.chapters.flatMap { it.missions }.forEach { s = s.run(Command.ClaimMission(it.id)) }
            repeat(6) { s = s.run(Command.UpgradeHq) }
            content.businesses.forEach { b ->
                s = s.run(Command.OpenBusiness(b.id))
                s = s.run(Command.BuyLevels(b.id, 10))
                if (b.id !in UNMANAGED) b.managers.firstOrNull()?.let { m -> s = s.run(Command.HireManager(b.id, m.id)) }
                b.expansions.forEach { e -> s = s.run(Command.StartExpansion(b.id, e.id)) }
            }
            if (round > 20) content.conglomerates.take(2).forEach { s = s.run(Command.FormConglomerate(it.id)) }
            s = engine.advance(s, 30 * 60_000L).state
        }
        return engine.advance(s, 90_000).state
    }
}
