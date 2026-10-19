package com.caregiver.mobile.core.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps a context in the given language (ar/en) for RTL-correct resources.
 *
 * The DataStore value in `SettingsStore` stays the single source of truth;
 * this helper adds a process-wide mirror so every activity can apply the
 * language without touching disk:
 *
 * - `CaregiverApp.attachBaseContext` reads the store once per process and
 *   seeds the cache (it runs before any activity exists);
 * - `MainActivity.attachBaseContext` wraps its base context with the cached
 *   language, so resources and layout direction are correct from the first
 *   frame — no flash of the wrong language;
 * - `SettingsStore.setLanguage` writes through to the cache before the
 *   settings screen recreates the activity.
 */
object LocaleHelper {
    @Volatile
    private var cachedLanguage: String? = null

    /**
     * The configuration [wrap] applies to a context: [language] plus the
     * layout direction that follows from it (Arabic switches the layout to
     * RTL). Exposed separately so tests can pin the decision without
     * building a context.
     */
    fun configurationFor(context: Context, language: String): Configuration =
        Configuration(context.resources.configuration).apply { setLocale(Locale(language)) }

    /** Wraps [context] so it resolves resources in [language] (ar/en). */
    fun wrap(context: Context, language: String): Context =
        ContextWrapper(context.createConfigurationContext(configurationFor(context, language)))

    /** Records the effective language for the rest of the process. */
    fun cache(language: String) {
        cachedLanguage = language
    }

    /** The cached language, or [default] before the store has been read. */
    fun cachedOr(default: String): String = cachedLanguage ?: default
}
