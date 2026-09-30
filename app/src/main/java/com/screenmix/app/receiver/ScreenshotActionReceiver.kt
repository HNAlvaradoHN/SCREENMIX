package com.screenmix.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotNotifier

class ScreenshotActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        val uri = Uri.parse(uriString)

        when (intent.action) {
            ACTION_COPY_DELETE -> ScreenshotActions.copyAndDelete(context, uri)
            ACTION_COPY_SAVE -> ScreenshotActions.copyAndSave(context, uri)
            ACTION_DISMISS -> ScreenshotActions.dismissScreenshot(context, uri)
            else -> return
        }

        ScreenshotNotifier.cancelPrompt(context, uri)
    }

    companion object {
        const val ACTION_COPY_DELETE = "com.screenmix.app.COPY_DELETE"
        const val ACTION_COPY_SAVE = "com.screenmix.app.COPY_SAVE"
        const val ACTION_DISMISS = "com.screenmix.app.DISMISS"
        const val EXTRA_URI = "extra_uri"
    }
}
