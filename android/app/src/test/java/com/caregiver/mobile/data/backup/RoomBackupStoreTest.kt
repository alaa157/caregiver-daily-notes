package com.caregiver.mobile.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AddendumEntity
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.OutboxEntity
import com.caregiver.mobile.data.local.OutboxStatus
import com.caregiver.mobile.data.local.PlanEntity
import com.caregiver.mobile.data.local.RecipientEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Room restore seam: one consistent snapshot, all-or-nothing replace,
 * order preservation, conflict detection, and the guarantee that the outbox
 * is never part of a restore.
 */
@RunWith(RobolectricTestRunner::class)
class RoomBackupStoreTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recipientRow(
        serverId: String,
        name: String = "Mona",
        active: Boolean = true,
        archivedAt: Long? = null,
    ) = RecipientEntity(
        serverId = serverId,
        name = name,
        active = active,
        archivedAt = archivedAt,
    )

    private fun noteRow(
        serverId: String,
        recipientId: String = "r1",
        date: String = "2026-10-01",
        text: String = "note",
    ) = NoteEntity(
        serverId = serverId,
        recipientId = recipientId,
        date = date,
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = text,
    )

    private fun addendumRow(id: String, noteId: String = "n1") = AddendumEntity(
        id = id,
        noteId = noteId,
        createdAt = "2026-10-02T10:00:00",
        text = "addendum",
    )

    private fun planRow(id: String, recipientId: String = "r1") = PlanEntity(
        id = id,
        recipientId = recipientId,
        versionsJson = "[]",
        updatedAt = 1,
    )

    @Test
    fun snapshotReadsEveryRestorableTableInOrder() = runTest {
        db.recipientDao().insertAll(listOf(recipientRow("r1"), recipientRow("r2", name = "Zaid")))
        db.noteDao().insertAll(
            listOf(
                noteRow("n-old", date = "2026-09-30", text = "older"),
                noteRow("n-same-1", date = "2026-10-01", text = "same day first"),
                noteRow("n-same-2", date = "2026-10-01", text = "same day second"),
            ),
        )
        db.addendumDao().insert(addendumRow("a1"))
        db.planDao().upsert(planRow("p1"))

        val snapshot = RoomBackupStore(db).snapshot()

        assertEquals(listOf("r1", "r2"), snapshot.recipients.map { it.serverId })
        assertEquals(
            listOf("n-same-1", "n-same-2", "n-old"),
            snapshot.notes.map { it.serverId },
        )
        assertEquals(listOf("a1"), snapshot.addenda.map { it.id })
        assertEquals(listOf("p1"), snapshot.plans.map { it.id })
        assertEquals(BackupCounts(recipients = 2, notes = 3, addenda = 1, plans = 1), snapshot.counts)
    }

    @Test
    fun snapshotAndRestorePreserveArchiveState() = runTest {
        db.recipientDao().insertAll(
            listOf(
                recipientRow("r1", archivedAt = 111L),
                recipientRow("r2"),
            ),
        )
        db.noteDao().insertAll(listOf(noteRow("n1", recipientId = "r1")))
        val store = RoomBackupStore(db)

        val snapshot = store.snapshot()
        assertEquals(listOf(111L, null), snapshot.recipients.map { it.archivedAt })

        assertEquals(BackupReplaceResult.Replaced, store.replaceAll(snapshot, store.counts()))
        assertEquals(111L, db.recipientDao().byServerId("r1")?.archivedAt)
        assertNull(db.recipientDao().byServerId("r2")?.archivedAt)
    }

    @Test
    fun restoringAPreDeletionSnapshotRecreatesADeletedRecipient() = runTest {
        db.recipientDao().insertAll(listOf(recipientRow("r1", archivedAt = 111L)))
        db.noteDao().insertAll(listOf(noteRow("n1", recipientId = "r1")))
        val store = RoomBackupStore(db)
        val snapshot = store.snapshot()

        db.deleteArchivedRecipient("r1")
        assertNull(db.recipientDao().byServerId("r1"))

        assertEquals(BackupReplaceResult.Replaced, store.replaceAll(snapshot, store.counts()))
        assertEquals(111L, db.recipientDao().byServerId("r1")?.archivedAt)
        assertNotNull(db.noteDao().byServerId("n1"))
    }

    @Test
    fun replaceAllSwapsEverythingAndPreservesOrder() = runTest {
        db.recipientDao().insertAll(listOf(recipientRow("old")))
        db.noteDao().insertAll(listOf(noteRow("old-note", recipientId = "old", text = "old text")))
        val store = RoomBackupStore(db)

        val backup = BackupData(
            recipients = listOf(backupRecipient("r9", name = "First"), backupRecipient("r8", name = "Second")),
            notes = listOf(
                backupNote("n-same-1", recipientId = "r9", date = "2026-10-01", text = "same day first"),
                backupNote("n-same-2", recipientId = "r9", date = "2026-10-01", text = "same day second"),
                backupNote("n-older", recipientId = "r8", date = "2026-09-01", text = "older"),
            ),
            addenda = listOf(backupAddendum("a9", noteId = "n-same-1")),
            plans = listOf(backupPlan("p9", recipientId = "r8")),
        )

        val result = store.replaceAll(backup, store.counts())

        assertEquals(BackupReplaceResult.Replaced, result)
        // Recipient list order (rowId) is what the UI shows.
        assertEquals(listOf("r9", "r8"), db.recipientDao().all().map { it.serverId })
        // Same-day relative order is what the history list shows.
        assertEquals(
            listOf("n-same-1", "n-same-2", "n-older"),
            db.noteDao().query(null, null, null).map { it.serverId },
        )
        assertEquals("same day first", db.noteDao().byServerId("n-same-1")?.text)
        assertEquals(listOf("a9"), db.addendumDao().all().map { it.id })
        assertEquals(listOf("p9"), db.planDao().all().map { it.id })
    }

    @Test
    fun replaceAllNeverTouchesTheOutbox() = runTest {
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
                text = "unsynced note",
                date = "2026-10-01",
                createdAt = 42,
                status = OutboxStatus.PENDING,
            ),
        )
        val store = RoomBackupStore(db)

        val result = store.replaceAll(
            backupData(recipients = emptyList(), notes = emptyList(), addenda = emptyList(), plans = emptyList()),
            store.counts(),
        )

        assertEquals(BackupReplaceResult.Replaced, result)
        assertEquals("unsynced note", db.outboxDao().byLocalId("pending-1")?.text)
        assertEquals(OutboxStatus.PENDING, db.outboxDao().byLocalId("pending-1")?.status)
    }

    @Test
    fun countsChangedAfterThePreviewReturnsConflictAndWritesNothing() = runTest {
        db.recipientDao().insertAll(listOf(recipientRow("r1")))
        val store = RoomBackupStore(db)
        val expected = store.counts()

        // Data change (any writer) between preview and confirmation.
        db.noteDao().insertAll(listOf(noteRow("created-later")))

        val result = store.replaceAll(
            backupData(recipients = listOf(backupRecipient("r9")), notes = emptyList(), addenda = emptyList(), plans = emptyList()),
            expected,
        )

        assertEquals(BackupReplaceResult.Conflict, result)
        assertEquals(listOf("r1"), db.recipientDao().all().map { it.serverId })
        assertNotNull(db.noteDao().byServerId("created-later"))
    }

    @Test
    fun aFailedInsertRollsBackEverything() = runTest {
        db.recipientDao().insertAll(listOf(recipientRow("r1")))
        db.noteDao().insertAll(listOf(noteRow("n1", text = "original")))
        val store = RoomBackupStore(db)

        // Duplicate ids would normally be rejected by validation; force one to
        // prove the store itself keeps the transaction all-or-nothing.
        val duplicated = BackupData(
            recipients = listOf(backupRecipient("r2"), backupRecipient("r2")),
            notes = listOf(backupNote("n2", recipientId = "r2")),
            addenda = emptyList(),
            plans = emptyList(),
        )

        val failure = runCatching { store.replaceAll(duplicated, store.counts()) }.exceptionOrNull()

        assertNotNull("the unique-key abort must fail the restore", failure)
        assertEquals(listOf("r1"), db.recipientDao().all().map { it.serverId })
        assertEquals("original", db.noteDao().byServerId("n1")?.text)
        assertTrue(db.addendumDao().all().isEmpty())
    }
}
