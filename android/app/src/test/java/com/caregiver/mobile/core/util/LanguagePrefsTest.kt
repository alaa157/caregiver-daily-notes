package com.caregiver.mobile.core.util

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.data.SettingsStore
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The language a user picked must still be applied after the app is closed
 * and reopened. [LanguagePrefs] is the synchronous SharedPreferences mirror
 * the cold-start read in `CaregiverApp.attachBaseContext` serves first, so
 * these pin the contract it leans on:
 *
 * - writing without a host (before any activity exists) is a no-op, never a
 *   crash;
 * - the last written language wins and survives dropping the host — the
 *   in-test stand-in for the process being killed;
 * - `SettingsStore.setLanguage` mirrors through, so every UI write feeds
 *   the mirror the restart path reads.
 */
@RunWith(AndroidJUnit4::class)
class LanguagePrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        LanguagePrefs.resetForTest()
        LanguagePrefs.attachHost(context)
        file = File.createTempFile("language-prefs-test", ".preferences_pb")
        store = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        clearMirror()
    }

    @After
    fun tearDown() {
        clearMirror()
        LanguagePrefs.resetForTest()
        file.delete()
    }

    /** Same file/settings name LanguagePrefs owns; cleared between tests. */
    private fun clearMirror() {
        context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun beforeAHostIsAttachedWriteIsANoOpAndReadIsNull() {
        LanguagePrefs.resetForTest()

        LanguagePrefs.write("en")

        assertNull(LanguagePrefs.read())
    }

    @Test
    fun lastWriteWinsAndSurvivesDroppingTheHost() {
        LanguagePrefs.write("en")
        assertEquals("en", LanguagePrefs.read())

        // Restarting the process drops the static host and re-attaches it.
        LanguagePrefs.resetForTest()
        LanguagePrefs.attachHost(context)

        assertEquals("en", LanguagePrefs.read())

        LanguagePrefs.write("ar")
        assertEquals("ar", LanguagePrefs.read())
    }

    @Test
    fun setLanguageMirrorsThroughToTheMirror() = runTest {
        store.setLanguage("en")

        assertEquals("en", LanguagePrefs.read())

        store.setLanguage("ar")

        assertEquals("ar", LanguagePrefs.read())
    }
}
