package com.screenmix.app

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotNotifier
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.ui.prompt.ScreenshotPromptContent
import com.screenmix.app.ui.theme.BoltScreenshotTheme

class ScreenshotPromptActivity : ComponentActivity() {
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val uri = pendingUri
        if (result.resultCode == RESULT_OK && uri != null) {
            ScreenshotNotifier.cancelPrompt(this, uri)
            val message = intent.getStringExtra(EXTRA_DELETE_SUCCESS_MESSAGE) ?: "Screenshot deleted"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else if (uri != null) {
            val message = intent.getStringExtra(EXTRA_DELETE_CANCEL_MESSAGE)
                ?: "Delete cancelled — screenshot copied to clipboard"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        finish()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
        )
        enableEdgeToEdge()

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString == null) {
            finish()
            return
        }
        val uri = Uri.parse(uriString)
        pendingUri = uri

        val deleteOnly = intent.getBooleanExtra(EXTRA_DELETE_ONLY, false)
        val shareOnlyMode = intent.hasExtra(EXTRA_SHARE_FLOW)
        val deleteAfterShare = intent.getBooleanExtra(EXTRA_SHARE_FLOW, true)
        val deleteSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DELETE_INTENT, IntentSender::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DELETE_INTENT)
        }
        if (deleteOnly && deleteSender != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deleteLauncher.launch(IntentSenderRequest.Builder(deleteSender).build())
            return
        }
        if (shareOnlyMode) {
            launchShareChooser(uri, deleteAfterShare)
            return
        }

        setContent {
            val prefs = ScreenshotPreferences(this)
            BoltScreenshotTheme(themeId = prefs.themeId) {
                ScreenshotPromptContent(
                    uri = uri,
                    copyRowOnTop = prefs.copyRowOnTop,
                    tapOutsideToDismiss = prefs.tapOutsideToDismiss,
                    onCopyDelete = {
                        ScreenshotActions.copyAndDelete(this, uri)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                            ScreenshotActions.hasAllFilesAccess(this)
                        ) {
                            finish()
                        }
                    },
                    onCopySave = {
                        ScreenshotActions.copyAndSave(this, uri)
                        finish()
                    },
                    onShareAndDelete = {
                        launchShareChooser(uri, deleteAfterShare = true)
                    },
                    onShareAndSave = {
                        launchShareChooser(uri, deleteAfterShare = false)
                    },
                    onDismiss = {
                        ScreenshotActions.dismissScreenshot(this, uri)
                        finish()
                    },
                )
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun launchShareChooser(uri: Uri, deleteAfterShare: Boolean) {
        val shareUri = if (deleteAfterShare) {
            ScreenshotActions.createTemporarySnapshot(this, uri)
        } else {
            uri
        }

        if (shareUri == null) {
            Toast.makeText(this, "Could not prepare screenshot for sharing", Toast.LENGTH_SHORT).show()
            return
        }

        ScreenshotActions.acknowledgeScreenshot(this, uri)
        val chooser = ScreenshotActions.buildShareChooserIntent(
            context = this,
            shareUri = shareUri,
            originalUri = uri,
            deleteAfterShare = deleteAfterShare,
        )

        runCatching { startActivity(chooser) }
            .onSuccess { finish() }
            .onFailure {
                Toast.makeText(this, "Could not open share menu", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_DELETE_ONLY = "extra_delete_only"
        const val EXTRA_DELETE_INTENT = "extra_delete_intent"
        const val EXTRA_DELETE_CANCEL_MESSAGE = "extra_delete_cancel_message"
        const val EXTRA_DELETE_SUCCESS_MESSAGE = "extra_delete_success_message"
        const val EXTRA_SHARE_FLOW = "extra_share_flow"
        const val EXTRA_SHARE_AND_DELETE = "extra_share_and_delete"
    }
}
