package com.arnavgames.tycoon.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The journeys a player makes in the first minutes: cold start, the welcome screen, every tab and a scroll through
 * the Empire list. Code on these paths is compiled ahead of time from the profile, so the first session is smooth.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun firstSession() = rule.collect(packageName = "com.arnavgames.tycoon", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Open your kiosk")), 10_000)
        device.findObject(By.text("Open your kiosk"))?.click()
        device.wait(Until.hasObject(By.descStartsWith("Dynasty")), 10_000)
        for (tab in listOf("Markets", "Estates", "Collection", "Dynasty", "Empire")) {
            device.findObject(By.descStartsWith(tab))?.click()
            device.waitForIdle()
        }
        // Scroll the Empire list down and back up with screen swipes (list nodes are recreated while scrolling).
        val x = device.displayWidth / 2
        val top = device.displayHeight / 4
        val bottom = device.displayHeight * 3 / 4
        repeat(3) { device.swipe(x, bottom, x, top, 20); device.waitForIdle() }
        repeat(3) { device.swipe(x, top, x, bottom, 20); device.waitForIdle() }
    }
}
