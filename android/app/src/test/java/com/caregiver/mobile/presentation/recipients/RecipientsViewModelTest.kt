package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.RecipientDto
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Recipients list + add-person logic: loading, blank-name validation, create
 * round-trip with list refresh, and failure mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipientsViewModelTest {

    private lateinit var file: File
    private lateinit var recipients: FakeRecipientApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("people-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        recipients = FakeRecipientApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.recipientApi = recipients }
        repository = AuthRepository(apis, settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun loadsListOnStart() = runTest {
        recipients.listHandler = { listOf(RecipientDto("1", "Aisha", true)) }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)

        val state = vm.state.first { it is PeopleState.Content }

        assertEquals(listOf(RecipientDto("1", "Aisha", true)), (state as PeopleState.Content).recipients)
    }

    @Test
    fun blankNameBlockedWithoutCallingApi() = runTest {
        recipients.listHandler = { emptyList() }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)
        vm.state.first { it is PeopleState.Content }

        vm.onName("   ")
        vm.add()

        assertEquals(NameError.Required, vm.addState.value.nameError)
        assertNull(recipients.lastCreatedName)
    }

    @Test
    fun validNameCreatesRefreshesAndSignalsAdded() = runTest {
        val people = mutableListOf<RecipientDto>()
        recipients.listHandler = { people.toList() }
        recipients.createHandler = { RecipientDto("9", it.name, true).also(people::add) }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)
        vm.state.first { it is PeopleState.Content }

        vm.onName("  Karim  ")
        vm.add()
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        assertEquals("Karim", recipients.lastCreatedName)
        assertEquals("9", vm.addState.value.addedId)
        assertEquals(listOf(RecipientDto("9", "Karim", true)), (vm.state.value as PeopleState.Content).recipients)
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        recipients.listHandler = { throw IOException("down") }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)

        val state = vm.state.first { it is PeopleState.Error }

        assertTrue(state is PeopleState.Error)
    }
}
