package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.testAddendum
import com.caregiver.mobile.data.testNote
import com.caregiver.mobile.data.testNoteDetail
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Note detail (board 7) and addendum (board 8): immutable original, appended
 * corrections, blank-text guard, and failure mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {

    private lateinit var file: File
    private lateinit var notes: FakeNoteApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("detail-note-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        notes = FakeNoteApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.noteApi = notes }
        repository = AuthRepository(apis, settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun vm(scope: TestScope) = NoteDetailViewModel(
        "n1",
        FakeBackendApis(FakeAuthApi()).also { it.noteApi = notes },
        repository,
        scope,
    )

    private suspend fun content(vm: NoteDetailViewModel): NoteContent {
        val state = vm.state.first { it is NoteDetailState.Content || it is NoteDetailState.Error }
        assertTrue("expected Content, was $state", state is NoteDetailState.Content)
        return (state as NoteDetailState.Content).content
    }

    @Test
    fun loadsOriginalWithAddendaInOrder() = runTest {
        val original = testNote(text = "original words")
        notes.detailHandler = {
            testNoteDetail(note = original, addenda = listOf(testAddendum(id = "a1"), testAddendum(id = "a2")))
        }
        val vm = vm(this)

        val c = content(vm)

        assertEquals("original words", c.note.text)
        assertEquals(listOf("a1", "a2"), c.addenda.map { it.id })
    }

    @Test
    fun blankAddendumBlockedWithoutCallingApi() = runTest {
        notes.detailHandler = { testNoteDetail() }
        val vm = vm(this)
        content(vm)

        vm.onAddendumText("  ")
        vm.submitAddendum()

        assertTrue(vm.addendum.value.addendumError)
        assertEquals(null, notes.lastAppend)
    }

    @Test
    fun addendumAppendsAndReloadsWithOriginalIntact() = runTest {
        val original = testNote(text = "original words")
        val stored = mutableListOf(testAddendum(id = "a1"))
        notes.detailHandler = { testNoteDetail(note = original, addenda = stored.toList()) }
        notes.appendHandler = { id, body ->
            testAddendum(id = "a2", noteId = id, text = body.text).also(stored::add)
        }
        val vm = vm(this)
        content(vm)

        vm.onAddendumText("تصحيح جديد")
        vm.submitAddendum()
        val reloaded = vm.state.first {
            it is NoteDetailState.Content && it.content.addenda.size == 2
        } as NoteDetailState.Content

        assertEquals("n1" to "تصحيح جديد", notes.lastAppend?.let { it.first to it.second.text })
        assertEquals("original words", reloaded.content.note.text)
        assertEquals(listOf("a1", "a2"), reloaded.content.addenda.map { it.id })
        assertEquals("", vm.addendum.value.text)
        assertEquals(true, vm.addendum.value.appended)
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        notes.detailHandler = { throw IOException("down") }
        val vm = vm(this)

        val state = vm.state.first { it is NoteDetailState.Error }

        assertTrue(state is NoteDetailState.Error)
    }
}
