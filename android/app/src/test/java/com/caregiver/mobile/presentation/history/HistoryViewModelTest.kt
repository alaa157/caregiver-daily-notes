package com.caregiver.mobile.presentation.history

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.testAddendum
import com.caregiver.mobile.data.testNote
import com.caregiver.mobile.data.testNoteDetail
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import java.time.LocalDate
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
 * History (board 9): unfiltered load, recipient/date narrowing mapped to
 * exact query params, per-day entries with addendum counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var file: File
    private lateinit var notes: FakeNoteApi
    private lateinit var recipients: FakeRecipientApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("history-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        notes = FakeNoteApi()
        recipients = FakeRecipientApi()
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.noteApi = notes
            it.recipientApi = recipients
        }
        repository = AuthRepository(apis, settings, TokenHolder())
        recipients.listHandler = { emptyList() }
        notes.historyHandler = { _, _, _ -> emptyList() }
        notes.detailHandler = { testNoteDetail() }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun apis() = FakeBackendApis(FakeAuthApi()).also {
        it.noteApi = notes
        it.recipientApi = recipients
    }

    private suspend fun content(vm: HistoryViewModel): HistoryContent {
        val state = vm.state.first { it is HistoryState.Content || it is HistoryState.Error }
        assertTrue("expected Content, was $state", state is HistoryState.Content)
        return (state as HistoryState.Content).content
    }

    @Test
    fun defaultLoadSendsNullFilters() = runTest {
        val vm = HistoryViewModel(apis(), repository, this)

        content(vm)

        assertEquals(Triple(null, null, null), notes.lastHistory)
    }

    @Test
    fun presetFiltersApplyOnFirstLoad() = runTest {
        val vm = HistoryViewModel(
            apis(), repository, this,
            initialRecipientId = "r1",
            initialFrom = LocalDate.of(2026, 10, 1),
            initialTo = LocalDate.of(2026, 10, 8),
        )

        content(vm)

        assertEquals(Triple("r1", "2026-10-01", "2026-10-08"), notes.lastHistory)
    }

    @Test
    fun recipientFilterNarrowsTheList() = runTest {
        recipients.listHandler = {
            listOf(RecipientDto("r1", "A", true), RecipientDto("r2", "B", true))
        }
        notes.historyHandler = { id, _, _ ->
            if (id == "r1") listOf(testNote(id = "n1")) else listOf(testNote(id = "n2"), testNote(id = "n3"))
        }
        val vm = HistoryViewModel(apis(), repository, this)
        content(vm)

        vm.setRecipient("r1")
        val narrowed = vm.state.first {
            it is HistoryState.Content && it.content.entries.size == 1
        } as HistoryState.Content

        assertEquals("n1", narrowed.content.entries.single().note.id)
        assertEquals("r1", notes.lastHistory?.first)
    }

    @Test
    fun entriesCarryAddendumCounts() = runTest {
        notes.historyHandler = { _, _, _ ->
            listOf(testNote(id = "n1"), testNote(id = "n2"))
        }
        notes.detailHandler = { id ->
            if (id == "n1") {
                testNoteDetail(note = testNote(id = "n1"), addenda = listOf(testAddendum(), testAddendum(id = "a2")))
            } else {
                testNoteDetail(note = testNote(id = "n2"))
            }
        }
        val vm = HistoryViewModel(apis(), repository, this)

        val entries = content(vm).entries.associateBy({ it.note.id }, { it.addendumCount })

        assertEquals(mapOf("n1" to 2, "n2" to 0), entries)
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        notes.historyHandler = { _, _, _ -> throw IOException("down") }
        val vm = HistoryViewModel(apis(), repository, this)

        val state = vm.state.first { it is HistoryState.Error }

        assertTrue(state is HistoryState.Error)
    }

    @Test
    fun recipientListFailureSurfacesError() = runTest {
        recipients.listHandler = { throw IOException("down") }
        val vm = HistoryViewModel(apis(), repository, this)

        val state = vm.state.first { it is HistoryState.Error }

        assertTrue(state is HistoryState.Error)
        assertNull(notes.lastHistory)
    }
}
