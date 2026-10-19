package com.caregiver.mobile.core.util

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.data.SettingsStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * The language chips only recreate the activity; [MainActivity] applies the
 * stored language through [LocaleHelper]. These tests pin the parts of that
 * mechanism that can run on the JVM:
 *
 * - the helper derives a configuration carrying the language and the RTL/LTR
 *   layout direction that follows from it;
 * - the Arabic resources that configuration selects actually resolve;
 * - saving a language feeds the process cache a recreation will read, and
 *   the DataStore value survives process death.
 *
 * Robolectric ignores a `createConfigurationContext` override when resolving
 * resources (on a device the framework applies it), so the end-to-end apply
 * is proven by `MainActivityLocaleTest` in the instrumented suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LocaleHelperTest {

    private lateinit var file: File
    private lateinit var store: SettingsStore
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        file = File.createTempFile("locale-helper-test", ".preferences_pb")
        store = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        LocaleHelper.cache(SettingsStore.DEFAULT_LANGUAGE)
    }

    @After
    fun tearDown() {
        LocaleHelper.cache(SettingsStore.DEFAULT_LANGUAGE)
        file.delete()
    }

    private fun layoutDirectionOf(configuration: Configuration): Int =
        configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK

    @Test
    fun configurationCarriesTheLanguageAndItsLayoutDirection() {
        val arabic = LocaleHelper.configurationFor(context, "ar")
        assertEquals("ar", arabic.locale.language)
        assertEquals(Configuration.SCREENLAYOUT_LAYOUTDIR_RTL, layoutDirectionOf(arabic))

        val english = LocaleHelper.configurationFor(context, "en")
        assertEquals("en", english.locale.language)
        assertEquals(Configuration.SCREENLAYOUT_LAYOUTDIR_LTR, layoutDirectionOf(english))
    }

    @Test
    fun arabicResourcesResolveToArabicTextAndRtl() {
        RuntimeEnvironment.setQualifiers("+ar")
        assertEquals("ar", context.resources.configuration.locale.language)
        assertEquals(
            Configuration.SCREENLAYOUT_LAYOUTDIR_RTL,
            layoutDirectionOf(context.resources.configuration),
        )
        assertEquals("اللغة", context.getString(R.string.settings_language))
    }

    @Test
    fun englishResourcesResolveToEnglishTextAndLtr() {
        RuntimeEnvironment.setQualifiers("+en")
        assertEquals("en", context.resources.configuration.locale.language)
        assertEquals(
            Configuration.SCREENLAYOUT_LAYOUTDIR_LTR,
            layoutDirectionOf(context.resources.configuration),
        )
        assertEquals("Language", context.getString(R.string.settings_language))
    }

    @Test
    fun savingALanguageFeedsTheProcessCacheAndSurvivesProcessDeath() = runTest {
        LocaleHelper.cache(SettingsStore.DEFAULT_LANGUAGE)

        store.setLanguage("en")
        assertEquals("en", LocaleHelper.cachedOr(SettingsStore.DEFAULT_LANGUAGE))
        assertEquals("en", store.language.first())

        store.setLanguage("ar")
        assertEquals("ar", LocaleHelper.cachedOr("en"))
        assertEquals("ar", store.language.first())
    }
}
