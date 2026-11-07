package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * TEST_PLAN §4 flow 8: the demo purchase sheet shows the DEMO label → the product is granted → the Boosted flag is set.
 * Starts from the debug "midgame" scenario.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StoreFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "midgame")
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun close() { scenario?.close() }

    /** The midgame scenario is ready once the Empire list can scroll to Restaurants (it may be below the fold on short screens). */
    private fun waitForMidgame(timeout: Long = 60_000) {
        rule.waitUntil(timeout) {
            runCatching { rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Restaurants", substring = true)); true }.getOrDefault(false)
        }
    }

    private fun waitForText(text: String, timeout: Long = 15_000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40).take(5000), e)
    }

    @Test
    fun demoPurchaseIsLabelledAndSetsBoosted() {
        waitForMidgame()
        rule.onNodeWithContentDescription("Dynasty", substring = true).performClick()
        waitForText("Your Dynasty")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Store (demo)"))
        rule.onAllNodesWithText("Store (demo)").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("DEMO STORE")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("demo-bullion_100"))
        rule.onNodeWithTag("demo-bullion_100").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("DEMO PURCHASE — no real money is charged")
        rule.onNodeWithTag("demo-confirm").performSemanticsAction(SemanticsActions.OnClick)
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("boosted-badge"))
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Boosted").fetchSemanticsNodes().isNotEmpty() }
    }
}
