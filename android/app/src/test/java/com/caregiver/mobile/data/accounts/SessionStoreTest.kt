package com.caregiver.mobile.data.accounts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** #592: the active-account id survives restart, and nothing else is stored. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        file = File.createTempFile("session-store-test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
        ) { file }
        store = SessionStore(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun idStartsNullRoundTripsAndClears() = runTest {
        assertNull(store.activeAccountId.first())

        store.setActiveAccountId("acc-1")
        assertEquals("acc-1", store.activeAccountId.first())

        store.clearActiveAccountId()
        assertNull(store.activeAccountId.first())
    }

    @Test
    fun aFreshStoreInstanceReadsThePersistedId() = runTest {
        store.setActiveAccountId("acc-2")

        // The wrapper holds no state: a new instance over the same DataStore —
        // as happens after process death — reads the id back from disk.
        val recreated = SessionStore(dataStore)
        assertEquals("acc-2", recreated.activeAccountId.first())
    }

    @Test
    fun onlyTheAccountIdKeyIsPersisted() = runTest {
        store.setActiveAccountId("acc-3")

        val persisted = dataStore.data.first().asMap()
        assertEquals(setOf(SessionStore.ACTIVE_ACCOUNT_ID), persisted.keys)
        assertFalse(persisted.values.any { value ->
            value is String && (value.contains("hash", ignoreCase = true) || value.contains("salt", ignoreCase = true))
        })
    }

    @Test
    fun aClearedIdStaysClearedAfterRecreation() = runTest {
        store.setActiveAccountId("acc-before")
        store.clearActiveAccountId()

        val recreated = SessionStore(dataStore)
        assertNull(recreated.activeAccountId.first())
    }

    @Test
    fun theKeyNameMatchesTheIssueContract() {
        assertEquals("active_account_id", SessionStore.ACTIVE_ACCOUNT_ID.name)
    }
}
