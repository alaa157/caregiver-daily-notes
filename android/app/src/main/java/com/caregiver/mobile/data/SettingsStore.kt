package com.caregiver.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.caregiver.mobile.core.util.LanguagePrefs
import com.caregiver.mobile.core.util.ThemePrefs
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persisted settings: runtime-editable backend URL (issue #27), auth token,
 * and UI language. The token is write-only from the UI's perspective — this
 * store never logs it and no screen reads it except as present/absent.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Read side of the corruption policy: a corrupt or unreadable settings
     * file degrades to defaults for this collection instead of cancelling
     * every settings-dependent screen. The registry's
     * [androidx.datastore.core.handlers.ReplaceFileCorruptionHandler]
     * repairs the file itself on the next read; a transient disk error keeps
     * serving defaults until storage recovers. Writes still fail loudly —
     * silently accepting a setting that did not persist would be worse.
     */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val baseUrl: Flow<String> = safeData.map { it[BASE_URL] ?: DEFAULT_BASE_URL }

    /**
     * Whether a server URL has actually been stored. A blank URL means no
     * server is configured; callers use this to drive the unconfigured state
     * rather than mistaking an empty default for a usable host.
     */
    val isBaseUrlConfigured: Flow<Boolean> = baseUrl.map { it.isNotBlank() }

    val token: Flow<String?> = safeData.map { it[TOKEN] }
    val email: Flow<String?> = safeData.map { it[EMAIL] }
    val language: Flow<String> = safeData.map { it[LANGUAGE] ?: DEFAULT_LANGUAGE }

    /**
     * The stored language without the default applied, or null when nothing
     * was ever saved. The post-attach cold-start restore uses this so a
     * missing stored value cannot overwrite the synchronous mirror with the
     * default (the original "language reverts to Arabic" bug).
     */
    val storedLanguage: Flow<String?> = safeData.map { it[LANGUAGE] }
    val themeMode: Flow<String> = safeData.map { it[THEME_MODE] ?: DEFAULT_THEME_MODE }
    val pureBlack: Flow<Boolean> = safeData.map { it[PURE_BLACK] ?: false }
    val colorPalette: Flow<String> = safeData.map { it[COLOR_PALETTE] ?: DEFAULT_COLOR_PALETTE }
    val onboardingCompleted: Flow<Boolean> = safeData.map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[BASE_URL] = url }
    }

    suspend fun resetBaseUrl() {
        dataStore.edit { it.remove(BASE_URL) }
    }

    suspend fun setToken(token: String) {
        dataStore.edit { it[TOKEN] = token }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN) }
    }

    suspend fun setEmail(email: String) {
        dataStore.edit { it[EMAIL] = email }
    }

    suspend fun clearEmail() {
        dataStore.edit { it.remove(EMAIL) }
    }

    suspend fun setLanguage(code: String) {
        dataStore.edit { it[LANGUAGE] = code }
        // Apply through the one shared entry point so the cache activities
        // wrap with and the mirror the cold start reads can never diverge.
        // The DataStore value stays the durable source of truth.
        LanguagePrefs.apply(code)
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE] = mode }
        // Refresh the synchronous mirror on the caller's thread so the next
        // launch frame sees this choice without collecting the DataStore flow.
        // DataStore stays the durable source of truth; the other mirrored
        // value is carried over so the two setters cannot erase each other.
        ThemePrefs.seed(mode, ThemePrefs.cachedPureBlack())
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[PURE_BLACK] = enabled }
        // Same entry point as setThemeMode, keeping the cached mode intact.
        ThemePrefs.seed(ThemePrefs.cachedOr(DEFAULT_THEME_MODE), enabled)
    }

    suspend fun setColorPalette(palette: String) {
        dataStore.edit { it[COLOR_PALETTE] = palette }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    suspend fun setColorPalette(palette: Any?) {
        setColorPalette(palette?.toString() ?: DEFAULT_COLOR_PALETTE)
    }

    companion object {
        const val DEFAULT_BASE_URL = ""
        const val DEFAULT_LANGUAGE = "ar"
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_COLOR_PALETTE = "teal"

        private val BASE_URL = stringPreferencesKey("base_url")
        private val TOKEN = stringPreferencesKey("auth_token")
        private val EMAIL = stringPreferencesKey("auth_email")
        private val LANGUAGE = stringPreferencesKey("language")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val COLOR_PALETTE = stringPreferencesKey("color_palette")
        private val PURE_BLACK = booleanPreferencesKey("pure_black")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        fun create(context: Context, name: String = "settings"): SettingsStore =
            SettingsStoreRegistry.getOrCreate(name) {
                // Resolve the file from the passed Context's own filesDir,
                // never through `applicationContext` (which preferences-
                // DataStoreFile uses and which is null before attachBaseContext
                // calls super — the #879 cold-start NPE). Any base or
                // application context resolves to the same filesDir here.
                File(context.filesDir, "datastore/$name.preferences_pb")
            }
    }
}
