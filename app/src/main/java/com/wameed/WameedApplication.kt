package com.wameed

import android.app.Application

class WameedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WameedLogger.init(this)
        WameedCrashReporter.initialize(this)
    }
}
