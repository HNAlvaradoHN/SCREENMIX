package com.screenmix.app

import android.app.Application
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.service.ScreenshotMonitorService

class ScreenshotApplication : Application() {
    lateinit var preferences: ScreenshotPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = ScreenshotPreferences(this)
        ScreenshotActions.cleanupClipboardCache(this)
        if (preferences.isMonitorEnabled) {
            ScreenshotMonitorService.start(this)
        }
    }
}
