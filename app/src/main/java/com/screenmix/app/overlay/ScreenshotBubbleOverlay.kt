package com.screenmix.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.screenmix.app.R
import com.screenmix.app.handler.LocaleHelper
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.handler.ScreenshotPromptLauncher
import com.screenmix.app.ui.theme.ScreenMixAccent

object ScreenshotBubbleOverlay {
    private val handler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var currentUri: Uri? = null
    private var sessionStartedAtSeconds: Long = 0L

    private val timeoutRunnable = Runnable {
        val view = rootView ?: return@Runnable
        val context = view.context.applicationContext
        hide(context, markHandled = true)
    }

    fun show(context: Context, uri: Uri): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            !Settings.canDrawOverlays(appContext)
        ) {
            return false
        }

        val previous = currentUri
        if (previous != null && previous != uri) {
            ScreenshotActions.dismissScreenshot(appContext, previous)
        }

        removeView(appContext)
        currentUri = uri
        sessionStartedAtSeconds = System.currentTimeMillis() / 1000L

        val windowManager = appContext.getSystemService(WindowManager::class.java) ?: return false
        val density = appContext.resources.displayMetrics.density
        val localized = LocaleHelper.wrap(appContext)
        val selectedAccent = ScreenMixAccent.fromStorage(
            ScreenshotPreferences(appContext).accentId,
        )
        val accent = selectedAccent.notificationArgb
        val iconColor = if (selectedAccent == ScreenMixAccent.ICE) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        return runCatching {
            val root = FrameLayout(appContext).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (76 * density).toInt(),
                    (76 * density).toInt(),
                )
            }

            val mainButton = ImageView(appContext).apply {
                setImageResource(R.drawable.ic_notification)
                setColorFilter(iconColor)
                setPadding(
                    (15 * density).toInt(),
                    (15 * density).toInt(),
                    (15 * density).toInt(),
                    (15 * density).toInt(),
                )
                background = circleDrawable(accent)
                contentDescription = localized.getString(R.string.open_capture_actions)
                elevation = 12 * density
                setOnClickListener {
                    val target = resolveLatestScreenshot(appContext) ?: currentUri
                    removeView(appContext)
                    currentUri = null
                    handler.removeCallbacks(timeoutRunnable)
                    if (target != null) {
                        ScreenshotPromptLauncher.showPanel(appContext, target)
                    }
                }
            }
            root.addView(
                mainButton,
                FrameLayout.LayoutParams(
                    (58 * density).toInt(),
                    (58 * density).toInt(),
                    Gravity.START or Gravity.BOTTOM,
                ),
            )

            val closeButton = TextView(appContext).apply {
                text = "×"
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(Color.WHITE)
                background = circleDrawable(Color.rgb(42, 46, 54))
                contentDescription = localized.getString(R.string.close_capture_button)
                elevation = 16 * density
                setOnClickListener {
                    hide(appContext, markHandled = true)
                }
            }
            root.addView(
                closeButton,
                FrameLayout.LayoutParams(
                    (26 * density).toInt(),
                    (26 * density).toInt(),
                    Gravity.END or Gravity.TOP,
                ),
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (12 * density).toInt()
            }

            windowManager.addView(root, params)
            rootView = root
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, SESSION_TIMEOUT_MS)
            true
        }.getOrElse {
            removeView(appContext)
            currentUri = null
            false
        }
    }

    fun hide(context: Context, markHandled: Boolean) {
        val appContext = context.applicationContext
        val target = if (markHandled) {
            resolveLatestScreenshot(appContext) ?: currentUri
        } else {
            null
        }

        handler.removeCallbacks(timeoutRunnable)
        removeView(appContext)
        currentUri = null

        if (markHandled && target != null) {
            ScreenshotActions.dismissScreenshot(appContext, target)
        }
    }

    private fun removeView(context: Context) {
        val view = rootView ?: return
        rootView = null
        runCatching {
            context.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
        }
    }

    private fun resolveLatestScreenshot(context: Context): Uri? {
        val fallback = currentUri
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

        val since = (sessionStartedAtSeconds - 2L).coerceAtLeast(0L)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DATE_ADDED} >= ?",
            arrayOf(since.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIndex = 2

            var inspected = 0
            while (cursor.moveToNext() && inspected < MAX_RECENT_ROWS) {
                inspected += 1
                val name = cursor.getString(nameIndex)
                val path = cursor.getString(pathIndex)
                if (!ScreenshotActions.isScreenshot(name, path)) continue

                val id = cursor.getLong(idIndex)
                return Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
            }
        }

        return fallback
    }

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private const val SESSION_TIMEOUT_MS = 90_000L
    private const val MAX_RECENT_ROWS = 12
}
