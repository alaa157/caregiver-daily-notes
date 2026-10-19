package com.caregiver.mobile.presentation.settings

import android.net.Uri
import com.caregiver.mobile.data.backup.BackupCounts
import com.caregiver.mobile.data.backup.BackupDecodeResult
import com.caregiver.mobile.data.backup.BackupDocumentIo
import com.caregiver.mobile.data.backup.BackupFailure
import com.caregiver.mobile.data.backup.BackupFailureReason
import com.caregiver.mobile.data.backup.BackupImportTooLargeException
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.CareBackupCodec
import com.caregiver.mobile.data.backup.FakeBackupStore
import com.caregiver.mobile.data.backup.encodeBackup
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings ViewModel: exact notices, preview-gated restores, re-entry
 * blocking, cancel-never-writes, and the unavailable path.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    private val uri: Uri = Uri.parse("content://test/backup.carebackup.json")

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC)

    private class FakeDocumentIo(var text: String = "") : BackupDocumentIo {
        val reads = mutableListOf<Uri>()
        val writes = mutableListOf<Pair<Uri, String>>()
        var readError: Exception? = null
        var writeError: Exception? = null
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun read(uri: Uri): String {
            reads += uri
            readGate?.await()
            readError?.let { throw it }
            return text
        }

        override suspend fun write(uri: Uri, content: String) {
            writeGate?.await()
            writeError?.let { throw it }
            writes += uri to content
        }
    }

    private fun service(store: FakeBackupStore = FakeBackupStore()): BackupRestoreService =
        BackupRestoreService(store = store, appVersion = "test (1)", clock = clock)

    @Test
    fun createWritesTheDocumentAndReportsTheRealCount() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo()
        val vm = BackupRestoreViewModel(service(store), io, this)

        vm.createTo(uri)
        advanceUntilIdle()

        assertEquals(BackupNotice.Created(BackupCounts(1, 1, 1, 1)), vm.state.value.notice)
        assertNull(vm.state.value.operation)
        val (writtenUri, content) = io.writes.single()
        assertEquals(uri, writtenUri)
        assertTrue(CareBackupCodec.decode(content) is BackupDecodeResult.Success)
    }

    @Test
    fun createDoubleTapsAreIgnoredWhileBusy() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo()
        io.writeGate = CompletableDeferred()
        val vm = BackupRestoreViewModel(service(store), io, this)

        vm.createTo(uri)
        vm.createTo(uri)
        advanceUntilIdle()

        assertEquals(BackupOperation.CREATE, vm.state.value.operation)

        io.writeGate!!.complete(Unit)
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertNull(vm.state.value.operation)
        assertEquals(1, io.writes.size)
    }

    @Test
    fun createWriteFailureReportsAnActionableNotice() = runTest {
        val io = FakeDocumentIo()
        io.writeError = IOException("no space")
        val vm = BackupRestoreViewModel(service(), io, this)

        vm.createTo(uri)
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.CREATE, BackupFailure(BackupFailureReason.WRITE_FAILED)),
            vm.state.value.notice,
        )
    }

    @Test
    fun createSourceFailureReportsTheSourceReason() = runTest {
        val broken = BackupRestoreService(
            store = object : com.caregiver.mobile.data.backup.BackupStore {
                override suspend fun snapshot() = throw IllegalStateException("db gone")
                override suspend fun counts() = BackupCounts(0, 0, 0, 0)
                override suspend fun replaceAll(
                    data: com.caregiver.mobile.data.backup.BackupData,
                    expected: BackupCounts,
                ) = com.caregiver.mobile.data.backup.BackupReplaceResult.Conflict
            },
            appVersion = "test (1)",
            clock = clock,
        )
        val io = FakeDocumentIo()
        val vm = BackupRestoreViewModel(broken, io, this)

        vm.createTo(uri)
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.CREATE, BackupFailure(BackupFailureReason.SOURCE_FAILED)),
            vm.state.value.notice,
        )
        assertTrue(io.writes.isEmpty())
    }

    @Test
    fun restoreOpensThePreviewWithoutWriting() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo(text = encodeBackup())
        val vm = BackupRestoreViewModel(service(store), io, this)

        vm.restoreFrom(uri)
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertNotNull(preview)
        assertEquals(BackupCounts(1, 1, 1, 1), preview!!.backup)
        assertNull(vm.state.value.notice)
        assertTrue(store.replaced.isEmpty())
    }

    @Test
    fun restoreOfATamperedFileReportsTheChecksumReason() = runTest {
        val io = FakeDocumentIo(text = encodeBackup().replace("\"note\"", "\"edited\""))
        val vm = BackupRestoreViewModel(service(), io, this)

        vm.restoreFrom(uri)
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.CHECKSUM_MISMATCH)),
            vm.state.value.notice,
        )
        assertNull(vm.state.value.preview)
    }

    @Test
    fun restoreOfANewerFormatReportsTheVersion() = runTest {
        val newer = encodeBackup().replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 7")
        val io = FakeDocumentIo(text = newer)
        val vm = BackupRestoreViewModel(service(), io, this)

        vm.restoreFrom(uri)
        advanceUntilIdle()

        val failure = (vm.state.value.notice as BackupNotice.Failed).failure
        assertEquals(BackupFailureReason.UNSUPPORTED_VERSION, failure.reason)
        assertEquals("7", failure.detail)
    }

    @Test
    fun oversizedFileReportsTooLargeWithoutParsing() = runTest {
        val io = FakeDocumentIo(text = encodeBackup())
        io.readError = BackupImportTooLargeException()
        val vm = BackupRestoreViewModel(service(), io, this)

        vm.restoreFrom(uri)
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.TOO_LARGE)),
            vm.state.value.notice,
        )
    }

    @Test
    fun restoreReadFailureReportsTheReadReason() = runTest {
        val io = FakeDocumentIo()
        io.readError = IOException("gone")
        val vm = BackupRestoreViewModel(service(), io, this)

        vm.restoreFrom(uri)
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.READ_FAILED)),
            vm.state.value.notice,
        )
    }

    @Test
    fun duplicateRestoreTapsAreIgnoredWhileReading() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo(text = encodeBackup())
        io.readGate = CompletableDeferred()
        val vm = BackupRestoreViewModel(service(store), io, this)

        vm.restoreFrom(uri)
        vm.restoreFrom(uri)
        advanceUntilIdle()

        assertEquals(1, io.reads.size)
        assertEquals(BackupOperation.RESTORE, vm.state.value.operation)

        io.readGate!!.complete(Unit)
        advanceUntilIdle()

        assertNotNull(vm.state.value.preview)
        assertNull(vm.state.value.operation)
        assertTrue(store.replaced.isEmpty())
    }

    @Test
    fun confirmingThePreviewRestoresAndReportsCounts() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo(text = encodeBackup())
        val vm = BackupRestoreViewModel(service(store), io, this)
        vm.restoreFrom(uri)
        advanceUntilIdle()

        vm.commitRestore()
        advanceUntilIdle()

        assertEquals(BackupNotice.Restored(BackupCounts(1, 1, 1, 1)), vm.state.value.notice)
        assertNull(vm.state.value.preview)
        assertEquals(1, store.replaced.size)
    }

    @Test
    fun cancellingThePreviewWritesNothing() = runTest {
        val store = FakeBackupStore()
        val io = FakeDocumentIo(text = encodeBackup())
        val vm = BackupRestoreViewModel(service(store), io, this)
        vm.restoreFrom(uri)
        advanceUntilIdle()

        vm.cancelRestorePreview()

        assertNull(vm.state.value.preview)
        assertTrue(store.replaced.isEmpty())
    }

    @Test
    fun aConflictDuringCommitReportsItAndClearsTheStalePreview() = runTest {
        val store = FakeBackupStore(conflict = true)
        val io = FakeDocumentIo(text = encodeBackup())
        val vm = BackupRestoreViewModel(service(store), io, this)
        vm.restoreFrom(uri)
        advanceUntilIdle()

        vm.commitRestore()
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.RESTORE_CONFLICT)),
            vm.state.value.notice,
        )
        assertNull(vm.state.value.preview)
        assertTrue(store.replaced.isEmpty())
    }

    @Test
    fun aStoreFailureDuringCommitReportsRestoreFailed() = runTest {
        val store = FakeBackupStore(throwOnReplace = true)
        val io = FakeDocumentIo(text = encodeBackup())
        val vm = BackupRestoreViewModel(service(store), io, this)
        vm.restoreFrom(uri)
        advanceUntilIdle()

        vm.commitRestore()
        advanceUntilIdle()

        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.RESTORE_FAILED)),
            vm.state.value.notice,
        )
        assertNull(vm.state.value.preview)
    }

    @Test
    fun withoutALocalStoreBothActionsFailCleanly() = runTest {
        val io = FakeDocumentIo()
        val vm = BackupRestoreViewModel(
            BackupRestoreService(store = null, appVersion = "test (1)", clock = clock),
            io,
            this,
        )

        assertFalse(vm.available)
        vm.createTo(uri)
        advanceUntilIdle()
        assertEquals(
            BackupNotice.Failed(BackupOperation.CREATE, BackupFailure(BackupFailureReason.UNAVAILABLE)),
            vm.state.value.notice,
        )

        vm.restoreFrom(uri)
        advanceUntilIdle()
        assertEquals(
            BackupNotice.Failed(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.UNAVAILABLE)),
            vm.state.value.notice,
        )
        assertTrue(io.writes.isEmpty())
        assertTrue(io.reads.isEmpty())
    }

    @Test
    fun dismissingTheNoticeClearsIt() = runTest {
        val io = FakeDocumentIo()
        val vm = BackupRestoreViewModel(service(), io, this)
        vm.createTo(uri)
        advanceUntilIdle()

        vm.dismissNotice()

        assertNull(vm.state.value.notice)
    }
}
