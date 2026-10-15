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
 * `debug` and `minified` are the offline demo; `release` is production.
 * The same test source runs for each unit-test variant and keys off this
 * variant's own [BuildConfig.BUILD_TYPE], so the expectation travels with
 * the variant instead of assuming demo == debuggable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphProviderTest {

    @Test
    fun demoFlagMatchesBuildType() {
        val expectedDemo = BuildConfig.BUILD_TYPE == "debug" || BuildConfig.BUILD_TYPE == "minified"
        assertEquals(expectedDemo, BuildConfig.DEMO_BACKEND)
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
