package com.caregiver.mobile.presentation.notes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.NoteDraftStore
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.testNote
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * #906 autosave behaviour: a draft survives a fresh editor (i.e. an app
 * restart), is scoped to one person and one day, and is cleared on save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorDraftViewModelTest {

    private lateinit var file: File
    private lateinit var notes: FakeNoteApi
    private lateinit var repository: AuthRepository
    private lateinit var drafts: NoteDraftStore

    private val dayOne = Clock.fixed(Instant.parse("2026-11-17T10:15:30Z"), ZoneOffset.UTC)
    private val dayTwo = Clock.fixed(Instant.parse("2026-11-18T10:15:30Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        file = File.createTempFile("draft-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        notes = FakeNoteApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.noteApi = notes }
        repository = AuthRepository(apis, settings, TokenHolder())
        drafts = NoteDraftStore.inMemory()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun newVm(
        scope: TestScope,
        recipientId: String = "r1",
        clock: Clock = this@NoteEditorDraftViewModelTest.dayOne,
    ) = NoteEditorViewModel(
        recipientId,
        FakeBackendApis(FakeAuthApi()).also { it.noteApi = notes },
        repository,
        scope,
        drafts,
        clock,
    )

    @Test
    fun everyChangeIsAutosavedAndRestoredWhenTheEditorReopens() = runTest {
        val vm = newVm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.onText("in progress")

        val savedAt = vm.state.value.draftSavedAt
        assertNotNull(savedAt)
        assertEquals(dayOne.millis(), savedAt)

        // A fresh ViewModel models reopening the editor / relaunching the app.
        val reopened = newVm(this)
        assertEquals("good", reopened.state.value.mood)
        assertEquals("in progress", reopened.state.value.text)
        assertEquals(savedAt, reopened.state.value.draftSavedAt)
    }

    @Test
    fun aDraftNeverLeaksIntoAnotherDay() = runTest {
        val vm = newVm(this)
        vm.select(OptionGroup.Mood, "good")

        val nextDay = newVm(this, clock = dayTwo)

        assertNull(nextDay.state.value.mood)
        assertNull(nextDay.state.value.draftSavedAt)
    }

    @Test
    fun aDraftNeverLeaksIntoAnotherPerson() = runTest {
        val vm = newVm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.setPain(4)

        val other = newVm(this, recipientId = "r2")

        assertNull(other.state.value.mood)
        assertNull(other.state.value.pain)
        assertNull(other.state.value.draftSavedAt)
    }

    @Test
    fun successfulSaveDiscardsTheDraft() = runTest {
        notes.createHandler = { testNote(id = "n9") }
        val key = NoteDraftStore.key("r1", "2026-11-17")
        val vm = newVm(this)
        vm.select(OptionGroup.Mood, "good")
        assertNotNull(drafts.load(key))

        vm.submit()
        vm.state.first { it.savedId != null }

        assertNull(vm.state.value.draftSavedAt)
        assertNull(drafts.load(key))
        // A reopened editor has nothing stale to restore.
        assertNull(newVm(this).state.value.mood)
    }

    @Test
    fun oneAnsweredFieldIsEnoughToSave() = runTest {
        notes.createHandler = { testNote(id = "n10") }
        val vm = newVm(this)
        vm.setPain(0) // a recorded zero still counts as an answer

        vm.submit()
        vm.state.first { it.savedId != null }

        assertEquals(0, notes.lastCreate!!.pain)
        assertEquals(true, notes.lastCreate!!.painRecorded)
    }

    @Test
    fun nothingAnsweredShowsTheShortMessageAndDoesNotSave() = runTest {
        val vm = newVm(this)

        vm.submit()

        assertEquals(EditorError.NothingAnswered, vm.state.value.formError)
        assertNull(notes.lastCreate)
    }
}
