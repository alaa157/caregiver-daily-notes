package com.caregiver.mobile.core.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/** Wraps a context in the given language (ar/en) for RTL-correct resources. */
object LocaleHelper {
    fun wrap(context: Context, language: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(language))
        return ContextWrapper(context.createConfigurationContext(config))
    }
}
