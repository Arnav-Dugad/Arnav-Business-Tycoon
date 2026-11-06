package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST_PLAN §4 flow 3: buy a stock → sell it → the portfolio and cash update; short selling is locked before
 * Chapter 5. Starts from the debug "markets" scenario (Chapter 1 completed with real commands).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MarketsFlowTest {
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

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 15_000) = try {
        waitForTextInner(text, substring, timeout)
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().printToString(maxDepth = 40).take(6000), e)
    }

    private fun waitForTextInner(text: String, substring: Boolean, timeout: Long) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun buySellAndShortLock() {
        // The scenario finishes Chapter 1, which opens the exchange.
        waitForText("Main Street", timeout = 30_000)
        rule.onNodeWithContentDescription("Markets", substring = true).performClick()
        waitForText("PORTFOLIO")
        waitForText("Exchange")

        // Open the first stock in the list.
        scrollTo("· Defensive")
        rule.onAllNodesWithText("· Defensive", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("24-hour change")
        scrollTo("Buy ·")
        rule.onNode(hasText("Buy ·", substring = true) and isEnabled()).performClick()

        // The position appears on the instrument and in the portfolio on the Markets tab.
        waitForText("Your position")
        waitForText("Gain or loss")
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Your positions")
        scrollTo("· Defensive")
        rule.onAllNodesWithText("· Defensive", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("24-hour change")
        scrollTo("Trade")
        rule.onAllNodesWithText("Sell", substring = false).onFirst().performClick()
        rule.onNodeWithText("Max").performClick()
        scrollTo("Sell ·")
        rule.onNode(hasText("Sell ·", substring = true) and isEnabled()).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Gain or loss").fetchSemanticsNodes().isEmpty() || rule.onAllNodesWithText("You don't own any yet.").fetchSemanticsNodes().isNotEmpty() }

        // Short selling is locked before Chapter 5.
        rule.onAllNodesWithText("Short", substring = false).onFirst().performClick()
        waitForText("Short selling unlocks in chapter 5")
    }
}
