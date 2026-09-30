package com.screenmix.app.handler

enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE("pt", "Português"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag.orEmpty() } ?: SYSTEM
    }
}
