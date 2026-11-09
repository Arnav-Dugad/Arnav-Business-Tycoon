package com.arnavgames.tycoon.screens

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click as touchClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arnavgames.tycoon.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the real app on the JVM (docs/PLAN_2.0.md I3): each test loads a debug scenario, walks its screens
 * and captures them in light and dark at Pixel 7 size. Images go to app/build/screens/ for review.
 * Record: ./gradlew :app:testDebugUnitTest --tests "*ScreensTest*" -Proborazzi.test.record=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreensTest(private val theme: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes() = listOf(arrayOf<Any>("light"), arrayOf<Any>("dark"))
    }

    @get:Rule val rule = createEmptyComposeRule()

    /** Each theme gets its own fresh app (the Application and its game live for one test). */
    @Before
    fun setTheme() = RuntimeEnvironment.setQualifiers(if (theme == "dark") "+night" else "+notnight")

    private fun waitText(text: String, timeout: Long = 30_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/${name}_$theme.png")
    }

    private fun click(n: SemanticsNodeInteraction) { n.performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
    private fun tapText(text: String) = click(rule.onAllNodesWithText(text, substring = true).onFirst())
    private fun tab(name: String) = click(rule.onAllNodesWithContentDescription(name, substring = true).onFirst())
    private fun back() = click(rule.onAllNodesWithContentDescription("Back").onFirst())
    private fun tag(t: String) = click(rule.onNodeWithTag(t))
    /** Collection and Dynasty live under the More tab. */
    private fun more(door: String) { tab("More"); tag("door-$door") }
    /** The Empire tab is ready (the businesses summary sits below the counter, so scroll to it, then back up). */
    private fun waitEmpire(timeout: Long = 30_000) {
        rule.waitUntil(timeout) {
            runCatching { rule.onNodeWithTag("empire-list").performScrollToNode(hasText("Your businesses", substring = true, ignoreCase = true)); true }.getOrDefault(false)
        }
        rule.onNodeWithTag("empire-list").performScrollToIndex(0)
        rule.waitForIdle()
    }
    private fun scrollTo(text: String) = runCatching { rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true)) }

    private fun launch(scenario: String?, block: () -> Unit) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        if (scenario != null) intent.putExtra(MainActivity.EXTRA_SCENARIO, scenario)
        ActivityScenario.launch<MainActivity>(intent).use { block() }
    }


    @Test
    fun firstSession() {
        launch(null) {
            waitText("Open your kiosk")
            shot("01_welcome")
            tapText("Open your kiosk")
            waitText("Tap anywhere")
            shot("02_counter_first_minute")
            repeat(6) { rule.onNodeWithTag("counter-stage").performTouchInput { touchClick(center) }; rule.waitForIdle() }
            shot("02b_counter_tapping")
            tab("Empire")
            waitText("Your businesses")
            shot("02c_empire_first_minute")
            tab("Goals")
            waitText("Open for business")
            shot("02d_goals_first_minute")
        }
    }

    @Test
    fun midGame() {
        launch("midgame") {
            waitEmpire()
            Thread.sleep(1500)
            rule.waitForIdle()
            shot("03_empire_midgame")
            scrollTo("Your businesses")
            Thread.sleep(400)
            shot("04_businesses")
            scrollTo("Restaurants")
            shot("04b_businesses_scrolled")
            tab("Counter")
            Thread.sleep(800)
            shot("03b_counter_midgame")
            tab("Empire")
            waitEmpire()
            tapText("Retail Stores")
            waitText("Price level")
            shot("05_business_retail")
            back()
            tab("More")
            waitText("Achievements")
            shot("06a_more")
            runCatching {
                tab("Goals"); Thread.sleep(800); shot("06g_goals")
                scrollTo("Achievements"); tag("goals-achievements"); Thread.sleep(800); shot("06h_achievements"); back()
            }
            tab("More")
            waitText("Achievements")
            tag("door-dynasty")
            waitText("Your Dynasty")
            shot("06_dynasty")
            back()
            scrollTo("Settings")
            tapText("Settings")
            waitText("Appearance")
            shot("07_settings")
            scrollTo("Cheats")
            runCatching {
                tag("menu-cheats"); Thread.sleep(800); shot("07b_cheats_gate")
                tag("cheats-use-here"); tag("cheats-confirm"); Thread.sleep(500); shot("07c_cheats")
                tag("cheat-cash_day"); Thread.sleep(800); shot("07d_cheats_undo")
                back()
            }
            scrollTo("Save slots")
            runCatching { tag("menu-slots"); Thread.sleep(1500); shot("07e_save_slots"); back() }
        }
    }

    @Test
    fun markets() {
        launch("markets") {
            waitEmpire()
            tab("Markets")
            Thread.sleep(1000)
            shot("08_markets")
            runCatching { tag("market-sort"); Thread.sleep(500); scrollTo("Top gainers"); shot("08c_markets_sorted") ; repeat(5) { tag("market-sort") } }
            scrollTo("Wallmark")
            runCatching { tapText("Wallmark"); Thread.sleep(1000); shot("08b_stock_detail") }
        }
    }

    @Test
    fun lateGame() {
        launch("lategame") {
            waitEmpire(60_000)
            shot("09_empire_lategame")
            tab("Estates")
            Thread.sleep(800)
            shot("10_estates")
            scrollTo("Lisbon")
            runCatching { tapText("Lisbon"); Thread.sleep(1000); shot("10b_city_lisbon"); back() }
            more("collection")
            Thread.sleep(800)
            shot("11_collection")
            back()
            tag("menu-store")
            Thread.sleep(800)
            shot("12_store")
            scrollTo("Just for show")
            Thread.sleep(500)
            shot("12b_store_cosmetics")
        }
    }

    @Test
    fun worldDesk() {
        launch("world") {
            waitEmpire(60_000)
            runCatching { scrollTo("Fortune"); tapText("Desk"); Thread.sleep(800); shot("13_desk"); back() }
            runCatching { scrollTo("Fortune"); tapText("Fortune"); Thread.sleep(800); shot("13b_fortune"); back() }
            runCatching { scrollTo("Credit"); tapText("Credit"); Thread.sleep(800); shot("13c_finance"); back() }
            tab("More")
            waitText("Achievements")
            scrollTo("Account & cloud save")
            runCatching { tapText("Account"); Thread.sleep(800); shot("14_account") }
        }
    }

    @Test
    fun crown() {
        launch("crown") {
            waitEmpire(90_000)
            more("dynasty")
            waitText("Your Dynasty")
            rule.waitUntil(120_000) { rule.onAllNodesWithText("Pass the Crown").fetchSemanticsNodes().isNotEmpty() }
            shot("15_dynasty_crown_ready")
            tab("Empire")
            runCatching { waitEmpire(5_000) }
            scrollTo("The crown is ready")
            Thread.sleep(500)
            shot("15b_empire_crown_ready")
            tab("More")
            rule.waitUntil(30_000) { rule.onAllNodesWithText("Pass the Crown").fetchSemanticsNodes().isNotEmpty() }
            click(rule.onAllNodesWithText("Pass the Crown").onFirst())
            waitText("Heir trait")
            shot("16_crown")
        }
    }
}
