package com.screenmix.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class ScreenMixAccent(
    val storageValue: String,
    val color: Color,
    val notificationArgb: Int,
) {
    OCEAN("ocean", Color(0xFF4DA3FF), 0xFF4DA3FF.toInt()),
    VIOLET("violet", Color(0xFF8B5CF6), 0xFF8B5CF6.toInt()),
    ROSE("rose", Color(0xFFF43F5E), 0xFFF43F5E.toInt()),
    CORAL("coral", Color(0xFFFF6B4A), 0xFFFF6B4A.toInt()),
    AMBER("amber", Color(0xFFF6B73C), 0xFFF6B73C.toInt()),
    LIME("lime", Color(0xFF84CC16), 0xFF84CC16.toInt()),
    MINT("mint", Color(0xFF2DD4BF), 0xFF2DD4BF.toInt()),
    ICE("ice", Color(0xFFE2E8F0), 0xFFE2E8F0.toInt()),
    ;

    companion object {
        fun fromStorage(value: String?): ScreenMixAccent =
            entries.firstOrNull { it.storageValue == value } ?: OCEAN
    }
}
