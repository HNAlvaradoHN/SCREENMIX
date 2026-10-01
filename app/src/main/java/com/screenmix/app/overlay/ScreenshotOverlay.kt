package com.screenmix.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.screenmix.app.R
import com.screenmix.app.handler.LocaleHelper
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.handler.ScreenshotPromptLauncher
import com.screenmix.app.ui.prompt.ScreenshotPromptContent
import com.screenmix.app.ui.theme.ScreenMixAccent
import com.screenmix.app.ui.theme.ScreenMixTheme
import com.screenmix.app.ui.theme.ScreenMixThemeColors

object ScreenshotOverlay {
    private val handler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null

    private val expanded = mutableStateOf(false)
    private val currentUri = mutableStateOf<Uri?>(null)

    private var sessionStartedAtSeconds: Long = 0L
    private var originalPath: String? = null

    private val timeoutRunnable = Runnable {
        val context = appContext ?: return@Runnable
        close(context, markHandled = true)
    }

    fun isShowing(): Boolean = composeView != null

    fun showFloatingSession(context: Context, uri: Uri): Boolean {
        val applicationContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            !Settings.canDrawOverlays(applicationContext)
        ) {
            return false
        }

        val previous = currentUri.value
        if (previous != null &&
            previous != uri &&
            previous != ScreenshotPromptLauncher.testUri
        ) {
            ScreenshotActions.dismissScreenshot(applicationContext, previous)
        }

        sessionStartedAtSeconds = System.currentTimeMillis() / 1000L
        originalPath = normalizePath(readImagePath(applicationContext, uri))
        currentUri.value = uri
        expanded.value = false

        if (composeView == null) {
            if (!createOverlay(applicationContext)) {
                currentUri.value = null
                return false
            }
        }

