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

    private var candidate: Candidate? = null
    private val announcedUris = LinkedHashSet<String>()

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            scheduleCandidateCheck(EVENT_SETTLE_MS)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleCandidateCheck(EVENT_SETTLE_MS)
        }
    }

    private val checkRunnable = Runnable { checkLatestScreenshot() }
    private val deliverRunnable = Runnable { deliverIfStillStable() }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun stop() {
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(deliverRunnable)
        candidate = null
        context.contentResolver.unregisterContentObserver(observer)
    }

    private fun scheduleCandidateCheck(delayMs: Long) {
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(deliverRunnable)
        handler.postDelayed(checkRunnable, delayMs)
    }

    private fun checkLatestScreenshot() {
        val snapshot = findLatestRecentScreenshot() ?: run {
            candidate = null
            return
        }

        val uriKey = snapshot.uri.toString()
        if (preferences.isSaved(uriKey) || announcedUris.contains(uriKey)) {
            if (candidate?.snapshot?.uri == snapshot.uri) {
                candidate = null
            }
            return
        }

        if (snapshot.isPending) {
            candidate = Candidate(snapshot = snapshot, stableChecks = 0)
            handler.postDelayed(checkRunnable, STABILITY_SAMPLE_MS)
            return
        }

        val current = candidate
        if (current == null || current.snapshot.uri != snapshot.uri) {
            candidate = Candidate(snapshot = snapshot, stableChecks = 0)
            handler.postDelayed(checkRunnable, STABILITY_SAMPLE_MS)
            return
        }

        if (!snapshot.sameFileStateAs(current.snapshot)) {
            candidate = Candidate(snapshot = snapshot, stableChecks = 0)
            handler.postDelayed(checkRunnable, STABILITY_SAMPLE_MS)
            return
        }

        val stableChecks = current.stableChecks + 1
        candidate = Candidate(snapshot = snapshot, stableChecks = stableChecks)

        if (stableChecks < REQUIRED_STABLE_CHECKS) {
            handler.postDelayed(checkRunnable, STABILITY_SAMPLE_MS)
            return
        }

        handler.removeCallbacks(deliverRunnable)
        handler.postDelayed(
            deliverRunnable,
            preferences.detectionDelayMs.coerceAtLeast(0L),
        )
    }

    private fun deliverIfStillStable() {
        val expected = candidate?.snapshot ?: return
        val current = readSnapshot(expected.uri)

        if (current == null) {
            candidate = null
            return
        }

        if (current.isPending || !current.sameFileStateAs(expected)) {
            candidate = Candidate(snapshot = current, stableChecks = 0)
            handler.postDelayed(checkRunnable, STABILITY_SAMPLE_MS)
            return
        }

        val uriKey = current.uri.toString()
        if (preferences.isSaved(uriKey) || announcedUris.contains(uriKey)) {
            candidate = null
            return
        }

        rememberAnnounced(uriKey)
        candidate = null
        onScreenshot(current.uri)
    }

    private fun findLatestRecentScreenshot(): MediaSnapshot? {
        val resolver = context.contentResolver
        val projection = buildProjection()
        val newestAllowed = (System.currentTimeMillis() / 1000L) - RECENT_WINDOW_SECONDS

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DATE_ADDED} >= ?",
            arrayOf(newestAllowed.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.RELATIVE_PATH
            } else {
                MediaStore.Images.Media.DATA
            }
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val pendingIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            } else {
                -1
            }

            var inspected = 0
            while (cursor.moveToNext() && inspected < MAX_RECENT_ROWS) {
                inspected += 1

                val name = cursor.getString(nameIndex)
                val path = cursor.getString(pathIndex)
                if (!ScreenshotActions.isScreenshot(name, path)) continue

                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )

                return MediaSnapshot(
                    uri = uri,
                    dateAddedSeconds = cursor.getLong(dateAddedIndex),
                    dateModifiedSeconds = cursor.getLong(modifiedIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    isPending = pendingIndex >= 0 && cursor.getInt(pendingIndex) != 0,
                )
            }
        }

        return null
    }

    private fun readSnapshot(uri: Uri): MediaSnapshot? {
        val projection = buildProjection()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val pendingIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            } else {
                -1
            }

            return MediaSnapshot(
                uri = uri,
                dateAddedSeconds = cursor.getLong(dateAddedIndex),
                dateModifiedSeconds = cursor.getLong(modifiedIndex),
                sizeBytes = cursor.getLong(sizeIndex),
                isPending = pendingIndex >= 0 && cursor.getInt(pendingIndex) != 0,
            )
        }
        return null
    }

    private fun buildProjection(): Array<String> = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            add(MediaStore.Images.Media.DATA)
        }
        add(MediaStore.Images.Media.DATE_ADDED)
        add(MediaStore.Images.Media.DATE_MODIFIED)
        add(MediaStore.Images.Media.SIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.IS_PENDING)
        }
    }.toTypedArray()

    private fun rememberAnnounced(uriKey: String) {
        announcedUris.add(uriKey)
        while (announcedUris.size > MAX_ANNOUNCED_URIS) {
            val oldest = announcedUris.iterator().next()
            announcedUris.remove(oldest)
        }
    }

    private data class Candidate(
        val snapshot: MediaSnapshot,
        val stableChecks: Int,
    )

    private data class MediaSnapshot(
        val uri: Uri,
        val dateAddedSeconds: Long,
        val dateModifiedSeconds: Long,
        val sizeBytes: Long,
        val isPending: Boolean,
    ) {
        fun sameFileStateAs(other: MediaSnapshot): Boolean {
            return uri == other.uri &&
                dateAddedSeconds == other.dateAddedSeconds &&
                dateModifiedSeconds == other.dateModifiedSeconds &&
                sizeBytes == other.sizeBytes &&
                isPending == other.isPending
        }
    }

    companion object {
        private const val EVENT_SETTLE_MS = 150L
        private const val STABILITY_SAMPLE_MS = 350L
        private const val REQUIRED_STABLE_CHECKS = 2
        private const val RECENT_WINDOW_SECONDS = 45L
        private const val MAX_RECENT_ROWS = 12
        private const val MAX_ANNOUNCED_URIS = 32
    }
}
