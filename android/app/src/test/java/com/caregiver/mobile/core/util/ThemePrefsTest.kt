package com.caregiver.mobile.core.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.caregiver.mobile.core.theme.THEME_MODE_DARK
import com.caregiver.mobile.core.theme.THEME_MODE_LIGHT
import com.caregiver.mobile.data.SettingsStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The launch window colour is chosen before the theme flows are collected, so
 * the values it reads have to be in memory already. These pin the contract
 * `CaregiverApp.onCreate` and the settings setters feed into [ThemePrefs]:
 * a seed is served synchronously, setter writes refresh it without a restart,
 * and a missing/unreadable stored value never leaves a stale choice behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePrefsTest {

    private lateinit var file: File
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        ThemePrefs.resetForTest()
        file = File.createTempFile("theme-prefs-test", ".preferences_pb")
        store = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
    }

    @After
    fun tearDown() {
        ThemePrefs.resetForTest()
        file.delete()
    }

    @Test
    fun cachedOrReturnsTheSeededMode() {
        ThemePrefs.seed(THEME_MODE_LIGHT, false)

        assertEquals(THEME_MODE_LIGHT, ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE))
    }

    @Test
    fun cachedPureBlackReturnsTheSeededValue() {
        ThemePrefs.seed(THEME_MODE_DARK, true)

        assertTrue(ThemePrefs.cachedPureBlack())

        ThemePrefs.seed(THEME_MODE_DARK, false)

        assertFalse(ThemePrefs.cachedPureBlack())
    }

    @Test
    fun settingsSettersRefreshTheMirrorWithoutARestart() = runTest {
        store.setThemeMode(THEME_MODE_DARK)

        assertEquals(THEME_MODE_DARK, ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE))

        store.setPureBlack(true)

        assertTrue(ThemePrefs.cachedPureBlack())
        // Neither setter erases the other's mirrored value.
        assertEquals(THEME_MODE_DARK, ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE))
    }

    @Test
    fun missingOrUnreadableStoredValuesLeaveTheMirrorAtTheDefault() = runTest {
        // Fresh install: nothing has been written to DataStore yet.
        ThemePrefs.seed(store.themeMode.first(), store.pureBlack.first())

        assertEquals(
            SettingsStore.DEFAULT_THEME_MODE,
            ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE),
        )
        assertFalse(ThemePrefs.cachedPureBlack())

        // Corrupt/unreadable file: safeData degrades to the same defaults.
        val unreadable = SettingsStore(FailingDataStore())
        ThemePrefs.seed(unreadable.themeMode.first(), unreadable.pureBlack.first())

        assertEquals(
            SettingsStore.DEFAULT_THEME_MODE,
            ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE),
        )
        assertFalse(ThemePrefs.cachedPureBlack())
    }

    @Test
    fun seedIsSafeToCallTwice() = runTest {
        ThemePrefs.seed(THEME_MODE_DARK, true)
        ThemePrefs.seed(THEME_MODE_DARK, true)

        assertEquals(THEME_MODE_DARK, ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE))
        assertTrue(ThemePrefs.cachedPureBlack())

        // A read that produced no mode must not replace a mirrored choice
        // with the default (the same guard the language restore uses).
        ThemePrefs.seed(null, true)

        assertEquals(THEME_MODE_DARK, ThemePrefs.cachedOr(SettingsStore.DEFAULT_THEME_MODE))
        assertTrue(ThemePrefs.cachedPureBlack())
    }
}

/** Same read failure SettingsStoreTest uses; the flows degrade to defaults. */
private class FailingDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("settings disk unavailable") }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("settings disk unavailable")
}