        updateWindow(expandedMode = false)
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(
            timeoutRunnable,
            ScreenshotPreferences(applicationContext).floatingButtonTimeoutMs,
        )
        return true
    }

    fun show(context: Context, uri: Uri): Boolean {
        val applicationContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            !Settings.canDrawOverlays(applicationContext)
        ) {
            return false
        }

        close(applicationContext, markHandled = false)
        sessionStartedAtSeconds = System.currentTimeMillis() / 1000L
        originalPath = normalizePath(readImagePath(applicationContext, uri))
        currentUri.value = uri
        expanded.value = true

        if (!createOverlay(applicationContext)) {
            currentUri.value = null
            return false
        }

        updateWindow(expandedMode = true)
        return true
    }

    fun hide(context: Context) {
        close(context.applicationContext, markHandled = false)
    }

    private fun createOverlay(context: Context): Boolean {
        val uiContext = LocaleHelper.wrap(context)
        val manager = context.getSystemService(WindowManager::class.java) ?: return false

        return runCatching {
            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner
            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            val view = ComposeView(uiContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val prefs = ScreenshotPreferences(context)
                    ScreenMixTheme(accent = ScreenMixAccent.fromStorage(prefs.accentId)) {
                        if (expanded.value) {
                            ExpandedPanel(context)
                        } else {
                            CompactButton(context)
                        }
                    }
                }
            }

            val params = compactLayoutParams(context)
            appContext = context
            windowManager = manager
            composeView = view
            layoutParams = params
            manager.addView(view, params)
            true
        }.getOrElse {
            close(context, markHandled = false)
            false
        }
    }

    @Composable
    private fun CompactButton(context: Context) {
        val colors = ScreenMixThemeColors.current
        val accent = ScreenMixAccent.fromStorage(
            ScreenshotPreferences(context).accentId,
        )
        val iconTint = if (accent == ScreenMixAccent.ICE) Color.Black else Color.White
        val bubbleShape = RoundedCornerShape(22.dp)

        Box(
            modifier = Modifier.size(width = 96.dp, height = 72.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 82.dp, height = 58.dp)
                    .shadow(12.dp, bubbleShape)
                    .clip(bubbleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.surfaceAlt,
                                colors.surface,
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = colors.accent.copy(alpha = 0.45f),
                        shape = bubbleShape,
                    )
                    .clickable {
                        expanded.value = true
                        updateWindow(expandedMode = true)
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.accent,
                                    colors.accent.copy(alpha = 0.72f),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_notification),
                        contentDescription = context.getString(R.string.open_capture_actions),
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(21.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 16.dp, height = 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.18f)),
                )
            }

            Box(
                modifier = Modifier
                    .size(25.dp)
                    .align(Alignment.TopEnd)
                    .shadow(7.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.surfaceAlt)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = CircleShape,
                    )
                    .clickable {
                        close(context, markHandled = true)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = colors.textPrimary.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                )
            }
        }
    }

    @Composable
    private fun ExpandedPanel(context: Context) {
        val prefs = ScreenshotPreferences(context)

        ScreenshotPromptContent(
            overlayMode = true,
            position = prefs.promptPosition,
            tapOutsideToDismiss = prefs.tapOutsideToDismiss,
            onCopyDelete = {
                val target = resolveLatestSessionImage(context)
                close(context, markHandled = false)
                if (target != null && target != ScreenshotPromptLauncher.testUri) {
                    ScreenshotActions.copyAndDelete(context, target)
                }
            },
            onCopySave = {
                val target = resolveLatestSessionImage(context)
                close(context, markHandled = false)
                if (target != null && target != ScreenshotPromptLauncher.testUri) {
                    ScreenshotActions.copyAndSave(context, target)
                }
            },
            onDismiss = {
                close(context, markHandled = true)
            },
        )
    }

    private fun updateWindow(expandedMode: Boolean) {
        val context = appContext ?: return
        val view = composeView ?: return
        val manager = windowManager ?: return
        val params = layoutParams ?: return
        val density = context.resources.displayMetrics.density

        if (expandedMode) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
        } else {
            params.width = (96 * density).toInt()
            params.height = (72 * density).toInt()
            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.x = (12 * density).toInt()
            params.y = 0
        }

        runCatching { manager.updateViewLayout(view, params) }
    }

    private fun close(context: Context, markHandled: Boolean) {
        val target = if (markHandled) {
            resolveLatestSessionImage(context) ?: currentUri.value
        } else {
            null
        }

        handler.removeCallbacks(timeoutRunnable)

        val view = composeView
        composeView = null
        if (view != null) {
            runCatching {
                windowManager?.removeViewImmediate(view)
            }
        }

        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        lifecycleOwner = null
        windowManager = null
        layoutParams = null
        appContext = null
        expanded.value = false
        currentUri.value = null
        sessionStartedAtSeconds = 0L
        originalPath = null

        if (markHandled &&
            target != null &&
            target != ScreenshotPromptLauncher.testUri
        ) {
            ScreenshotActions.dismissScreenshot(context, target)
        }
    }

    private fun compactLayoutParams(context: Context): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            (96 * density).toInt(),
            (72 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (12 * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun resolveLatestSessionImage(context: Context): Uri? {
        val fallback = currentUri.value
        if (fallback == ScreenshotPromptLauncher.testUri) return fallback

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            pathColumn,
            MediaStore.Images.Media.DATE_ADDED,
        )

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
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)

            var inspected = 0
            while (cursor.moveToNext() && inspected < MAX_RECENT_ROWS) {
                inspected += 1
                val name = cursor.getString(nameIndex)
                val path = cursor.getString(pathIndex)
                val normalizedPath = normalizePath(path)
                val related =
                    ScreenshotActions.isScreenshot(name, path) ||
                        (originalPath != null && originalPath == normalizedPath)

                if (!related) continue

                val id = cursor.getLong(idIndex)
                return Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
            }
        }

        return fallback
    }

    private fun normalizePath(path: String?): String? {
        if (path == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            path
        } else {
            path.substringBeforeLast('/', missingDelimiterValue = path)
        }
    }

    private fun readImagePath(context: Context, uri: Uri): String? {
        if (uri == ScreenshotPromptLauncher.testUri) return null

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        }

        context.contentResolver.query(
            uri,
            arrayOf(pathColumn),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    private const val MAX_RECENT_ROWS = 16
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
