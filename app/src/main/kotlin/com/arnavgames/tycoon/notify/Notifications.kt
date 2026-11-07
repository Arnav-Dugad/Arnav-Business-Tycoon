package com.arnavgames.tycoon.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arnavgames.tycoon.MainActivity
import com.arnavgames.tycoon.R
import com.arnavgames.tycoon.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Local notifications (docs/PLAN_2.0.md E6): planned when the player leaves, cancelled when they return. Off until
 * the player turns them on in Settings; the Android 13+ permission is asked only at that moment.
 */
object Notifications {
    private const val TAG = "abt-notify"
    const val CHANNEL = "empire"

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun schedule(ctx: Context, plan: List<PlannedNotification>, nowWall: Long) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(TAG)
        plan.forEachIndexed { i, n ->
            val req = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInitialDelay((n.atWallMs - nowWall).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to n.title, "body" to n.body, "id" to i + 1))
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("$TAG-$i", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
        NotificationManagerCompat.from(ctx).cancelAll()
    }

    internal fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Your empire", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Finished expansions and renovations, auction results and a full Desk, while you're away."
            })
        }
    }
}

/** Posts one planned notification, re-checking the player's switches and the daily limit at the moment it fires. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx).settings.first()
        if (!settings.notifications || !Notifications.canPost(ctx)) return Result.success()
        // Daily limit, counted where it matters: on the device, at the moment of posting.
        val prefs = ctx.getSharedPreferences("notify", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val count = if (prefs.getString("day", "") == today) prefs.getInt("count", 0) else 0
        if (count >= NotificationPlanner.MAX_PER_DAY) return Result.success()
        prefs.edit().putString("day", today).putInt("count", count + 1).apply()

        Notifications.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = inputData.getString("body").orEmpty()
        val n = NotificationCompat.Builder(ctx, Notifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_crown)
            .setColor(0xFFC9A24B.toInt())
            .setContentTitle(inputData.getString("title"))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(inputData.getInt("id", 1), n)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to do.
        }
        return Result.success()
    }
}
