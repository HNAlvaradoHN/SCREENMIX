package com.screenmix.app.handler

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    fun wrap(context: Context): Context {
        val tag = ScreenshotPreferences(context).languageTag
        if (tag.isBlank()) return context

        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(config)
    }
}
