package com.caregiver.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.caregiver.mobile.core.util.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted settings: runtime-editable backend URL (issue #27), auth token,
 * and UI language. The token is write-only from the UI's perspective — this
 * store never logs it and no screen reads it except as present/absent.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val baseUrl: Flow<String> = dataStore.data.map { it[BASE_URL] ?: DEFAULT_BASE_URL }
    val token: Flow<String?> = dataStore.data.map { it[TOKEN] }
    val email: Flow<String?> = dataStore.data.map { it[EMAIL] }
    val language: Flow<String> = dataStore.data.map { it[LANGUAGE] ?: DEFAULT_LANGUAGE }
    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: DEFAULT_THEME_MODE }
    val pureBlack: Flow<Boolean> = dataStore.data.map { it[PURE_BLACK] ?: false }
    val colorPalette: Flow<String> = dataStore.data.map { it[COLOR_PALETTE] ?: DEFAULT_COLOR_PALETTE }

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
        // Mirror the choice into the process cache the activities wrap their
        // base context with. The DataStore value stays the source of truth;
        // this only lets a recreation apply it without a disk read.
        LocaleHelper.cache(code)
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[PURE_BLACK] = enabled }
    }

    suspend fun setColorPalette(palette: String) {
        dataStore.edit { it[COLOR_PALETTE] = palette }
    }

    suspend fun setColorPalette(palette: Any?) {
        setColorPalette(palette?.toString() ?: DEFAULT_COLOR_PALETTE)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://CHANGE-ME"
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

        fun create(context: Context, name: String = "settings"): SettingsStore =
            SettingsStoreRegistry.getOrCreate(name) { context.preferencesDataStoreFile(name) }
    }
}
