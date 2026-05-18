package com.kire.remotecontroller

import android.app.Application
import android.util.Log

class RemoteControllerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "Uncaught in ${thread.name}", error)
            defaultHandler?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val TAG = "RemoteControllerApp"
    }
}
