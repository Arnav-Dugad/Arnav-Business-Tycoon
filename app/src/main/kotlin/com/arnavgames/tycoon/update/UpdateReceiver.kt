package com.arnavgames.tycoon.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.arnavgames.tycoon.game.AppServices
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives the result of an update install session started by [UpdateManager] (not exported). */
@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {
    @Inject lateinit var services: AppServices

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val updates = services.updates
        val fromUser = intent.getBooleanExtra(EXTRA_FROM_USER, false)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                updates.onNeedsConfirmation(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java), fromUser, foreground)
            }
            // On success Android replaces (and stops) this process; nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.onInstallFailed(null)
            else -> updates.onInstallFailed(
                when (status) {
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update can't be installed over your version."
                    else -> "The update couldn't be installed (${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: status})."
                },
            )
        }
    }

    companion object {
        const val ACTION = "com.arnavgames.tycoon.UPDATE_STATUS"
        const val EXTRA_FROM_USER = "fromUser"
    }
}
