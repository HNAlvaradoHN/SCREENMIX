package com.screenmix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Immutable
data class ScreenMixColorScheme(
    val accent: Color,
    val background: Color = Color(0xFF0B0D10),
    val surface: Color = Color(0xFF141820),
    val surfaceAlt: Color = Color(0xFF1B2230),
    val border: Color = Color(0xFF283241),
    val textPrimary: Color = Color(0xFFF8FAFC),
    val textMuted: Color = Color(0xFF94A3B8),
)

val ScreenMixDisplayFont = FontFamily.SansSerif
val ScreenMixBodyFont = FontFamily.SansSerif

private val LocalScreenMixColors = staticCompositionLocalOf {
    ScreenMixColorScheme(ScreenMixAccent.OCEAN.color)
}

object ScreenMixThemeColors {
    val current: ScreenMixColorScheme
        @Composable get() = LocalScreenMixColors.current
}

@Composable
fun ScreenMixTheme(
    accent: ScreenMixAccent,
    content: @Composable () -> Unit,
) {
    val colors = ScreenMixColorScheme(accent = accent.color)
    val materialColors = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.background,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceAlt,
        outline = colors.border,
    )

    CompositionLocalProvider(LocalScreenMixColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = MaterialTheme.typography.copy(
                headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = ScreenMixDisplayFont,
                    fontWeight = FontWeight.Bold,
                ),
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = ScreenMixBodyFont,
                ),
            ),
            content = content,
        )
    }
}
