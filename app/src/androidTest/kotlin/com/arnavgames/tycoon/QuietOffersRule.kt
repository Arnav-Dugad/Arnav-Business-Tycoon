package com.arnavgames.tycoon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Marks the one-time notification offer as already seen, so its dialog never covers a flow under test. Runs before
 * the activity starts (order 0). The offer itself is covered by its own screenshot and unit tests.
 */
class QuietOffersRule : ExternalResource() {
    override fun before() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("intros", Context.MODE_PRIVATE)
            .edit().putBoolean("notify-offer", true).commit()
    }
}
