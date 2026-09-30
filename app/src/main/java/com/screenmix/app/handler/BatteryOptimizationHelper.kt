package com.screenmix.app.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    fun isUnrestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestUnrestricted(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val packageUri = Uri.parse("package:${context.packageName}")
        val directRequest = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri,
        )
        val fallbackList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

        runCatching { context.startActivity(directRequest) }
            .recoverCatching { context.startActivity(fallbackList) }
            .recoverCatching { context.startActivity(appDetails) }
    }
}
