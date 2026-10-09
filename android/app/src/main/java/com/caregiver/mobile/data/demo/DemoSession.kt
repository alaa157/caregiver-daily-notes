package com.caregiver.mobile.data.demo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.demoSessionStore by preferencesDataStore("demo_session")

/**
 * Local demo session (Step 6). "Start demo" sets the flag; sign-out clears
 * it. No credentials, no network, and no password is ever stored.
 */
class DemoSession(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.demoSessionStore)

    /** Null until the first DataStore read lands (root splash state). */
    val active: Flow<Boolean?> = store.data.map { it[KEY] }

    suspend fun startDemo() {
        store.edit { it[KEY] = true }
    }

    suspend fun signOut() {
        store.edit { it[KEY] = false }
    }

    companion object {
        private val KEY = booleanPreferencesKey("demo_active")
    }
}
