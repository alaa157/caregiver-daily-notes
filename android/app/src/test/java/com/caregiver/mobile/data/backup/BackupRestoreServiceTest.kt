package com.caregiver.mobile.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.OutboxEntity
import com.caregiver.mobile.data.local.OutboxStatus
import com.caregiver.mobile.data.local.RecipientEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end service over a real in-memory database: create, preview,
 * commit, conflict detection, rejection paths, and the sync safeguards
 * (outbox untouched, no secrets in the file, unavailable without a store).
 */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var service: BackupRestoreService

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = BackupRestoreService(
            store = RoomBackupStore(db),
            appVersion = "test (1)",
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        db.recipientDao().insertAll(
            listOf(RecipientEntity(serverId = "r1", name = "منى", active = true)),
        )
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "n1",
                    recipientId = "r1",
                    date = "2026-10-01",
                    mood = "good",
                    appetite = "good",
                    sleep = "ok",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 2,
                    fall = false,
                    text = "ملاحظة الرعاية",
                ),
            ),
        )
    }

    private suspend fun prepareReady(text: String): RestorePreview {
        val preparation = service.prepare(text)
        assertTrue("expected a ready preview, got $preparation", preparation is RestorePreparation.Ready)
        return (preparation as RestorePreparation.Ready).preview
    }

    @Test
    fun createCapturesEveryRecordAndFilenameInUtc() = runTest {
        seed()

        val packageResult = service.create()

        assertEquals(BackupCounts(recipients = 1, notes = 1, addenda = 0, plans = 0), packageResult.counts)
        assertEquals("caregiver-backup-2026-10-19.carebackup.json", service.fileName())
        val decoded = CareBackupCodec.decode(packageResult.content)
        val backup = (decoded as BackupDecodeResult.Success).backup
        assertEquals("2026-10-19T09:00:00Z", backup.createdAt)
        assertEquals("test (1)", backup.appVersion)
        assertEquals("منى", backup.data.recipients.single().name)
        assertEquals("ملاحظة الرعاية", backup.data.notes.single().text)
    }

    @Test
    fun aBackupNeverIncludesOutboxRowsOrSecrets() = runTest {
        seed()
        db.outboxDao().insert(
            OutboxEntity(
                localId = "pending-1",
                recipientId = "r1",
                mood = "good",
                appetite = "good",
                sleep = "ok",
                mobility = "walks",
                medicationTaken = "taken",
                pain = 3,
                fall = false,
                text = "OUTBOX-SECRET-TEXT",
                date = "2026-10-01",
                createdAt = 42,
                status = OutboxStatus.PENDING,
            ),
        )

        val content = service.create().content

        assertFalse(content.contains("OUTBOX-SECRET-TEXT"))
        assertFalse(content.contains("outbox"))
        assertFalse(content.contains("localId"))
    }

    @Test
    fun prepareShowsCountsAndChangesNothing() = runTest {
        seed()
        val backupText = service.create().content

        val preview = prepareReady(backupText)

        assertEquals(BackupCounts(1, 1, 0, 0), preview.backup)
        assertEquals(BackupCounts(1, 1, 0, 0), preview.current)
        assertEquals("2026-10-19T09:00:00Z", preview.createdAt)
        assertEquals("test (1)", preview.appVersion)
        assertEquals(CareBackupCodec.FORMAT_VERSION, preview.formatVersion)
        // Nothing was written: the database still holds exactly the seeded rows.
        assertEquals(listOf("r1"), db.recipientDao().all().map { it.serverId })
        assertEquals(listOf("n1"), db.noteDao().query(null, null, null).map { it.serverId })
    }

    @Test
    fun committingReplacesLocalDataWithTheBackup() = runTest {
        seed()
        val backupText = service.create().content
        // Drift away from the backup, then restore it.
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "n2",
                    recipientId = "r1",
                    date = "2026-10-05",
                    mood = "low",
                    appetite = "poor",
                    sleep = "ok",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 5,
                    fall = true,
                    text = "created after the backup",
                ),
            ),
        )
        db.recipientDao().insertAll(listOf(RecipientEntity(serverId = "r2", name = "Extra", active = true)))

        val preview = prepareReady(backupText)
        val outcome = service.commit(preview)

        assertEquals(RestoreOutcome.Restored(BackupCounts(1, 1, 0, 0)), outcome)
        assertEquals(listOf("r1"), db.recipientDao().all().map { it.serverId })
        assertEquals(listOf("n1"), db.noteDao().query(null, null, null).map { it.serverId })
        assertEquals("ملاحظة الرعاية", db.noteDao().byServerId("n1")?.text)
    }

    @Test
    fun commitRefusesWhenDataChangedAfterThePreview() = runTest {
        seed()
        val backupText = service.create().content
        val preview = prepareReady(backupText)

        // A concurrent write between preview and confirmation.
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "created-later",
                    recipientId = "r1",
                    date = "2026-10-06",
                    mood = "good",
                    appetite = "good",
                    sleep = "ok",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 1,
                    fall = false,
                    text = "must survive",
                ),
            ),
        )

        val outcome = service.commit(preview)

        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(
            BackupFailureReason.RESTORE_CONFLICT,
            (outcome as RestoreOutcome.Failed).failure.reason,
        )
        assertNotNull("the newer note is untouched", db.noteDao().byServerId("created-later"))
        assertNotNull(db.noteDao().byServerId("n1"))
    }

    @Test
    fun aRestoreFailureIsReportedAndRollsBack() = runTest {
        seed()
        val backupText = service.create().content
        val preview = prepareReady(backupText)
        // Force the store write to fail: the outcome must be a clean failure.
        val broken = BackupRestoreService(
            store = object : BackupStore by RoomBackupStore(db) {
                override suspend fun replaceAll(data: BackupData, expected: BackupCounts): BackupReplaceResult =
                    throw IllegalStateException("disk full")
            },
            appVersion = "test (1)",
            clock = clock,
        )

        val outcome = broken.commit(preview)

        assertEquals(
            BackupFailureReason.RESTORE_FAILED,
            (outcome as RestoreOutcome.Failed).failure.reason,
        )
        assertNotNull(db.noteDao().byServerId("n1"))
    }

    @Test
    fun aTamperedFileFailsBeforeAnyPreview() = runTest {
        seed()
        val tampered = service.create().content.replace("\"ملاحظة الرعاية\"", "\"edited\"")

        val preparation = service.prepare(tampered)

        assertEquals(
            BackupFailureReason.CHECKSUM_MISMATCH,
            (preparation as RestorePreparation.Failed).failure.reason,
        )
    }

    @Test
    fun invalidRecordsFailValidationWithACount() = runTest {
        val broken = backupData(notes = listOf(backupNote(recipientId = "missing")))
        val preparation = service.prepare(encodeBackup(broken))

        val failure = (preparation as RestorePreparation.Failed).failure
        assertEquals(BackupFailureReason.INVALID_DATA, failure.reason)
        assertEquals(1, failure.issueCount)
    }

    @Test
    fun aStoreReadFailureDuringPrepareIsReportedAsSourceFailure() = runTest {
        val broken = BackupRestoreService(
            store = FakeBackupStore(throwOnCounts = true),
            appVersion = "test (1)",
            clock = clock,
        )

        val preparation = broken.prepare(encodeBackup())

        assertEquals(
            BackupFailureReason.SOURCE_FAILED,
            (preparation as RestorePreparation.Failed).failure.reason,
        )
    }

    @Test
    fun withoutALocalStoreTheServiceIsUnavailable() = runTest {
        val unavailable = BackupRestoreService(store = null, appVersion = "test (1)")

        assertFalse(unavailable.available)
        val failure = runCatching { unavailable.prepare(encodeBackup()) }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun aRestoreAcceptsAnEmptyBackup() = runTest {
        seed()
        val empty = encodeBackup(backupData(emptyList(), emptyList(), emptyList(), emptyList()))

        val outcome = service.commit(prepareReady(empty))

        assertEquals(RestoreOutcome.Restored(BackupCounts(0, 0, 0, 0)), outcome)
        assertTrue(db.recipientDao().all().isEmpty())
        assertTrue(db.noteDao().query(null, null, null).isEmpty())
    }
}
