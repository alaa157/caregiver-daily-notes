package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.testNote
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
 * Note editor logic (board 5): single-select chip groups, pain clamping,
 * mood/text requirements, exact DTO submission, and failure mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {

    private lateinit var file: File
    private lateinit var notes: FakeNoteApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("editor-vm-test", ".preferences_pb")
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

    private fun vm(scope: TestScope) = NoteEditorViewModel("r1", notesApi(), repository, scope)

    private fun notesApi() = FakeBackendApis(FakeAuthApi()).also { it.noteApi = notes }

    @Test
    fun chipsAreSingleSelectPerGroupWithToggleOff() = runTest {
        val vm = vm(this)

        vm.select(OptionGroup.Mood, "good")
        vm.select(OptionGroup.Mood, "bad")
        assertEquals("bad", vm.state.value.mood)

        vm.select(OptionGroup.Mood, "bad")
        assertNull(vm.state.value.mood)

        vm.select(OptionGroup.Appetite, "poor")
        assertEquals("poor", vm.state.value.appetite)
        assertNull(vm.state.value.mood)
    }

    @Test
    fun painClampsToZeroToTen() = runTest {
        val vm = vm(this)

        vm.setPain(11)
        assertEquals(10, vm.state.value.pain)
        vm.setPain(-3)
        assertEquals(0, vm.state.value.pain)
        vm.setPain(7)
        assertEquals(7, vm.state.value.pain)
    }

    @Test
    fun missingMoodBlocksSubmit() = runTest {
        val vm = vm(this)
        vm.onText("some text")

        vm.submit()

        assertEquals(EditorError.MoodRequired, vm.state.value.moodError)
        assertNull(notes.lastCreate)
    }

    @Test
    fun blankTextBlocksSubmit() = runTest {
        val vm = vm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.onText("  ")

        vm.submit()

        assertTrue(vm.state.value.textError != null)
        assertNull(notes.lastCreate)
    }

    @Test
    fun validSubmitSendsExactDtoWithArabicTextIntact() = runTest {
        val arabic = "أكل جيداً ونام متقطعاً"
        notes.createHandler = { testNote(id = "n9", text = it.text) }
        val vm = vm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.select(OptionGroup.Medication, "unsure")
        vm.setPain(4)
        vm.setFall(true)
        vm.onText(arabic)

        vm.submit()
        vm.state.first { it.savedId != null }

        val sent = notes.lastCreate!!
        assertEquals("r1", sent.recipientId)
        assertEquals("good", sent.mood)
        assertEquals("", sent.appetite)
        assertEquals("unsure", sent.medicationTaken)
        assertEquals(4, sent.pain)
        assertEquals(true, sent.fall)
        assertEquals(arabic, sent.text)
        assertEquals("n9", vm.state.value.savedId)
    }

    @Test
    fun duplicateTodaySurfacesDuplicateDayCode() = runTest {
        notes.createHandler = {
            throw FakeAuthApi.httpError(422, "VALIDATION_ERROR", "A note already exists for this recipient today.")
        }
        val vm = vm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.onText("text")

        vm.submit()
        val error = vm.state.first { it.formError != null }.formError

        assertTrue(error is EditorError.Rejected)
        assertEquals("VALIDATION_ERROR", (error as EditorError.Rejected).code)
        assertEquals(false, vm.state.value.busy)
    }

    @Test
    fun errorCodesMapToLocalizedCopy() {
        // Spec copy owns a single generic error string; the duplicate-day
        // distinction returns in Step 5 with the editor rewrite.
        assertEquals(
            com.caregiver.mobile.R.string.state_error,
            EditorErrorText.res("VALIDATION_ERROR"),
        )
        assertEquals(
            com.caregiver.mobile.R.string.state_error,
            EditorErrorText.res("SOME_FUTURE_CODE"),
        )
        assertEquals(
            com.caregiver.mobile.R.string.state_error,
            EditorErrorText.res(null),
        )
    }

    @Test
    fun networkFailureSurfacesUnreachable() = runTest {
        notes.createHandler = { throw IOException("down") }
        val vm = vm(this)
        vm.select(OptionGroup.Mood, "good")
        vm.onText("text")

        vm.submit()
        val error = vm.state.first { it.formError != null }.formError

        assertEquals(EditorError.Unreachable, error)
    }
}
