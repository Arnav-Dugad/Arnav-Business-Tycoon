package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * TEST_PLAN §4 flow 6: resolve a decision card on the Desk, then self-file a tax return. Starts from the debug "world"
 * scenario (late game played through a full tax quarter, so cards are waiting and a return is due).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DeskAndTaxFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "world")
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun close() { scenario?.close() }

    private fun waitForText(text: String, timeout: Long = 15_000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40).take(5000), e)
    }

    /** Scrolls the screen's list until a node with [text] is composed (lazy lists only build what is visible). */
    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))
    }

    private fun tap(text: String) = rule.onAllNodesWithText(text, substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun resolveACardAndFileTaxes() {
        // The Desk is a door on the More tab; its badge says when decisions are waiting.
        waitForText("Your businesses", timeout = 240_000)
        rule.onNodeWithContentDescription("More", substring = true).performClick()
        waitForText("waiting", timeout = 120_000)
        tap("Desk")
        waitForText("Decisions")
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("decision-card").fetchSemanticsNodes().isNotEmpty() }
        // The scenario leaves the Desk full (three cards); deciding one frees a slot.
        waitForText("Desk full")
        rule.onAllNodesWithTag("card-option").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Next in")
        rule.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Credit")
        tap("Credit")
        waitForText("CREDIT SCORE")
        scrollTo("File quarter")
        tap("File quarter")
        waitForText("Deductions you earned")
        scrollTo("File and claim")
        tap("File and claim")
        scrollTo("Last return")
        waitForText("Last return", timeout = 20_000)
    }
}
