package com.caregiver.mobile.data.accounts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #616: onboarding is tracked per account. A second account sees onboarding
 * once; the legacy global flag migrates onto the owner account; no active
 * account reads "not completed" without crashing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingScopingTest {

    private lateinit var file: File
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val activeAccount = MutableStateFlow<String?>("account-a")
    private lateinit var store: OnboardingStore

    @Before
    fun setUp() {
        file = File.createTempFile("onboarding-test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
        ) { file }
        store = OnboardingStore(dataStore, activeAccount)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun accountACompletesButBStillSeesOnboarding() = runTest {
        activeAccount.value = "account-a"
        store.completeOnboarding()
        assertTrue(store.onboardingCompleted.first())

        activeAccount.value = "account-b"
        assertFalse(store.onboardingCompleted.first())
    }

    @Test
    fun bothAccountsCompleteIndependently() = runTest {
        activeAccount.value = "account-a"
        store.completeOnboarding()

        activeAccount.value = "account-b"
        store.completeOnboarding()

        assertTrue(store.onboardingCompleted.first())
        activeAccount.value = "account-a"
        assertTrue(store.onboardingCompleted.first())
    }

    @Test
    fun legacyGlobalFlagMigratesToOwnerAccount() = runTest {
        // Simulate a pre-accounts install: only the legacy global flag set.
        dataStore.edit { it[booleanPreferencesKey("onboarding_completed")] = true }

        // Before migration the owner still sees "not completed" per-account.
        activeAccount.value = "owner"
        assertFalse(store.onboardingCompleted.first())

        store.migrateLegacyFlag("owner")
        assertTrue(store.onboardingCompleted.first())

        // A different account must NOT inherit the legacy flag.
        activeAccount.value = "account-b"
        assertFalse(store.onboardingCompleted.first())
    }

    @Test
    fun noActiveAccountReadsNotCompletedAndDoesNotCrash() = runTest {
        activeAccount.value = null
        assertFalse(store.onboardingCompleted.first())
        store.completeOnboarding() // must be a no-op, not throw
        assertFalse(store.onboardingCompleted.first())
    }
}
