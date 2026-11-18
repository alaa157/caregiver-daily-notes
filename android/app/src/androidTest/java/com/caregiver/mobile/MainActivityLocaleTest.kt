package com.caregiver.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `SettingsScrollUiTest` language checks can only see the chips; this
 * test proves the app actually switches locale. It drives the real
 * [MainActivity] through the same flow the chips use — save, then
 * recreate — and asserts the activity's own resources carry the stored
 * language and the matching layout direction.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLocaleTest {

    private val store: SettingsStore
        get() = SettingsStore.create(ApplicationProvider.getApplicationContext())

    @After
    fun restoreDefaultLanguage() {
        runBlocking { store.setLanguage(SettingsStore.DEFAULT_LANGUAGE) }
    }

    private fun layoutDirectionOf(context: Context): Int =
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK

    @Test
    fun storedLanguageDrivesActivityResourcesAndLayoutDirection() {
        runBlocking { store.setLanguage("en") }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("en", activity.resources.configuration.locale.language)
                assertEquals(
                    Configuration.SCREENLAYOUT_LAYOUTDIR_LTR,
                    layoutDirectionOf(activity),
                )
            }

            // Exactly what the English/Arabic chips do: persist the choice,
            // then recreate. The new activity must come up in Arabic + RTL.
            runBlocking { store.setLanguage("ar") }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("ar", activity.resources.configuration.locale.language)
                assertEquals(
                    Configuration.SCREENLAYOUT_LAYOUTDIR_RTL,
                    layoutDirectionOf(activity),
                )
            }
        }
    }
}
