package com.caregiver.mobile.data

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

/**
 * Single-instance owner for [SettingsStore] backing files. DataStore allows
 * exactly one active instance per file: the locale read in
 * `CaregiverApp.attachBaseContext` and `AppGraph.settings` both resolve
 * through here, so the `settings` file never gets two competing stores.
 * The producer runs at most once per name; the key is the file name, never
 * the calling context, so base-context and application-context callers share
 * the instance without touching `applicationContext` before attach.
 *
 * A corrupt preferences file is replaced with empty settings. Only
 * reconstructible configuration lives here: losing it resets the backend
 * URL/language/theme and drops the cached token (the user signs in again),
 * while clinical notes, plans, and medications are in Room and untouched.
 */
object SettingsStoreRegistry {
    private val lock = Any()
    private val stores = mutableMapOf<String, SettingsStore>()

    fun getOrCreate(name: String, produceFile: () -> File): SettingsStore =
        synchronized(lock) {
            stores.getOrPut(name) {
                SettingsStore(
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        produceFile = produceFile,
                    ),
                )
            }
        }
}
