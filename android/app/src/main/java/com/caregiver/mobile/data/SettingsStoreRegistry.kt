package com.caregiver.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File

/**
 * Single-instance owner for [SettingsStore] backing files. DataStore allows
 * exactly one active instance per file: the locale read in
 * `CaregiverApp.attachBaseContext` and `AppGraph.settings` both resolve
 * through here, so the `settings` file never gets two competing stores.
 * The producer runs at most once per name; the key is the file name, never
 * the calling context, so base-context and application-context callers share
 * the instance without touching `applicationContext` before attach.
 */
object SettingsStoreRegistry {
    private val lock = Any()
    private val stores = mutableMapOf<String, SettingsStore>()

    fun getOrCreate(name: String, produceFile: () -> File): SettingsStore =
        synchronized(lock) {
            stores.getOrPut(name) {
                SettingsStore(PreferenceDataStoreFactory.create { produceFile() })
            }
        }
}
