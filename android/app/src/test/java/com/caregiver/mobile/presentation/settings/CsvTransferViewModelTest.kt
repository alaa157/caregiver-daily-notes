package com.caregiver.mobile.presentation.settings

import android.net.Uri
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.csv.CsvCodec
import com.caregiver.mobile.data.csv.CsvDocumentIo
import com.caregiver.mobile.data.csv.CsvFailure
import com.caregiver.mobile.data.csv.CsvFailureReason
import com.caregiver.mobile.data.csv.CsvImportPlanner
import com.caregiver.mobile.data.csv.CsvImportTooLargeException
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.FakeCsvNoteStore
import com.caregiver.mobile.data.csv.ImportSummary
import com.caregiver.mobile.data.csv.csvRow
import com.caregiver.mobile.data.csv.csvText
import com.caregiver.mobile.data.testNote
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings ViewModel: exact notices, preview-gated imports, unmatched
 * recipient resolution, and re-entry blocking while an operation runs.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CsvTransferViewModelTest {

    private val uri: Uri = Uri.parse("content://test/notes.csv")

    private class FakeDocumentIo(var text: String = "") : CsvDocumentIo {
        val reads = mutableListOf<Uri>()
        val writes = mutableListOf<Pair<Uri, String>>()
        var readError: Exception? = null
        var writeError: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(uri: Uri): String {
            reads += uri
            gate?.await()
            readError?.let { throw it }
            return text
        }

        override suspend fun write(uri: Uri, content: String) {
            writeError?.let { throw it }
            writes += uri to content
        }
    }

    private fun store(
        notes: List<NoteDto> = emptyList(),
        recipients: List<RecipientDto> = listOf(RecipientDto("r1", "Mona", true)),
    ) = FakeCsvNoteStore(notes = notes, recipients = recipients)

    @Test
    fun exportWritesTheContentAndReportsTheRealCount() = runTest {
        val fake = store(notes = listOf(testNote(id = "n1"), testNote(id = "n2")))
        val io = FakeDocumentIo()
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)

        vm.exportTo(uri)
        advanceUntilIdle()

        assertEquals(CsvNotice.ExportDone(2), vm.state.value.notice)
        assertNull(vm.state.value.operation)
        assertTrue(io.writes.single().second.startsWith(CsvCodec.BOM))
    }

    @Test
    fun exportWithoutNotesReportsZeroAndWritesTheHeader() = runTest {
        val io = FakeDocumentIo()
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.exportTo(uri)
        advanceUntilIdle()

        assertEquals(CsvNotice.ExportDone(0), vm.state.value.notice)
        assertTrue(io.writes.single().second.contains("note_text"))
    }

    @Test
    fun exportWriteFailureReportsAnActionableNotice() = runTest {
        val io = FakeDocumentIo()
        io.writeError = IOException("no space")
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.exportTo(uri)
        advanceUntilIdle()

        assertEquals(
            CsvNotice.Failed(CsvOperation.EXPORT, CsvFailure(CsvFailureReason.WRITE_FAILED)),
            vm.state.value.notice,
        )
    }

    @Test
    fun duplicateExportTapsAreIgnoredWhileBusy() = runTest {
        val io = FakeDocumentIo()
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.exportTo(uri)
        vm.exportTo(uri)
        advanceUntilIdle()

        assertEquals(1, io.writes.size)
    }

    @Test
    fun importSuccessOpensThePreviewWithoutWriting() = runTest {
        val fake = store()
        val io = FakeDocumentIo(text = csvText(csvRow()))
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)

        vm.importFrom(uri)
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertNotNull(preview)
        assertEquals(1, preview!!.valid.size)
        assertNull(vm.state.value.notice)
        assertEquals(0, fake.inserted.size)
    }

    @Test
    fun importFailureReportsTheReason() = runTest {
        val io = FakeDocumentIo(text = "")
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.importFrom(uri)
        advanceUntilIdle()

        assertEquals(
            CsvNotice.Failed(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.EMPTY_FILE)),
            vm.state.value.notice,
        )
        assertNull(vm.state.value.preview)
    }

    @Test
    fun oversizedFileReportsTooLargeWithoutParsing() = runTest {
        val io = FakeDocumentIo(text = csvText(csvRow()))
        io.readError = CsvImportTooLargeException()
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.importFrom(uri)
        advanceUntilIdle()

        assertEquals(
            CsvNotice.Failed(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.TOO_LARGE)),
            vm.state.value.notice,
        )
    }

    @Test
    fun duplicateImportTapsAreIgnoredWhileReading() = runTest {
        val io = FakeDocumentIo(text = csvText(csvRow()))
        io.gate = CompletableDeferred()
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)

        vm.importFrom(uri)
        vm.importFrom(uri)
        advanceUntilIdle()

        assertEquals(1, io.reads.size)
        assertEquals(CsvOperation.IMPORT, vm.state.value.operation)

        io.gate!!.complete(Unit)
        advanceUntilIdle()

        assertNotNull(vm.state.value.preview)
        assertNull(vm.state.value.operation)
    }

    @Test
    fun resolvingAnUnmatchedRecipientReplansAndCommitsTheMapping() = runTest {
        val fake = store()
        val io = FakeDocumentIo(text = csvText(csvRow(recipientId = "gone", text = "first")))
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)
        vm.importFrom(uri)
        advanceUntilIdle()

        val unresolved = vm.state.value.preview!!
        assertEquals(0, unresolved.valid.size)
        assertEquals(1, unresolved.unmatched.size)

        vm.resolveUnmatched(CsvImportPlanner.idKey("gone"), "r1")

        val resolved = vm.state.value.preview!!
        assertEquals(1, resolved.valid.size)
        assertEquals("first", resolved.valid.single().text)

        vm.commitImport()
        advanceUntilIdle()

        assertEquals(
            CsvNotice.ImportDone(
                ImportSummary(imported = 1, skipped = 0, duplicates = 0, rejected = 0, total = 1),
            ),
            vm.state.value.notice,
        )
        assertEquals(1, fake.inserted.size)
        assertEquals("r1", fake.inserted.single().recipientId)
        assertNull(vm.state.value.preview)
    }

    @Test
    fun skippingEveryUnmatchedRecipientCommitsWithoutWriting() = runTest {
        val fake = store()
        val io = FakeDocumentIo(text = csvText(csvRow(recipientId = "gone")))
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)
        vm.importFrom(uri)
        advanceUntilIdle()

        vm.commitImport()
        advanceUntilIdle()

        assertEquals(
            CsvNotice.ImportDone(
                ImportSummary(imported = 0, skipped = 1, duplicates = 0, rejected = 0, total = 1),
            ),
            vm.state.value.notice,
        )
        assertEquals(0, fake.inserted.size)
    }

    @Test
    fun cancellingThePreviewDropsThePlanWithoutWriting() = runTest {
        val fake = store()
        val io = FakeDocumentIo(text = csvText(csvRow()))
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)
        vm.importFrom(uri)
        advanceUntilIdle()

        vm.cancelImportPreview()

        assertNull(vm.state.value.preview)
        assertEquals(0, fake.inserted.size)
    }

    @Test
    fun aStoreFailureDuringCommitSurfacesInTheSummary() = runTest {
        val fake = store()
        fake.throwOnInsert = true
        val io = FakeDocumentIo(text = csvText(csvRow()))
        val vm = CsvTransferViewModel(CsvTransferService(fake), io, this)
        vm.importFrom(uri)
        advanceUntilIdle()

        vm.commitImport()
        advanceUntilIdle()

        val summary = (vm.state.value.notice as CsvNotice.ImportDone).summary
        assertEquals(0, summary.imported)
        assertEquals(1, summary.rejected)
        assertEquals(CsvFailureReason.INSERT_FAILED, summary.failure?.reason)
        assertNull(vm.state.value.preview)
    }

    @Test
    fun dismissingTheNoticeClearsIt() = runTest {
        val io = FakeDocumentIo()
        val vm = CsvTransferViewModel(CsvTransferService(store()), io, this)
        vm.exportTo(uri)
        advanceUntilIdle()

        vm.dismissNotice()

        assertNull(vm.state.value.notice)
    }
}
