package com.caregiver.mobile.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Synchronous SharedPreferences mirror of the UI language.
 *
 * The DataStore value in [SettingsStore][com.caregiver.mobile.data.SettingsStore]
 * stays the source of truth the UI reads. This mirror exists for process
 * start: a language change hidden in DataStore has shown up reverted on
 * some devices after the app is closed and reopened, and the cold-start
 * read (blocking DataStore collection in `CaregiverApp.attachBaseContext`)
 * has no observable failure path. SharedPreferences `commit()` is the most
 * boring, synchronous, atomic write there is, so it is written on every
 * language change and read first at process start; DataStore remains the
 * fallback and self-heals back into the mirror when it disagrees.
 *
 * [host] is captured once from `CaregiverApp.attachBaseContext` so writers
 * that only have the store (no Context) can still persist.
 */
object LanguagePrefs {
    private const val FILE = "language_prefs"
    private const val KEY = "language"

    @Volatile
    private var host: Context? = null

    /** Captures the process-lifetime base context once, at attach time. */
    fun attachHost(base: Context) {
        if (host == null) host = base
    }

    /** Durably records [language]; no-op until a host is attached. */
    fun write(language: String) {
        prefs()?.edit()?.putString(KEY, language)?.commit()
    }

    /** The mirrored language, or null when nothing was ever written. */
    fun read(): String? = prefs()?.getString(KEY, null)

    private fun prefs(): SharedPreferences? =
        host?.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Test hygiene: drops the captured host so tests start clean. */
    fun resetForTest() {
        host = null
    }
}
