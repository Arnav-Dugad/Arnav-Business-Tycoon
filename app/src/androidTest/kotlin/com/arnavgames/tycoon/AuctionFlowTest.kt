package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
 * TEST_PLAN §4 flow 5: a live auction (the player's proxy bid is in, 45 s before the hammer) → the hammer falls →
 * the piece appears in a showroom. Starts from the debug "auction" scenario.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuctionFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "auction")
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun close() { scenario?.close() }

    private fun waitForText(text: String, timeout: Long = 15_000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40).take(5000), e)
    }

    @Test
    fun proxyBidWinsAndThePieceIsShown() {
        waitForText("Raw Power", timeout = 60_000)
        rule.onNodeWithContentDescription("Collection", substring = true).performClick()
        waitForText("STATUS")
        waitForText("Live auction", timeout = 30_000)
        rule.onAllNodesWithText("Live auction", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("CURRENT BID")
        waitForText("You lead")
        // The proxy answers any late rival; wait for the hammer.
        waitForText("The hammer has fallen", timeout = 150_000)
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Auction house")
        waitForText("1/")
    }
}
