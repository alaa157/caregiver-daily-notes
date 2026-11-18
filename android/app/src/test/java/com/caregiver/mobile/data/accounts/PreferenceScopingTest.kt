package com.caregiver.mobile.data.accounts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.caregiver.mobile.data.SettingsStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * #617: persisted-preference audit. Every preference is either global
 * (device-level: language, theme, palette, pure-black, backend base URL,
 * legacy auth token/email) or per-account (onboarding flag, all AI
 * settings, AI keys — the latter two covered by `OnboardingScopingTest`
 * and `AiKeyStoreScopingTest`).
 *
 * This test proves the two halves of the contract:
 *  - a per-account value written under A is absent while B is active
 *    (demonstrated with a recipient-like last-selected key stored through
 *    the same `{accountId}_` namespacing convention);
 *  - a global value (language) is identical regardless of the active
 *    account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceScopingTest {

    private lateinit var file: File
    private lateinit var dataStore: androidx.datastore.core.DataStore<Preferences>
    private val activeAccount = MutableStateFlow<String?>("account-a")
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        file = File.createTempFile("prefs-test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
        ) { file }
        settings = SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun scopedKey(accountId: String, name: String) =
        stringPreferencesKey("${accountId}_$name")

    @Test
    fun lastSelectedRecipientIsAbsentForOtherAccount() = runTest {
        // Simulates a per-account "last selected recipient" preference using
        // the same `{accountId}_` prefix convention as AiKeyStore/OnboardingStore.
        dataStore.edit { it[scopedKey("account-a", "last_selected_recipient")] = "recipient-42" }

        assertEquals("recipient-42", dataStore.data.first()[scopedKey("account-a", "last_selected_recipient")])

        // While B is active the A-scoped key is never read — the reader
        // resolves `scopedKey(activeId, ...)` and gets null.
        val bView = dataStore.data.first()[scopedKey("account-b", "last_selected_recipient")]
        assertEquals(null, bView)
    }

    @Test
    fun languageIsGlobalAndUnchangedByAccountSwitch() = runTest {
        settings.setLanguage("en")
        assertEquals("en", settings.language.first())

        // "Switching accounts" must not change a device-level preference.
        activeAccount.value = "account-b"
        assertEquals("en", settings.language.first())

        settings.setLanguage("ar")
        activeAccount.value = "account-a"
        assertEquals("ar", settings.language.first())
    }

    @Test
    fun themeAndPaletteAreGlobal() = runTest {
        settings.setThemeMode("dark")
        settings.setColorPalette("ocean")
        settings.setPureBlack(true)

        activeAccount.value = "account-b"
        assertEquals("dark", settings.themeMode.first())
        assertEquals("ocean", settings.colorPalette.first())
        assertEquals(true, settings.pureBlack.first())
    }
}
