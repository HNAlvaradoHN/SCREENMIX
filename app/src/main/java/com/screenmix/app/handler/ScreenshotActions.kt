package com.screenmix.app.handler

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import com.screenmix.app.ScreenshotPromptActivity
import com.screenmix.app.receiver.ScreenshotActionReceiver

object ScreenshotActions {
    fun copyToClipboard(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val clip = ClipData.newUri(resolver, "Screenshot", uri)
        val clipboard = ContextCompat.getSystemService(context, android.content.ClipboardManager::class.java)
            ?: return false
        clipboard.setPrimaryClip(clip)
        return true
    }

    fun copyAndSave(context: Context, uri: Uri) {
        if (!copyToClipboard(context, uri)) {
            Toast.makeText(context, "Could not copy screenshot", Toast.LENGTH_SHORT).show()
            return
        }
        markHandled(context, uri)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun dismissScreenshot(context: Context, uri: Uri) {
        markHandled(context, uri)
    }

    fun launchShareAndDelete(context: Context, uri: Uri) {
        launchShareFlow(context, uri, deleteAfterShare = true)
    }

    fun launchShareAndSave(context: Context, uri: Uri) {
        launchShareFlow(context, uri, deleteAfterShare = false)
    }

    private fun launchShareFlow(context: Context, uri: Uri, deleteAfterShare: Boolean) {
        val intent = Intent(context, ScreenshotPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ScreenshotPromptActivity.EXTRA_URI, uri.toString())
            putExtra(ScreenshotPromptActivity.EXTRA_SHARE_FLOW, deleteAfterShare)
        }
        runCatching { context.startActivity(intent) }
            .onSuccess { acknowledgeScreenshot(context, uri) }
            .onFailure {
                Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
            }
    }

    fun onShareTargetChosen(context: Context, uri: Uri, deleteAfterShare: Boolean) {
        acknowledgeScreenshot(context, uri)
        if (deleteAfterShare) {
            executeShareDelete(context, uri)
        } else {
            Toast.makeText(context, "Shared and saved", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildShareChooserIntent(
        context: Context,
        shareUri: Uri,
        originalUri: Uri,
        deleteAfterShare: Boolean,
    ): Intent {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(shareUri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newRawUri("Screenshot", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val callbackIntent = Intent(context, ScreenshotActionReceiver::class.java).apply {
            action = ScreenshotActionReceiver.ACTION_SHARE_TARGET_CHOSEN
            putExtra(ScreenshotActionReceiver.EXTRA_URI, originalUri.toString())
            putExtra(ScreenshotActionReceiver.EXTRA_DELETE_AFTER_SHARE, deleteAfterShare)
        }
        val callbackFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val callback = PendingIntent.getBroadcast(
            context,
            (originalUri.toString() + deleteAfterShare).hashCode(),
            callbackIntent,
            callbackFlags,
        )

        return Intent.createChooser(share, null, callback.intentSender)
    }

    fun acknowledgeScreenshot(context: Context, uri: Uri) {
        markHandled(context, uri)
    }

    fun launchDeleteConfirmation(
        context: Context,
        uri: Uri,
        cancelMessage: String = "Delete cancelled — screenshot copied to clipboard",
        successMessage: String = "Screenshot deleted",
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            val intent = Intent(context, ScreenshotPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ScreenshotPromptActivity.EXTRA_URI, uri.toString())
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_ONLY, true)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_INTENT, pending.intentSender)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_CANCEL_MESSAGE, cancelMessage)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_SUCCESS_MESSAGE, successMessage)
            }
            context.startActivity(intent)
            return
        }
        Toast.makeText(
            context,
            "Could not delete — enable All files access in ScreenMix settings",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun executeShareDelete(context: Context, uri: Uri) {
        if (deleteScreenshotSilently(context, uri)) {
            Toast.makeText(context, "Shared and deleted", Toast.LENGTH_SHORT).show()
            return
        }

        launchDeleteConfirmation(
            context,
            uri,
            cancelMessage = "Delete cancelled — screenshot was shared",
            successMessage = "Shared and deleted",
        )
    }

    fun copyAndDelete(context: Context, uri: Uri) {
        val clipboardUri = createTemporarySnapshot(context, uri)
        if (clipboardUri == null || !copyToClipboard(context, clipboardUri)) {
            Toast.makeText(context, "Could not copy screenshot", Toast.LENGTH_SHORT).show()
            return
        }

        if (deleteScreenshotSilently(context, uri)) {
            Toast.makeText(context, "Copied and deleted", Toast.LENGTH_SHORT).show()
            return
        }

        requestDeleteWithSystemDialog(context, uri, successMessage = "Copied and deleted")
    }

    fun createTemporarySnapshot(context: Context, sourceUri: Uri): Uri? {
        cleanupClipboardCache(context)

        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri).orEmpty()
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "png"
        }

        val directory = File(context.cacheDir, CLIPBOARD_CACHE_DIR)
        if (!directory.exists() && !directory.mkdirs()) {
            return null
        }

        val snapshot = File(
            directory,
            "screenmix_clip_${System.currentTimeMillis()}.$extension",
        )

        val copied = runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                snapshot.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching false
            snapshot.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            snapshot.delete()
            return null
        }

        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                snapshot,
            )
        }.getOrElse {
            snapshot.delete()
            null
        }
    }

    fun cleanupClipboardCache(context: Context) {
        val directory = File(context.cacheDir, CLIPBOARD_CACHE_DIR)
        if (!directory.exists()) return

        val cutoff = System.currentTimeMillis() - CLIPBOARD_CACHE_MAX_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    fun deleteScreenshotSilently(context: Context, uri: Uri): Boolean {
        val deleted = tryDirectDelete(context.contentResolver, uri)
        if (deleted) {
            ScreenshotNotifier.cancelPrompt(context, uri)
        }
        return deleted
    }

    fun deleteScreenshot(context: Context, uri: Uri) {
        if (deleteScreenshotSilently(context, uri)) {
            Toast.makeText(context, "Screenshot deleted", Toast.LENGTH_SHORT).show()
            return
        }
        requestDeleteWithSystemDialog(context, uri)
    }

    fun hasAllFilesAccess(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun requestDeleteWithSystemDialog(
        context: Context,
        uri: Uri,
        deleteOnlyMessage: String = "Delete cancelled — screenshot copied to clipboard",
        successMessage: String = "Screenshot deleted",
    ) {
        launchDeleteConfirmation(context, uri, cancelMessage = deleteOnlyMessage, successMessage = successMessage)
    }

    private fun tryDirectDelete(resolver: ContentResolver, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            return false
        }
        return runCatching { resolver.delete(uri, null, null) }.getOrDefault(0) > 0
    }

    private fun markHandled(context: Context, uri: Uri) {
        ScreenshotPreferences(context).markSaved(uri.toString())
        ScreenshotNotifier.cancelPrompt(context, uri)
    }

    fun isScreenshot(displayName: String?, relativePath: String?): Boolean {
        val name = displayName.orEmpty().lowercase()
        val path = relativePath.orEmpty().lowercase()
        return name.contains("screenshot") ||
            path.contains("screenshot") ||
            path.contains("screen_shot") ||
            name.startsWith("scr_")
    }

    fun queryDisplayName(resolver: ContentResolver, uri: Uri): Pair<String?, String?> {
        resolver.query(
            uri,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) to cursor.getString(1)
            }
        }
        return null to null
    }

    private const val CLIPBOARD_CACHE_DIR = "clipboard"
    private const val CLIPBOARD_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
}
