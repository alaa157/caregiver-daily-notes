package com.caregiver.mobile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.RetrofitBackendApis
import com.caregiver.mobile.data.demo.DemoBackendApis
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Task 1: the offline demo switch stays isolated per build variant.
 * The same test source runs for debug, release, and minified unit-test
 * variants; the flag assertion holds in each because the manifest of
 * expectations travels with the variant's own BuildConfig.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphProviderTest {

    @Test
    fun demoFlagMatchesDebuggableVariant() {
        assertEquals(BuildConfig.DEBUG, BuildConfig.DEMO_BACKEND)
    }

    @Test
    fun selectorHonorsFlag() {
        val file = File.createTempFile("provider-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { file },
        )
        val demo = DemoBackendApis()
        val production = RetrofitBackendApis(settings, TokenHolder())
        assertSame(demo, selectApis(true, demo, production))
        assertSame(production, selectApis(false, demo, production))
    }
}
