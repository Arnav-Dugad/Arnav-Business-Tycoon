package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
 * TEST_PLAN §4 flow 7: Dynasty prestige with confirmation. From the debug "crown" scenario (chapter 8 finished), open
 * the Dynasty tab, pass the crown with the default trait, confirm, and land in generation 2.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CrownFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "crown")
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

    @Test
    fun passTheCrownStartsGenerationTwo() {
        waitForText("Your businesses", timeout = 60_000)
        rule.onNodeWithContentDescription("More", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("door-dynasty").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Your Dynasty")
        // The scenario plays to the end of chapter 8 in the background; the button appears when it is done.
        rule.waitUntil(400_000) { rule.onAllNodesWithText("Pass the Crown").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Pass the Crown").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Heir trait")
        rule.onNodeWithTag("crown-go").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Retire the founder?")
        rule.onNodeWithTag("crown-confirm").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Generation 2", timeout = 30_000)
    }
}
