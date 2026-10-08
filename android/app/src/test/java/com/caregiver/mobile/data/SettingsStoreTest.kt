package com.caregiver.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Settings survive process death in DataStore: base URL (runtime-editable per
 * issue #27), auth token (write-only, never logged), and UI language.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private lateinit var file: File
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        file = File.createTempFile("settings-test", ".preferences_pb")
        store = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun baseUrlDefaultsToPlaceholder() = runTest {
        assertEquals(SettingsStore.DEFAULT_BASE_URL, store.baseUrl.first())
    }

    @Test
    fun baseUrlRoundTripsAndResets() = runTest {
        store.setBaseUrl("https://demo.example:8080/")
        assertEquals("https://demo.example:8080/", store.baseUrl.first())
        store.resetBaseUrl()
        assertEquals(SettingsStore.DEFAULT_BASE_URL, store.baseUrl.first())
    }

    @Test
    fun tokenStartsNullRoundTripsAndClears() = runTest {
        assertNull(store.token.first())
        store.setToken("jwt-token")
        assertEquals("jwt-token", store.token.first())
        store.clearToken()
        assertNull(store.token.first())
    }

    @Test
    fun languageDefaultsToArabicAndRoundTrips() = runTest {
        assertEquals("ar", store.language.first())
        store.setLanguage("en")
        assertEquals("en", store.language.first())
        store.setLanguage("ar")
        assertEquals("ar", store.language.first())
    }

    @Test
    fun emailStartsNullRoundTripsAndClears() = runTest {
        assertNull(store.email.first())
        store.setEmail("a@b.c")
        assertEquals("a@b.c", store.email.first())
        store.clearEmail()
        assertNull(store.email.first())
    }
}
