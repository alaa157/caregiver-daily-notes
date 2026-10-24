package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeRecipientAdmin
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun serverErrorSurfacesError() = runTest {
        recipients.listHandler = { throw FakeAuthApi.httpError(500, "SERVER_ERROR") }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)

        val state = vm.state.first { it is PeopleState.Error }

        assertTrue(state is PeopleState.Error)
    }

    @Test
    fun unauthorizedLoadLeavesLoadingWithoutCrashing() = runTest {
        recipients.listHandler = { throw LoggedOutException() }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)

        runCurrent()

        assertTrue(vm.state.value is PeopleState.Loading)
    }

    @Test
    fun failedCreateClearsBusyOnHttpError() = runTest {
        recipients.listHandler = { emptyList() }
        recipients.createHandler = { throw FakeAuthApi.httpError(422, "VALIDATION_ERROR", "bad") }
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)
        vm.state.first { it is PeopleState.Content }

        vm.onName("Karim")
        vm.add()
        vm.state.first { it is PeopleState.Error }

        assertFalse(vm.addState.value.busy)
        assertNull(vm.addState.value.addedId)
    }

    @Test
    fun cancelledCreateClearsBusyAndPropagates() = runTest {
        recipients.listHandler = { emptyList() }
        val gate = CompletableDeferred<RecipientDto>()
        recipients.createHandler = { gate.await() }
        val vmScope = TestScope(UnconfinedTestDispatcher())
        val vm = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, vmScope)
        vm.state.first { it is PeopleState.Content }

        vm.onName("Karim")
        vm.add()
        assertTrue(vm.addState.value.busy)
        vmScope.cancel()

        assertFalse(vm.addState.value.busy)
    }

    private fun adminVm(scope: TestScope, admin: FakeRecipientAdmin): RecipientsViewModel {
        recipients.listHandler = { admin.recipients.values.toList() }
        return RecipientsViewModel(
            FakeBackendApis(FakeAuthApi()).also { it.recipientApi = recipients },
            repository,
            scope,
            admin,
        )
    }

    @Test
    fun archiveMovesRecipientToArchived() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true)))
        val vm = adminVm(this, admin)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.archive("r1")
        vm.state.first { it is PeopleState.Content && it.recipients.isEmpty() }

        assertEquals(listOf("r1"), admin.archiveCalls)
        assertEquals("r1", vm.pendingUndoId.value)
        vm.setFilter(PeopleFilter.Archived)
        val archived = vm.state.first {
            it is PeopleState.Content && it.recipients.any { r -> r.id == "r1" }
        } as PeopleState.Content
        assertNotNull(archived.recipients.single().archivedAt)
    }

    @Test
    fun undoArchiveRestoresRecipientAndActiveFilter() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        val vm = adminVm(this, admin)
        vm.setFilter(PeopleFilter.Archived)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.undoArchive("r1")
        vm.state.first { it is PeopleState.Content && it.recipients.isEmpty() }

        assertEquals(listOf("r1"), admin.unarchiveCalls)
        assertNull(vm.pendingUndoId.value)
        vm.setFilter(PeopleFilter.Active)
        val active = vm.state.value as PeopleState.Content
        assertEquals(listOf("r1"), active.recipients.map { it.id })
        assertNull(active.recipients.single().archivedAt)
    }

    @Test
    fun unarchiveRestoresRecipientToList() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        val vm = adminVm(this, admin)
        vm.setFilter(PeopleFilter.Archived)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.unarchive("r1")
        vm.state.first { it is PeopleState.Content && it.recipients.isEmpty() }

        vm.setFilter(PeopleFilter.Active)
        val active = vm.state.value as PeopleState.Content
        assertEquals(listOf("r1"), active.recipients.map { it.id })
        assertNull(active.recipients.single().archivedAt)
    }

    @Test
    fun deleteRemovesArchivedRecipient() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        val vm = adminVm(this, admin)
        vm.setFilter(PeopleFilter.Archived)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.delete("r1")
        vm.state.first { it is PeopleState.Content && it.recipients.isEmpty() }

        assertEquals(listOf("r1"), admin.deleteCalls)
        assertTrue(admin.recipients.isEmpty())
    }

    @Test
    fun deleteNotAvailableForUnarchivedRecipient() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true)))
        val vm = adminVm(this, admin)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.delete("r1")

        assertEquals(PeopleActionError.DeleteFailed, vm.error.value)
        assertTrue(admin.deleteCalls.isEmpty())
        assertTrue(admin.recipients.containsKey("r1"))
    }

    @Test
    fun deleteFailureLeavesRecipientUnchanged() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        admin.deleteError = IOException("db locked")
        val vm = adminVm(this, admin)
        vm.setFilter(PeopleFilter.Archived)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.delete("r1")
        vm.error.first { it == PeopleActionError.DeleteFailed }

        assertTrue(admin.recipients.containsKey("r1"))
        assertTrue((vm.state.value as PeopleState.Content).recipients.any { it.id == "r1" })
        assertFalse("r1" in vm.busyIds.value)
    }

    @Test
    fun archiveFailureShowsErrorAndKeepsRow() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true)))
        admin.archiveError = IOException("db locked")
        val vm = adminVm(this, admin)
        vm.state.first { it is PeopleState.Content && it.recipients.size == 1 }

        vm.archive("r1")
        vm.error.first { it == PeopleActionError.ArchiveFailed }

        assertTrue((vm.state.value as PeopleState.Content).recipients.any { it.id == "r1" })
        assertNull(vm.pendingUndoId.value)
    }

    @Test
    fun countsAreLoadedForConfirmation() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        admin.deletionCounts = RecipientDeletionCounts(1, 2, 3, 4, 5, 6)
        val vm = adminVm(this, admin)
        vm.state.first { it is PeopleState.Content }

        vm.counts("r1")
        val state = vm.deleteCounts.first { it?.counts != null }

        assertEquals(listOf("r1"), admin.countsCalls)
        assertEquals(RecipientDeletionCounts(1, 2, 3, 4, 5, 6), state?.counts)
        assertEquals("r1", state?.recipientId)
    }

    @Test
    fun sharedInstanceRefreshBringsNewcomerToList() = runTest {
        // Mirrors production: list and add screens share one VM instance, so
        // the creation refresh lands in the visible list on return.
        val people = mutableListOf<RecipientDto>()
        recipients.listHandler = { people.toList() }
        recipients.createHandler = { RecipientDto("9", it.name, true).also(people::add) }
        val shared = RecipientsViewModel(FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
        }, repository, this)
        shared.state.first { it is PeopleState.Content }

        shared.onName("Karim")
        shared.add()
        val updated = shared.state.first {
            it is PeopleState.Content && it.recipients.any { r -> r.id == "9" }
        } as PeopleState.Content

        assertEquals(listOf(RecipientDto("9", "Karim", true)), updated.recipients)
        assertEquals("9", shared.addState.value.addedId)
        shared.consumeAdded()
        assertNull(shared.addState.value.addedId)
    }
}
