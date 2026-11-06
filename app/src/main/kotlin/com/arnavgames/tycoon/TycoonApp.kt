package com.arnavgames.tycoon

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TycoonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Catch main-thread I/O and leaks early (performance target: no jank).
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
    }
}
