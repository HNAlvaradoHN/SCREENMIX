package com.screenmix.app.handler

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.screenmix.app.R
import com.screenmix.app.ScreenshotPromptActivity
import java.io.File

object ScreenshotActions {
    fun copyToClipboard(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val clip = ClipData.newUri(resolver, "Screenshot", uri)
        val clipboard = ContextCompat.getSystemService(
            context,
            android.content.ClipboardManager::class.java,
        ) ?: return false
        clipboard.setPrimaryClip(clip)
        return true
    }

    fun copyAndSave(context: Context, uri: Uri) {
        val localized = LocaleHelper.wrap(context)
        if (!copyToClipboard(context, uri)) {
            Toast.makeText(localized, localized.getString(R.string.could_not_copy), Toast.LENGTH_SHORT).show()
            return
        }
        markHandled(context, uri)
        Toast.makeText(localized, localized.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    fun copyAndDelete(context: Context, uri: Uri) {
        val localized = LocaleHelper.wrap(context)
        val clipboardUri = createTemporarySnapshot(context, uri)
        if (clipboardUri == null || !copyToClipboard(context, clipboardUri)) {
            Toast.makeText(localized, localized.getString(R.string.could_not_copy), Toast.LENGTH_SHORT).show()
            return
        }

        if (deleteScreenshotSilently(context, uri)) {
            Toast.makeText(localized, localized.getString(R.string.copied_and_deleted), Toast.LENGTH_SHORT).show()
            return
        }

        requestDeleteWithSystemDialog(
            context,
            uri,
            successMessage = localized.getString(R.string.copied_and_deleted),
        )
    }

    fun dismissScreenshot(context: Context, uri: Uri) {
        markHandled(context, uri)
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
        val localized = LocaleHelper.wrap(context)
        if (deleteScreenshotSilently(context, uri)) {
            Toast.makeText(localized, localized.getString(R.string.screenshot_deleted), Toast.LENGTH_SHORT).show()
            return
        }
        requestDeleteWithSystemDialog(context, uri)
    }

    fun hasAllFilesAccess(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
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
        deleteOnlyMessage: String? = null,
        successMessage: String? = null,
    ) {
        val localized = LocaleHelper.wrap(context)
        val resolvedCancelMessage =
            deleteOnlyMessage ?: localized.getString(R.string.delete_cancelled_copied)
        val resolvedSuccessMessage =
            successMessage ?: localized.getString(R.string.screenshot_deleted)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            val intent = Intent(context, ScreenshotPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ScreenshotPromptActivity.EXTRA_URI, uri.toString())
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_ONLY, true)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_INTENT, pending.intentSender)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_CANCEL_MESSAGE, resolvedCancelMessage)
                putExtra(ScreenshotPromptActivity.EXTRA_DELETE_SUCCESS_MESSAGE, resolvedSuccessMessage)
            }
            context.startActivity(intent)
            return
        }

        Toast.makeText(
            localized,
            localized.getString(R.string.could_not_delete),
            Toast.LENGTH_LONG,
        ).show()
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
