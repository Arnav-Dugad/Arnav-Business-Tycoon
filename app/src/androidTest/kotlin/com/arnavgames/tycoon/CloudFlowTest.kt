package com.arnavgames.tycoon

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * TEST_PLAN §4 flows 10 and 11, against the local Firebase Emulator Suite (start it with
 * `firebase emulators:start --only auth,firestore` in firebase/): sign up → verification banner → sign out → sign in;
 * guest → upgrade; and a sync conflict where both copies changed, resolved without losing anything.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CloudFlowTest {
    @get:Rule(order = 0) val quiet = QuietOffersRule()
    @get:Rule(order = 2) val shots = FailureScreenshotRule()
    @get:Rule(order = 1) val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val project = "demo-arnav-business-tycoon"

    private fun emulator(method: String, path: String, body: String? = null) {
        val c = URL("http://10.0.2.2:$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        check(c.responseCode in 200..299) { "emulator $method $path -> ${c.responseCode}" }
        c.disconnect()
    }

    @Before
    fun launch() {
        emulator("DELETE", "9099/emulator/v1/projects/$project/accounts")
        emulator("DELETE", "8080/emulator/v1/projects/$project/databases/(default)/documents")
        FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).firstOrNull { it.name == "abt-emulator" }?.let { FirebaseAuth.getInstance(it).signOut() }
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

    private fun waitForText(text: String, timeout: Long = 20_000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        throw AssertionError("'$text' not found. Screen: " + rule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40).take(5000), e)
    }

    private fun tap(text: String) = rule.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    private fun openAccount() {
        waitForMidgame()
        rule.onNodeWithContentDescription("Dynasty", substring = true).performClick()
        waitForText("Your Dynasty")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Account & cloud save"))
        tap("Account & cloud save")
        waitForText("Test server")
    }

    @Test
    fun signUpVerifySignOutSignIn() {
        openAccount()
        rule.onNodeWithTag("email").performTextInput("founder@example.com")
        rule.onNodeWithTag("password").performTextInput("kiosk-to-crown")
        tap("Create account")
        waitForText("founder@example.com")
        waitForText("Verify your email")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Sign out"))
        tap("Sign out")
        waitForText("Sign in or create an account")
        rule.onNodeWithTag("email").performTextInput("founder@example.com")
        rule.onNodeWithTag("password").performTextInput("kiosk-to-crown")
        tap("Sign in")
        waitForText("founder@example.com")
    }

    @Test
    fun guestUpgradesAndAConflictIsResolvedWithoutLoss() {
        openAccount()
        tap("Continue as guest")
        waitForText("Guest account")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Sync now"))
        rule.onNodeWithTag("sync-now").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Synced revision 1")
        // Another device saves (revision 5) while this one keeps playing.
        val app = FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).first { it.name == "abt-emulator" }
        val uid = FirebaseAuth.getInstance(app).currentUser!!.uid
        emulator("PATCH", "8080/v1/projects/$project/databases/(default)/documents/saves/$uid?updateMask.fieldPaths=rev&updateMask.fieldPaths=device",
            """{"fields":{"rev":{"integerValue":"5"},"device":{"stringValue":"Tablet"}}}""")
        Thread.sleep(62_000) // the game moves on by at least one game minute
        rule.onNodeWithTag("sync-now").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Two different saves")
        waitForText("Tablet")
        rule.onNodeWithTag("keep-local").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Synced revision 6", timeout = 30_000)
    }

    @Test
    fun verifiedPlayersPublishAndSeeTheLeaderboard() {
        openAccount()
        rule.onNodeWithTag("email").performTextInput("social@example.com")
        rule.onNodeWithTag("password").performTextInput("friends-and-lots")
        tap("Create account")
        waitForText("Verify your email")
        // The Auth emulator lets the test mark the email as verified (as clicking the link would).
        val app = FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).first { it.name == "abt-emulator" }
        val uid = FirebaseAuth.getInstance(app).currentUser!!.uid
        emulator("POST", "9099/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-api-key", """{"localId":"$uid","emailVerified":true}""")
        // The app may notice the verification on its own (token refresh); otherwise press "I've verified".
        runCatching {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag("verified-check"))
            rule.onNodeWithTag("verified-check").performSemanticsAction(SemanticsActions.OnClick)
        }
        waitForText("Verified")
        rule.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(15_000) { rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Friends & leaderboards"))
        tap("Friends & leaderboards")
        waitForText("YOUR FRIEND CODE")
        waitForText("(you)", timeout = 30_000)
    }
}
