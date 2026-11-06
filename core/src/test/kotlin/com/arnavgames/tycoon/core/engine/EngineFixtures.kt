package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.time.FakeClock

object EngineFixtures {
    val loaded by lazy { ContentLoader.loadFromRepo() }
    val engine by lazy { GameEngine(loaded.pack) }

    fun newGame(seed: Long = 1L): GameState = engine.newGame(seed, loaded.hash, FakeClock())

    fun GameState.withCash(dollars: Long): GameState =
        copy(ledger = Grants.grant(ledger, simTimeMs, "test grant", Money.ofDollars(dollars), 0, 0))

    fun GameState.withFavor(units: Long): GameState =
        copy(ledger = Grants.grant(ledger, simTimeMs, "test favor", Money.ZERO, 0, units))

    fun GameState.run(cmd: Command): GameState = when (val r = engine.apply(this, cmd)) {
        is CommandResult.Ok -> r.state
        is CommandResult.Rejected -> error("command $cmd rejected: ${r.error}")
    }

    fun GameState.reject(cmd: Command): GameError = when (val r = engine.apply(this, cmd)) {
        is CommandResult.Ok -> error("command $cmd unexpectedly accepted")
        is CommandResult.Rejected -> r.error
    }

    fun GameState.adv(ms: Long): GameState = engine.advance(this, ms).state

    /** A busy mid-game state: three businesses, one unmanaged, a running expansion, frenzy and a promo active. */
    fun busyState(): GameState {
        // Grant far more than any content price so these tests exercise rules, not balance numbers.
        var s = newGame(7).withCash(1_000_000_000_000).withFavor(50)
        s = s.run(Command.OpenBusiness("retail"))
            .run(Command.BuyLevels("retail", 30))
            .run(Command.OpenBusiness("restaurants"))
            .run(Command.BuyLevels("restaurants", 12))
            .run(Command.HireManager("restaurants", "rest_amara"))
            .run(Command.OpenBusiness("logistics"))
            .run(Command.BuyLevels("logistics", 22))
            .run(Command.HireManager("logistics", "log_kofi"))
            .run(Command.StartExpansion("retail", "market_stall"))
            .run(Command.StartRetailPromo("retail"))
            .run(Command.SetRetailPrice("retail", 140))
            .run(Command.ActivateFrenzy)
        return s
    }
}
