package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.NoteDto
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

    private fun vm(scope: TestScope) = RecipientDetailViewModel(
        "r1",
        FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
            it.noteApi = notes
        },
        repository,
        scope,
    )

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
}
