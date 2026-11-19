package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Single-instance owner for the session backing file, mirroring
 * [com.caregiver.mobile.data.SettingsStoreRegistry]. DataStore allows exactly
 * one active instance per file; without this registry every new [SessionStore]
 * (e.g. a second AppGraph in the same process — instrumentation runs many tests
 * in one process) crashed with "multiple DataStores active for the same file".
 * DEF-011: found by the first full emulator instrumentation run (136 failures).
 */
object SessionStoreRegistry {
    private val lock = Any()
    private val stores = mutableMapOf<String, SessionStore>()

    /**
     * Keyed by the resolved file's absolute path, not a bare name: one active
     * instance per file on a device (all graphs share one filesDir), while
     * Robolectric's per-test filesDir still yields isolated stores per test.
     */
    fun getOrCreate(produceFile: () -> File): SessionStore =
        synchronized(lock) {
            val file = produceFile()
            stores.getOrPut(file.absolutePath) {
                SessionStore(PreferenceDataStoreFactory.create(produceFile = { file }))
            }
        }
}

/**
 * The persisted active-account id (epic #581, #592).
 *
 * The injected [DataStore] MUST be the single one that backs `SettingsStore`
 * (the `settings` file) — this store adds only the `active_account_id` key to
 * it. Callers must not create a second DataStore for that file; DataStore
 * forbids two active instances on one file. No password, hash, salt or
 * session secret is ever written here — only an opaque account id.
 *
 * The store is deliberately stateless: a fresh instance over the same
 * DataStore reads back what a previous instance wrote, which is what makes
 * the id survive process death.
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Transient I/O errors degrade to "no active account" for this collection
     * instead of cancelling every screen that observes the session. The
     * corruption handler on the shared DataStore repairs the file itself.
     */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val activeAccountId: Flow<String?> = safeData.map { it[ACTIVE_ACCOUNT_ID] }

    /** Null clears the key, so a signed-out app persists no active id. */
    suspend fun setActiveAccountId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_ACCOUNT_ID)
            else prefs[ACTIVE_ACCOUNT_ID] = id
        }
    }

    suspend fun clearActiveAccountId() = setActiveAccountId(null)

    companion object {
        /** Added to the settings DataStore; kept here so both sides agree. */
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")

        /**
         * Single app-wide instance over the `session` DataStore file. Kept
         * separate from `settings` because `SettingsStore` does not expose its
         * DataStore and is not owned by this lane; only the opaque active id
         * lives here, never a credential.
         */
        fun create(context: Context): SessionStore = SessionStoreRegistry.getOrCreate {
            context.applicationContext.preferencesDataStoreFile(SESSION_FILE)
        }

        private const val SESSION_FILE = "session"
    }
}
