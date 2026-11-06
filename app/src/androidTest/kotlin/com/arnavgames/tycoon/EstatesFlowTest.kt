package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST_PLAN §4 flow 4: open the map → a city → a district → buy a property → set the rent → renovate.
 * Starts from the debug "markets" scenario (Chapter 1 completed with real commands, so Estates are open).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EstatesFlowTest {
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "markets")
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun close() { scenario?.close() }

    private fun waitForText(text: String, timeout: Long = 15_000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40).take(5000), e)
    }

    private fun scrollTo(text: String) = rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))

    private fun clickText(text: String) = rule.onAllNodesWithText(text, substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun mapCityDistrictBuyRentRenovate() {
        waitForText("Main Street", timeout = 30_000)
        rule.onNodeWithContentDescription("Estates", substring = true).performClick()
        waitForText("PROPERTY")
        waitForText("The map")

        // City list under the map → Lisbon → its first district.
        scrollTo("Lisbon")
        clickText("districts")
        waitForText("Districts")
        scrollTo("yield")
        clickText("yield")
        waitForText("Listings")

        // Select the first listing and buy it.
        scrollTo("Condition")
        clickText("Size ×")
        scrollTo("Buy ·")
        rule.onNode(hasText("Buy ·", substring = true) and isEnabled()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("You own here")

        // Open it, change the rent, start a renovation.
        scrollTo("Finding a tenant")
        clickText("Finding a tenant")
        waitForText("Asking rent")
        scrollTo("Asking rent")
        rule.onAllNodesWithText("90%", substring = false).onFirst().performClick()
        scrollTo("Renovate ·")
        rule.onNode(hasText("Renovate ·", substring = true) and isEnabled()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Renovation under way")
    }
}
