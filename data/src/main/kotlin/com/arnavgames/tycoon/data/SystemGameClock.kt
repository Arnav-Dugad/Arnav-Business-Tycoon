package com.arnavgames.tycoon.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.arnavgames.tycoon.core.time.GameClock

/** Real device clocks. Boot id comes from the global boot counter so monotonic times are only compared within one boot. */
class SystemGameClock(private val context: Context) : GameClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()
    override fun bootId(): String {
        val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        // Fallback: derive the boot instant from wall − uptime, rounded to the minute.
        return count?.toString() ?: "t" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000)
    }
}
