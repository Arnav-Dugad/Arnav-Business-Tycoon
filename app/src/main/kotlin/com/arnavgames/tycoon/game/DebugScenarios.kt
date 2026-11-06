package com.arnavgames.tycoon.game

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

/** Debug-only scenarios for screenshots and manual QA. Built only from real engine commands (no state hacking). */
internal object DebugScenarios {
    fun build(engine: GameEngine, base: GameState, name: String): GameState? = when (name) {
        "midgame" -> midGame(engine, base)
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
}
