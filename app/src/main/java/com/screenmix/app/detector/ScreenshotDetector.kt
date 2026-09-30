package com.screenmix.app.detector

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotPreferences

class ScreenshotDetector(
    private val context: Context,
    private val onScreenshot: (Uri) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences = ScreenshotPreferences(context)
    private var lastUri: Uri? = null
    private var lastDetectedAt = 0L

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            scheduleCheck()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleCheck()
        }
    }

    private val checkRunnable = Runnable { checkLatestScreenshot() }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun stop() {
        handler.removeCallbacks(checkRunnable)
        context.contentResolver.unregisterContentObserver(observer)
    }

    private fun scheduleCheck() {
        handler.removeCallbacks(checkRunnable)
        val delay = preferences.detectionDelayMs
        if (delay <= 0L) {
            handler.post(checkRunnable)
        } else {
            handler.postDelayed(checkRunnable, delay)
        }
    }

    private fun checkLatestScreenshot() {
        val resolver = context.contentResolver
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
            add(MediaStore.Images.Media.DATE_ADDED)
        }.toTypedArray()

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return

            val id = cursor.getLong(0)
            val name = cursor.getString(1)
            val path = cursor.getString(2)
            val dateAdded = cursor.getLong(3)

            if (!ScreenshotActions.isScreenshot(name, path)) return

            val nowSeconds = System.currentTimeMillis() / 1000
            if (dateAdded < nowSeconds - RECENT_WINDOW_SECONDS) return

            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toString(),
            )
            if (preferences.isSaved(uri.toString())) return

            val now = System.currentTimeMillis()
            if (uri == lastUri && now - lastDetectedAt < DUPLICATE_WINDOW_MS) return

            lastUri = uri
            lastDetectedAt = now
            onScreenshot(uri)
        }
    }

    companion object {
        private const val RECENT_WINDOW_SECONDS = 30L
        private const val DUPLICATE_WINDOW_MS = 2_500L
    }
}
