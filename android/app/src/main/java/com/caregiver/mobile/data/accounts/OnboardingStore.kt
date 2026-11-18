package com.caregiver.mobile.data.accounts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Per-account onboarding flag (#616).
 *
 * `SettingsStore.onboardingCompleted` was a single global boolean, so a
 * second account would skip onboarding entirely. This store keys the flag
 * by account: `onboarding_completed_<accountId>`.
 *
 * The flow type stays `Flow<Boolean>` so callers only swap which store
 * they read — the wiring into `SettingsStore`/`CaregiverRoot` is done by
 * whoever owns those files.
 *
 * With no active account the flag reads `false` ("not completed") and
 * [completeOnboarding] is a no-op — never a crash.
 */
class OnboardingStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountId: Flow<String?>,
) {
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /** True once the **active** account finished onboarding. */
    val onboardingCompleted: Flow<Boolean> =
        combine(activeAccountId, safeData) { id, prefs ->
            id?.let { prefs[keyFor(it)] } ?: false
        }

    /** Marks onboarding complete for the active account. No-op with no account. */
    suspend fun completeOnboarding() {
        val id = activeAccountId.first() ?: return
        dataStore.edit { it[keyFor(id)] = true }
    }

    /**
     * Copies the legacy global `onboarding_completed` flag onto
     * [ownerAccountId] (#625 migration hook). Only writes when the
     * per-account key is absent, so a re-run never overwrites a newer
     * value. The legacy key is left in place — other readers may still
     * consult it until the wiring lands.
     */
    suspend fun migrateLegacyFlag(ownerAccountId: String) {
        dataStore.edit { prefs ->
            if (prefs[LEGACY_KEY] == true && !prefs.contains(keyFor(ownerAccountId))) {
                prefs[keyFor(ownerAccountId)] = true
            }
        }
    }

    private fun keyFor(accountId: String) =
        booleanPreferencesKey("$KEY_PREFIX$accountId")

    companion object {
        /** Must match `SettingsStore.ONBOARDING_COMPLETED`. */
        private const val KEY_PREFIX = "onboarding_completed_"
        private val LEGACY_KEY = booleanPreferencesKey("onboarding_completed")
    }
}
