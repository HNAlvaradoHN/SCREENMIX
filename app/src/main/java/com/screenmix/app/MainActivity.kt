package com.screenmix.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.screenmix.app.accessibility.SystemPreviewDismissAccessibilityService
import com.screenmix.app.handler.AppLanguage
import com.screenmix.app.handler.LocaleHelper
import com.screenmix.app.handler.PromptPosition
import com.screenmix.app.handler.ScreenshotActions
import com.screenmix.app.handler.ScreenshotPreferences
import com.screenmix.app.handler.ScreenshotPromptLauncher
import com.screenmix.app.service.ScreenshotMonitorService
import com.screenmix.app.ui.theme.ScreenMixAccent
import com.screenmix.app.ui.theme.ScreenMixBodyFont
import com.screenmix.app.ui.theme.ScreenMixDisplayFont
import com.screenmix.app.ui.theme.ScreenMixTheme
import com.screenmix.app.ui.theme.ScreenMixThemeColors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val app by lazy { application as ScreenshotApplication }

    private var monitorEnabled by mutableStateOf(true)
    private var keepMonitorActive by mutableStateOf(false)
    private var instantPrompt by mutableStateOf(true)
    private var promptPosition by mutableStateOf(PromptPosition.CENTER)
    private var vibrateOnPrompt by mutableStateOf(true)
    private var tapOutsideToDismiss by mutableStateOf(true)
    private var detectionDelayMs by mutableFloatStateOf(
        ScreenshotPreferences.DEFAULT_DETECTION_DELAY_MS.toFloat(),
    )
    private var selectedLanguage by mutableStateOf(AppLanguage.SYSTEM)
    private var selectedAccent by mutableStateOf(ScreenMixAccent.OCEAN)
    private var dismissSystemPreview by mutableStateOf(false)
    private var systemPreviewDismissDelayMs by mutableFloatStateOf(
        ScreenshotPreferences.DEFAULT_SYSTEM_PREVIEW_DISMISS_DELAY_MS.toFloat(),
    )
    private var hasMediaPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAllFilesAccess by mutableStateOf(false)
    private var hasAccessibilityDismiss by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasMediaPermission = hasMediaAccess()
        hasNotificationPermission = hasNotificationAccess()
        syncMonitorState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        hasMediaPermission = hasMediaAccess()
        hasNotificationPermission = hasNotificationAccess()
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasAllFilesAccess = ScreenshotActions.hasAllFilesAccess(this)
        hasAccessibilityDismiss = SystemPreviewDismissAccessibilityService.isEnabled(this)
        enableEdgeToEdge()

        if (!hasMediaPermission || !hasNotificationPermission) {
            requestNeededPermissions()
        } else if (monitorEnabled) {
            ScreenshotMonitorService.start(this)
        }

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("1.0.0")

        setContent {
            ScreenMixTheme(accent = selectedAccent) {
                val colors = ScreenMixThemeColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = getString(R.string.app_name),
                        fontFamily = ScreenMixDisplayFont,
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getString(R.string.app_tagline),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 10.sp,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    SettingRow(
                        title = getString(R.string.monitor_screenshots_title),
                        subtitle = if (hasMediaPermission) {
                            getString(R.string.monitor_running)
                        } else {
                            getString(R.string.monitor_needs_access)
                        },
                        checked = monitorEnabled && hasMediaPermission,
                        onCheckedChange = { enabled ->
                            if (!hasMediaPermission) {
                                requestNeededPermissions()
                                return@SettingRow
                            }
                            monitorEnabled = enabled
                            app.preferences.isMonitorEnabled = enabled
                            syncMonitorState()
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.keep_monitor_title),
                        subtitle = getString(R.string.keep_monitor_subtitle),
                        checked = keepMonitorActive,
                        onCheckedChange = { enabled ->
                            keepMonitorActive = enabled
                            app.preferences.keepMonitorActive = enabled
                            syncMonitorState()
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.popup_prompt_title),
                        subtitle = getString(R.string.popup_prompt_subtitle),
                        checked = instantPrompt,
                        onCheckedChange = {
                            instantPrompt = it
                            app.preferences.showInstantPrompt = it
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.overlay_permission_title),
                        subtitle = if (hasOverlayPermission) {
                            getString(R.string.overlay_permission_subtitle_on)
                        } else {
                            getString(R.string.overlay_permission_subtitle_off)
                        },
                        checked = hasOverlayPermission,
                        onCheckedChange = {
                            openOverlaySettings()
                        },
                    )
                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsButton(
                            label = getString(R.string.enable_overlay),
                            primary = true,
                            onClick = { openOverlaySettings() },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.all_files_title),
                        subtitle = if (hasAllFilesAccess) {
                            getString(R.string.all_files_subtitle_on)
                        } else {
                            getString(R.string.all_files_subtitle_off)
                        },
                        checked = hasAllFilesAccess,
                        onCheckedChange = {
                            ScreenshotActions.openAllFilesAccessSettings(this@MainActivity)
                        },
                    )
                    if (!hasAllFilesAccess) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsButton(
                            label = getString(R.string.enable_all_files),
                            primary = false,
                            onClick = { ScreenshotActions.openAllFilesAccessSettings(this@MainActivity) },
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = getString(R.string.section_appearance),
                        fontFamily = ScreenMixDisplayFont,
                        color = colors.accent,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = getString(R.string.language_title),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = getString(R.string.language_subtitle),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AppLanguage.entries.chunked(2).forEach { rowLanguages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowLanguages.forEach { language ->
                                LanguageChip(
                                    label = if (language == AppLanguage.SYSTEM) {
                                        getString(R.string.language_system)
                                    } else {
                                        language.nativeName
                                    },
                                    selected = selectedLanguage == language,
                                    onClick = {
                                        selectedLanguage = language
                                        app.preferences.languageTag = language.tag
                                        recreate()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowLanguages.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = getString(R.string.accent_title),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = getString(R.string.accent_subtitle),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ScreenMixAccent.entries.forEach { accent ->
                            AccentSwatch(
                                accent = accent,
                                selected = selectedAccent == accent,
                                onClick = {
                                    selectedAccent = accent
                                    app.preferences.accentId = accent.storageValue
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = getString(R.string.section_customization),
                        fontFamily = ScreenMixDisplayFont,
                        color = colors.accent,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.dismiss_preview_title),
                        subtitle = if (hasAccessibilityDismiss && dismissSystemPreview) {
                            getString(R.string.dismiss_preview_subtitle_on)
                        } else {
                            getString(R.string.dismiss_preview_subtitle_off)
                        },
                        checked = dismissSystemPreview,
                        onCheckedChange = { enabled ->
                            dismissSystemPreview = enabled
                            app.preferences.dismissSystemPreview = enabled
                            if (enabled && !hasAccessibilityDismiss) {
                                SystemPreviewDismissAccessibilityService.openSettings(this@MainActivity)
                            }
                        },
                    )
                    if (dismissSystemPreview && !hasAccessibilityDismiss) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsButton(
                            label = getString(R.string.enable_dismiss_preview),
                            primary = false,
                            onClick = { SystemPreviewDismissAccessibilityService.openSettings(this@MainActivity) },
                        )
                    }
                    if (dismissSystemPreview && hasAccessibilityDismiss) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = getString(R.string.dismiss_preview_delay_title),
                            fontFamily = ScreenMixBodyFont,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = getString(R.string.dismiss_preview_delay_subtitle),
                            fontFamily = ScreenMixBodyFont,
                            color = colors.textMuted,
                            fontSize = 9.sp,
                        )
                        Text(
                            text = getString(
                                R.string.detection_delay_value,
                                systemPreviewDismissDelayMs.roundToInt(),
                            ),
                            fontFamily = ScreenMixBodyFont,
                            color = colors.accent,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Slider(
                            value = systemPreviewDismissDelayMs,
                            onValueChange = { systemPreviewDismissDelayMs = it },
                            onValueChangeFinished = {
                                app.preferences.systemPreviewDismissDelayMs =
                                    systemPreviewDismissDelayMs.roundToInt().toLong()
                            },
                            valueRange = 500f..3_000f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.textPrimary,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.surface,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = getString(R.string.prompt_position_title),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = getString(R.string.prompt_position_subtitle),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PositionChip(
                            label = getString(R.string.position_top),
                            selected = promptPosition == PromptPosition.TOP,
                            onClick = {
                                promptPosition = PromptPosition.TOP
                                app.preferences.promptPosition = PromptPosition.TOP
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PositionChip(
                            label = getString(R.string.position_center),
                            selected = promptPosition == PromptPosition.CENTER,
                            onClick = {
                                promptPosition = PromptPosition.CENTER
                                app.preferences.promptPosition = PromptPosition.CENTER
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PositionChip(
                            label = getString(R.string.position_bottom),
                            selected = promptPosition == PromptPosition.BOTTOM,
                            onClick = {
                                promptPosition = PromptPosition.BOTTOM
                                app.preferences.promptPosition = PromptPosition.BOTTOM
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.vibrate_title),
                        subtitle = getString(R.string.vibrate_subtitle),
                        checked = vibrateOnPrompt,
                        onCheckedChange = {
                            vibrateOnPrompt = it
                            app.preferences.vibrateOnPrompt = it
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = getString(R.string.tap_outside_title),
                        subtitle = getString(R.string.tap_outside_subtitle),
                        checked = tapOutsideToDismiss,
                        onCheckedChange = {
                            tapOutsideToDismiss = it
                            app.preferences.tapOutsideToDismiss = it
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = getString(R.string.detection_delay_title),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = getString(R.string.detection_delay_subtitle),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    Text(
                        text = getString(
                            R.string.detection_delay_value,
                            detectionDelayMs.roundToInt(),
                        ),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.accent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Slider(
                        value = detectionDelayMs,
                        onValueChange = { detectionDelayMs = it },
                        onValueChangeFinished = {
                            app.preferences.detectionDelayMs = detectionDelayMs.roundToInt().toLong()
                        },
                        valueRange = 0f..1000f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.textPrimary,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.surface,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsButton(
                        label = getString(R.string.test_prompt),
                        primary = true,
                        onClick = { ScreenshotPromptLauncher.showTest(this@MainActivity) },
                    )
                    Text(
                        text = getString(R.string.test_prompt_subtitle),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = getString(R.string.system_preview_note),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    if (!hasNotificationPermission && !instantPrompt) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = getString(R.string.notification_permission_note),
                            fontFamily = ScreenMixBodyFont,
                            color = colors.accent,
                            fontSize = 9.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = getString(R.string.version_label, versionName),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasMediaPermission = hasMediaAccess()
        hasNotificationPermission = hasNotificationAccess()
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasAllFilesAccess = ScreenshotActions.hasAllFilesAccess(this)
        hasAccessibilityDismiss = SystemPreviewDismissAccessibilityService.isEnabled(this)
        if (monitorEnabled && hasMediaPermission) {
            ScreenshotMonitorService.start(this)
        }
    }

    private fun loadPreferences() {
        monitorEnabled = app.preferences.isMonitorEnabled
        keepMonitorActive = app.preferences.keepMonitorActive
        instantPrompt = app.preferences.showInstantPrompt
        promptPosition = app.preferences.promptPosition
        vibrateOnPrompt = app.preferences.vibrateOnPrompt
        tapOutsideToDismiss = app.preferences.tapOutsideToDismiss
        detectionDelayMs = app.preferences.detectionDelayMs.toFloat()
        selectedLanguage = AppLanguage.fromTag(app.preferences.languageTag)
        selectedAccent = ScreenMixAccent.fromStorage(app.preferences.accentId)
        dismissSystemPreview = app.preferences.dismissSystemPreview
        systemPreviewDismissDelayMs = app.preferences.systemPreviewDismissDelayMs.toFloat()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun syncMonitorState() {
        if (monitorEnabled && hasMediaPermission) {
            ScreenshotMonitorService.start(this)
        } else {
            ScreenshotMonitorService.stop(this)
        }
    }

    private fun hasMediaAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNotificationAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNeededPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@androidx.compose.runtime.Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ScreenMixThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontFamily = ScreenMixBodyFont, color = colors.textPrimary, fontSize = 11.sp)
            Text(text = subtitle, fontFamily = ScreenMixBodyFont, color = colors.textMuted, fontSize = 9.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textPrimary,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}

@androidx.compose.runtime.Composable
private fun SettingsButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = ScreenMixThemeColors.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.accent else colors.surface,
            contentColor = if (primary) colors.background else colors.textPrimary,
        ),
    ) {
        Text(label, fontFamily = ScreenMixBodyFont, fontSize = 10.sp)
    }
}

@androidx.compose.runtime.Composable
private fun PositionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScreenMixThemeColors.current
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = ScreenMixBodyFont,
            color = if (selected) colors.background else colors.textMuted,
            fontSize = 10.sp,
        )
    }
}

@androidx.compose.runtime.Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScreenMixThemeColors.current
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.surfaceAlt else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = ScreenMixBodyFont,
            color = if (selected) colors.textPrimary else colors.textMuted,
            fontSize = 10.sp,
        )
    }
}

@androidx.compose.runtime.Composable
private fun AccentSwatch(
    accent: ScreenMixAccent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ScreenMixThemeColors.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(if (selected) 34.dp else 30.dp)
            .clip(CircleShape)
            .background(accent.color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.textPrimary else colors.border,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
