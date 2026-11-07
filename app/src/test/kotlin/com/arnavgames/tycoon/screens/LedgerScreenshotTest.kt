package com.arnavgames.tycoon.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.core.time.TimeFlag
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumTheme
import com.arnavgames.tycoon.designsystem.theme.ThemeMode
import com.arnavgames.tycoon.game.GameSession
import com.arnavgames.tycoon.ui.common.OfflineEarningsDialog
import com.arnavgames.tycoon.ui.ledger.DailyLedger
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Daily Ledger front page, from a real report built by [DailyLedger], in light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class LedgerScreenshotTest(private val theme: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes() = listOf(arrayOf<Any>("light"), arrayOf<Any>("dark"))
    }

    @get:Rule val rule = createComposeRule()

    @Test
    fun dailyLedger() {
        val loaded = ContentLoader.loadFromDirectory(File("../content"))
        val engine = GameEngine(loaded.pack)
        val clock = object : GameClock {
            override fun wallMillis() = 1_800_000_000_000L
            override fun monotonicMillis() = 0L
            override fun bootId() = "test"
        }
        val start = (engine.apply(engine.newGame(5, loaded.hash, clock), Command.OpenBusiness("retail")) as CommandResult.Ok).state
        val after = engine.advance(start, 3 * 3_600_000L).state
        val stories = DailyLedger.build(engine, start, after, listOf(
            GameEvent.ExpansionCompleted("retail", "market_stall"),
            GameEvent.MissionCompleted("c1_hustle"),
            GameEvent.AuctionWon("heli_1", Money.ofDollars(2_400_000)),
            GameEvent.WorldEventStarted(engine.content.world.events.first().id),
        ))
        val report = GameSession.OfflineReport(Money.ofDollars(48_250), 3 * 3_600_000L, 3 * 3_600_000L, TimeFlag.None, 4 * 3_600_000L, stories, 12)
        rule.setContent {
            AurumTheme(mode = if (theme == "dark") ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(Aurum.colors.background)) { OfflineEarningsDialog(report) {} }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/20_daily_ledger_$theme.png")
    }

    @Test
    fun whatsNew() {
        rule.setContent {
            AurumTheme(mode = if (theme == "dark") ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(Aurum.colors.background)) {
                    com.arnavgames.tycoon.ui.common.WhatsNewSheet("1.2.0", com.arnavgames.tycoon.ui.common.WhatsNew.forVersion("1.2.0")) {}
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/21_whats_new_$theme.png")
    }
}
