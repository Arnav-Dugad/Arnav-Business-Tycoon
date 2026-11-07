package com.arnavgames.tycoon

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arnavgames.tycoon.game.AppServices
import com.arnavgames.tycoon.game.GameSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TycoonApp : Application() {
    @Inject lateinit var session: GameSession
    @Inject lateinit var services: AppServices

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Catch main-thread I/O and leaks early (performance target: no jank).
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        session.start()
        // The game ticks only while the app is visible; offline time is credited on return.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { session.onForeground(); services.onForeground() }
            override fun onStop(owner: LifecycleOwner) { session.onBackground(); services.onBackground() }
        })
    }
}
