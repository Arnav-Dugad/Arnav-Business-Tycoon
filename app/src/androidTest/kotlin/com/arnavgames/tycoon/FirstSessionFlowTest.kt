package com.arnavgames.tycoon

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST_PLAN §4 flows 1–2 on a fresh install (AGP installs a clean app for each connected-test run):
 * welcome → open kiosk → claim → hustle → buy levels → business detail → expansion → rush with Favor.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FirstSessionFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 10_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun tapCounter(times: Int) {
        val counter = rule.onNodeWithContentDescription("Serve a customer", substring = true)
        repeat(times) { counter.performClick(); rule.waitForIdle() }
    }

    private fun nav(tab: String) { rule.onNodeWithContentDescription(tab, substring = true).performClick(); rule.waitForIdle() }

    /** Counter tab → serve [times] customers → back to the Empire tab (where the businesses are). */
    private fun counterRound(times: Int) {
        nav("Counter")
        waitForText("a customer")
        tapCounter(times)
        nav("Empire")
        waitForText("Your businesses")
    }

    /** One burst of play: work the counter, then collect the till if it has money. */
    private fun earnRound() {
        counterRound(25)
        scrollBusinessesTo("Collect")
        val collect = rule.onAllNodes(hasText("Collect") and isEnabled()).fetchSemanticsNodes()
        if (collect.isNotEmpty()) rule.onNode(hasText("Collect") and isEnabled()).tap()
    }

    private fun scrollBusinessesTo(text: String) {
        businessesList().performScrollToNode(hasText(text, substring = true))
    }

    /**
     * Clicks through the node's semantics action. A button scrolled just into view can sit under the floating tab
     * bar, where a touch at its centre would land on a tab instead.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.tap() =
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

    /** The Empire tab's list (by tag: other scrollable containers can be on screen during transitions). */
    private fun businessesList() = rule.onNodeWithTag("empire-list")

    @Test
    fun firstSessionFromWelcomeToRushedExpansion() {
        // Flow 1: onboarding
        waitForText("Open your kiosk", timeout = 20_000)
        rule.onNodeWithText("Open your kiosk").performClick()
        waitForText("a customer")
        nav("Empire")
        rule.onNodeWithTag("empire-list").performScrollToNode(hasText("Claim $", substring = true))
        waitForText("Claim $")
        rule.onNodeWithText("Claim $", substring = true).performClick()

        // Serve customers on the Counter tab until the 60-customer mission completes, then claim its Favor on Empire.
        nav("Counter")
        tapCounter(60)
        nav("Empire")
        rule.onNodeWithTag("empire-list").performScrollToNode(hasText("Claim $", substring = true))
        waitForText("Claim $")
        rule.onNodeWithText("Claim $", substring = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("5 Favor")).fetchSemanticsNodes().isNotEmpty() }

        // Buy levels on the Empire tab until Retail reaches level 5 (work the counter if cash runs out).
        nav("Empire")
        waitForText("Your businesses")
        var guard = 0
        while (rule.onAllNodesWithText("Lv 5", substring = true).fetchSemanticsNodes().isEmpty() && guard++ < 60) {
            scrollBusinessesTo("Buy ×1")
            val buy = rule.onAllNodes(hasText("Buy ×1", substring = true) and isEnabled()).fetchSemanticsNodes()
            if (buy.isNotEmpty()) rule.onNode(hasText("Buy ×1", substring = true) and isEnabled()).tap() else counterRound(10)
            rule.waitForIdle()
        }
        waitForText("Lv 5")

        // Earn enough for the Weekend Market Stall the way a player would: work the counter and bank the till.
        // (Prices come from tuned content, so the test earns until the button is enabled instead of assuming a price.)
        var buildEnabled = false
        for (attempt in 0 until 3) {
            repeat(10) { earnRound() }
            scrollBusinessesTo("Retail Stores")
            rule.onNodeWithText("Retail Stores").tap()
            waitForText("Price level")
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Weekend Market Stall"))
            buildEnabled = rule.onAllNodes(hasText("Build ·", substring = true) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            if (buildEnabled) break
            rule.onNodeWithContentDescription("Back").performClick()
            waitForText("Your businesses")
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Price level").fetchSemanticsNodes().isEmpty() }
        }
        check(buildEnabled) { "could not afford the expansion after earning" }
        rule.onNode(hasText("Build ·", substring = true) and isEnabled()).performClick()
        waitForText("Rush ·")
        rule.onNodeWithText("Rush ·", substring = true).performClick()
        // Completed expansions no longer show a Build or Rush action.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Rush ·", substring = true).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Your businesses")
    }

}
