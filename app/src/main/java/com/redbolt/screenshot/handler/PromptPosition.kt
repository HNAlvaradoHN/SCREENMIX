package com.screenmix.app.handler

enum class PromptPosition(val storageValue: String) {
    TOP("top"),
    CENTER("center"),
    BOTTOM("bottom"),
    ;

    companion object {
        fun fromStorage(value: String?): PromptPosition {
            return entries.firstOrNull { it.storageValue == value } ?: CENTER
        }
    }
}
