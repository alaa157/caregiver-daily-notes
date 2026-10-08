package com.caregiver.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://CHANGE-ME"
        const val DEFAULT_LANGUAGE = "ar"

        private val BASE_URL = stringPreferencesKey("base_url")
        private val TOKEN = stringPreferencesKey("auth_token")
        private val EMAIL = stringPreferencesKey("auth_email")
        private val LANGUAGE = stringPreferencesKey("language")

        fun create(context: Context, name: String = "settings"): SettingsStore =
            SettingsStoreRegistry.getOrCreate(name) { context.preferencesDataStoreFile(name) }
    }
}
