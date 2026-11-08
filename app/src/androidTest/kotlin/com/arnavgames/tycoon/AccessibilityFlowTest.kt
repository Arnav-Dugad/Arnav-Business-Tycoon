package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST_PLAN §4 flow 12 (automated part): on every main tab, each tappable element exposes a label for TalkBack
 * (its own text or a content description). From the debug "lategame" scenario so every tab has content.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SCENARIO, "lategame")
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun close() { scenario?.close() }

    private val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    private fun unlabeled(): List<String> = rule.onAllNodes(clickable).fetchSemanticsNodes().filter { n ->
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("")?.isNotBlank() == true
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("")?.isNotBlank() == true
        val label = n.config.getOrNull(SemanticsActions.OnClick)?.label?.isNotBlank() == true
        !(text || desc || label)
    }.map { it.boundsInRoot.toString() }

    @Test
    fun everyTappableElementHasALabel() {
        rule.waitUntil(120_000) { rule.onAllNodesWithText("Manage businesses", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(120_000) { rule.onAllNodesWithText("Desk", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val problems = mutableMapOf<String, List<String>>()
        for (tab in listOf("Empire", "Businesses", "Markets", "Estates", "More")) {
            rule.onNodeWithContentDescription(tab, substring = true).performClick()
            rule.waitForIdle()
            Thread.sleep(800)
            unlabeled().takeIf { it.isNotEmpty() }?.let { problems[tab] = it }
        }
        assertTrue("Unlabeled tappable elements: $problems", problems.isEmpty())
    }
}
