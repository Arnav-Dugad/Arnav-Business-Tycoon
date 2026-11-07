package com.arnavgames.tycoon.screens

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
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
            waitText("Work the counter")
            shot("02_empire_first_minute")
        }
    }

    @Test
    fun midGame() {
        launch("midgame") {
            waitText("Retail Stores")
            Thread.sleep(1500)
            rule.waitForIdle()
            shot("03_empire_midgame")
            scrollTo("Restaurants")
            shot("04_empire_businesses")
            tapText("Retail Stores")
            waitText("Price level")
            shot("05_business_retail")
            back()
            tab("Dynasty")
            waitText("Your Dynasty")
            shot("06_dynasty")
            scrollTo("Settings")
            tapText("Settings")
            waitText("Appearance")
            shot("07_settings")
        }
    }

    @Test
    fun markets() {
        launch("markets") {
            waitText("Retail Stores")
            tab("Markets")
            Thread.sleep(1000)
            shot("08_markets")
            scrollTo("Wallmark")
            runCatching { tapText("Wallmark"); Thread.sleep(1000); shot("08b_stock_detail") }
        }
    }

    @Test
    fun lateGame() {
        launch("lategame") {
            waitText("Retail Stores", 60_000)
            shot("09_empire_lategame")
            tab("Estates")
            Thread.sleep(800)
            shot("10_estates")
            scrollTo("Lisbon")
            runCatching { tapText("Lisbon"); Thread.sleep(1000); shot("10b_city_lisbon"); back() }
            tab("Collection")
            Thread.sleep(800)
            shot("11_collection")
            tab("Dynasty")
            waitText("Your Dynasty")
            scrollTo("Store (demo)")
            tapText("Store (demo)")
            Thread.sleep(800)
            shot("12_store")
        }
    }

    @Test
    fun worldDesk() {
        launch("world") {
            waitText("Retail Stores", 60_000)
            runCatching { tapText("Desk"); Thread.sleep(800); shot("13_desk"); back() }
            runCatching { tapText("Fortune"); Thread.sleep(800); shot("13b_fortune"); back() }
            runCatching { tapText("Credit"); Thread.sleep(800); shot("13c_finance"); back() }
            tab("Dynasty")
            waitText("Your Dynasty")
            scrollTo("Account & cloud save")
            runCatching { tapText("Account"); Thread.sleep(800); shot("14_account") }
        }
    }

    @Test
    fun crown() {
        launch("crown") {
            waitText("Retail Stores", 90_000)
            tab("Dynasty")
            waitText("Your Dynasty")
            rule.waitUntil(120_000) { rule.onAllNodesWithText("Pass the Crown").fetchSemanticsNodes().isNotEmpty() }
            shot("15_dynasty_crown_ready")
            click(rule.onAllNodesWithText("Pass the Crown").onFirst())
            waitText("Heir trait")
            shot("16_crown")
        }
    }
}
