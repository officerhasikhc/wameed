package com.wameed

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    /**
     * Wraps the given context with the saved language locale.
     * Call from `attachBaseContext` in every Activity.
     */
    fun wrap(context: Context): Context {
        val lang = WameedPrefs.getLanguage(context)
        return applyLocale(context, lang)
    }

    /**
     * Applies the given language code ("ar" or "en") to the context
     * and returns a new context with the updated configuration.
     */
    fun applyLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }

        return context.createConfigurationContext(config)
    }
}
