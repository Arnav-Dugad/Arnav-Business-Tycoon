package com.arnavgames.tycoon

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** Saves a full-device screenshot when a UI test fails (pull with: adb pull /sdcard/Android/data/<pkg>/files/test-failures). */
class FailureScreenshotRule : TestWatcher() {
    override fun failed(e: Throwable?, description: Description) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(inst.targetContext.getExternalFilesDir(null), "test-failures").apply { mkdirs() }
        File(dir, "${description.methodName}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
