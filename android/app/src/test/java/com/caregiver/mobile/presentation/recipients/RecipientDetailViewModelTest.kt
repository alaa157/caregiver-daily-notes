package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientAdmin
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Recipient detail logic: header data, latest note, the four design actions
 * with recipient context, and failure mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipientDetailViewModelTest {

    private lateinit var file: File
    private lateinit var recipients: FakeRecipientApi
    private lateinit var notes: FakeNoteApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("detail-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        recipients = FakeRecipientApi()
        notes = FakeNoteApi()
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
            it.noteApi = notes
        }
        repository = AuthRepository(apis, settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun vm(scope: TestScope, admin: RecipientAdmin? = null) = RecipientDetailViewModel(
        "r1",
        FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
            it.noteApi = notes
        },
        repository,
        scope,
        admin,
    )

    private fun adminVm(scope: TestScope, admin: FakeRecipientAdmin): RecipientDetailViewModel {
        recipients.listHandler = { admin.recipients.values.toList() }
        notes.historyHandler = { _, _, _ -> emptyList() }
        return vm(scope, admin)
    }

    @Test
    fun loadsRecipientLatestNoteAndFourActions() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(
                NoteDto("o", "r1", "2026-10-01", "bad", "poor", "broken", "bed", "missed", 6, false, "t"),
                NoteDto("n", "r1", "2026-10-07", "good", "ok", "fine", "walks", "taken", 2, false, "t"),
            )
        }
        val vm = vm(this)

        val state = vm.state.first { it is DetailState.Content }
        val content = (state as DetailState.Content).detail

        assertEquals("Aisha", content.name)
        assertEquals("n", content.lastNote?.id)
        assertEquals(
            listOf(DetailAction.AddNote, DetailAction.History, DetailAction.CarePlan, DetailAction.Summary),
            content.actions,
        )
    }

    @Test
    fun noNotesStillShowsActions() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        notes.historyHandler = { _, _, _ -> emptyList() }
        val vm = vm(this)

        val state = vm.state.first { it is DetailState.Content }
        val content = (state as DetailState.Content).detail

        assertNull(content.lastNote)
        assertEquals(4, content.actions.size)
    }

    @Test
    fun unknownRecipientSurfacesError() = runTest {
        recipients.listHandler = { emptyList() }
        notes.historyHandler = { _, _, _ -> emptyList() }
        val vm = vm(this)

        val state = vm.state.first { it is DetailState.Error }

        assertTrue(state is DetailState.Error)
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        recipients.listHandler = { throw IOException("down") }
        val vm = vm(this)

        val state = vm.state.first { it is DetailState.Error }

        assertTrue(state is DetailState.Error)
    }

    @Test
    fun serverErrorSurfacesError() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        notes.historyHandler = { _, _, _ ->
            throw com.caregiver.mobile.data.FakeAuthApi.httpError(500, "SERVER_ERROR")
        }
        val vm = vm(this)

        val state = vm.state.first { it is DetailState.Error }

        assertTrue(state is DetailState.Error)
    }

    @Test
    fun retryReloadsAfterFailure() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        var fail = true
        notes.historyHandler = { _, _, _ ->
            if (fail) throw IOException("down")
            else listOf(com.caregiver.mobile.data.testNote(id = "n1"))
        }
        val vm = vm(this)
        vm.state.first { it is DetailState.Error }

        fail = false
        vm.refresh()
        val state = vm.state.first { it is DetailState.Content }

        assertEquals("n1", (state as DetailState.Content).detail.lastNote?.id)
    }

    @Test
    fun detailArchiveKeepsDetailOpenAndReadOnly() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true)))
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content && !it.detail.archived }

        vm.archive()
        val state = vm.state.first { it is DetailState.Content && it.detail.archived }

        assertEquals(listOf("r1"), admin.archiveCalls)
        assertEquals("Aisha", (state as DetailState.Content).detail.name)
        assertFalse(vm.busy.value)
    }

    @Test
    fun detailUnarchiveRestoresEditableState() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content && it.detail.archived }

        vm.unarchive()
        val state = vm.state.first { it is DetailState.Content && !it.detail.archived }

        assertEquals(listOf("r1"), admin.unarchiveCalls)
        assertFalse((state as DetailState.Content).detail.archived)
    }

    @Test
    fun detailDeleteNavigatesBack() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content && it.detail.archived }

        vm.delete()

        assertTrue(vm.deleted.first { it })
        assertEquals(listOf("r1"), admin.deleteCalls)
        assertTrue(admin.recipients.isEmpty())
    }

    @Test
    fun detailDeleteRejectedForUnarchivedRecipient() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true)))
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content && !it.detail.archived }

        vm.delete()

        assertEquals(DetailActionError.DeleteUnavailable, vm.error.value)
        assertTrue(admin.deleteCalls.isEmpty())
        assertFalse(vm.deleted.value)
    }

    @Test
    fun detailDeleteFailureKeepsDetailOpen() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        admin.deleteError = IOException("db locked")
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content && it.detail.archived }

        vm.delete()
        vm.error.first { it == DetailActionError.DeleteFailed }

        assertFalse(vm.deleted.value)
        assertTrue(admin.recipients.containsKey("r1"))
        assertTrue(vm.state.value is DetailState.Content)
    }

    @Test
    fun detailCountsAreLoadedForConfirmation() = runTest {
        val admin = FakeRecipientAdmin(listOf(RecipientDto("r1", "Aisha", true, 5L)))
        admin.deletionCounts = RecipientDeletionCounts(1, 2, 3, 4, 5, 6)
        val vm = adminVm(this, admin)
        vm.state.first { it is DetailState.Content }

        vm.counts()
        val counts = vm.deleteCounts.first { it?.counts != null }

        assertEquals(RecipientDeletionCounts(1, 2, 3, 4, 5, 6), counts?.counts)
        assertEquals("r1", counts?.recipientId)
    }

    @Test
    fun actionsHiddenWhenAdminMissing() = runTest {
        val vm = vm(this)
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        notes.historyHandler = { _, _, _ -> emptyList() }
        vm.refresh()
        vm.state.first { it is DetailState.Content }

        vm.archive()
        vm.counts()
        vm.delete()
        runCurrent()

        assertFalse(vm.busy.value)
        assertFalse(vm.deleted.value)
        assertNull(vm.error.value)
        assertNull(vm.deleteCounts.value)
    }
}
