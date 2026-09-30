package com.screenmix.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.service.ScreenshotMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = ScreenshotPreferences(context)
        if (!prefs.isMonitorEnabled) {
            return
        }

        runCatching {
            ScreenshotMonitorService.start(context.applicationContext)
        }
    }
}
