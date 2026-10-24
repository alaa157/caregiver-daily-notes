package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 1: deleting an archived recipient is one transaction that removes
 * exactly that recipient's seven owned table rows; an unarchived recipient
 * is refused without mutating anything.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientDeletionTest {
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

    @Test
    fun deletingActiveRecipientThrowsAndLeavesEverything() = runTest {
        seed("active", archivedAt = null)
        val before = db.recipientDataCounts("active")

        try {
            db.deleteArchivedRecipient("active")
            fail("Expected ActiveRecipientDeleteException")
        } catch (expected: ActiveRecipientDeleteException) {
            assertEquals("active", expected.recipientId)
        }

        assertEquals(RecipientDeletionCounts(1, 1, 1, 1, 1, 1), before)
        assertEquals(before, db.recipientDataCounts("active"))
        assertNotNull(db.recipientDao().byServerId("active"))
        assertEquals(listOf("n-active"), db.noteDao().serverIdsByRecipient("active"))
        assertEquals(1, db.addendumDao().byNoteId("n-active").size)
        assertNotNull(db.planDao().byId("p-active"))
        assertEquals(listOf("m-active"), db.medicationDao().idsByRecipient("active"))
        assertEquals(1, db.medicationDao().countIntakesForRecipient("active"))
        assertEquals(1, db.outboxDao().query("active", null, null).size)
    }

    @Test
    fun deletingArchivedRecipientRemovesOnlyItsRows() = runTest {
        seed("archived", archivedAt = 1_700_000_000_000)
        seed("active", archivedAt = null)

        assertEquals(
            RecipientDeletionCounts(1, 1, 1, 1, 1, 1),
            db.recipientDataCounts("archived"),
        )

        db.deleteArchivedRecipient("archived")

        assertNull(db.recipientDao().byServerId("archived"))
        assertEquals(
            RecipientDeletionCounts(0, 0, 0, 0, 0, 0),
            db.recipientDataCounts("archived"),
        )
        assertTrue(db.noteDao().serverIdsByRecipient("archived").isEmpty())
        assertTrue(db.addendumDao().byNoteId("n-archived").isEmpty())
        assertNull(db.planDao().byId("p-archived"))
        assertTrue(db.medicationDao().idsByRecipient("archived").isEmpty())
        assertTrue(db.outboxDao().query("archived", null, null).isEmpty())

        // The unrelated recipient and every one of its rows remain.
        assertEquals(
            RecipientDeletionCounts(1, 1, 1, 1, 1, 1),
            db.recipientDataCounts("active"),
        )
        assertNotNull(db.recipientDao().byServerId("active"))
        assertEquals(listOf("n-active"), db.noteDao().serverIdsByRecipient("active"))
        assertEquals(1, db.addendumDao().byNoteId("n-active").size)
        assertNotNull(db.planDao().byId("p-active"))
        assertEquals(listOf("m-active"), db.medicationDao().idsByRecipient("active"))
        assertEquals(1, db.medicationDao().countIntakesForRecipient("active"))
        assertEquals(1, db.outboxDao().query("active", null, null).size)
    }

    @Test
    fun deletingAgainOrDeletingMissingRecipientIsANoOp() = runTest {
        seed("archived", archivedAt = 1L)
        db.deleteArchivedRecipient("archived")

        db.deleteArchivedRecipient("archived")
        db.deleteArchivedRecipient("never-existed")

        assertNull(db.recipientDao().byServerId("archived"))
        assertEquals(
            RecipientDeletionCounts(0, 0, 0, 0, 0, 0),
            db.recipientDataCounts("archived"),
        )
    }

    private suspend fun seed(id: String, archivedAt: Long?) {
        db.recipientDao().insertAll(
            listOf(
                RecipientEntity(
                    serverId = id,
                    name = "Name $id",
                    active = true,
                    archivedAt = archivedAt,
                ),
            ),
        )
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "n-$id",
                    recipientId = id,
                    date = "2026-10-24",
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 1,
                    fall = false,
                    text = "note $id",
                ),
            ),
        )
        db.addendumDao().insert(
            AddendumEntity(
                id = "a-$id",
                noteId = "n-$id",
                createdAt = "2026-10-24T10:00:00",
                text = "addendum",
            ),
        )
        db.planDao().upsert(
            PlanEntity(
                id = "p-$id",
                recipientId = id,
                versionsJson = "[]",
                updatedAt = 1L,
            ),
        )
        db.medicationDao().upsert(
            MedicationEntity(
                id = "m-$id",
                recipientId = id,
                name = "Med",
                dose = null,
                schedule = null,
                active = true,
                createdAt = 1L,
            ),
        )
        db.medicationDao().upsertIntake(
            MedicationIntakeEntity(
                id = "m-$id:2026-10-24:0",
                medicationId = "m-$id",
                date = "2026-10-24",
                times = 1,
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                localId = "o-$id",
                recipientId = id,
                mood = "good",
                appetite = "good",
                sleep = "good",
                mobility = "walks",
                medicationTaken = "taken",
                pain = 1,
                fall = false,
                text = "outbox $id",
                date = "2026-10-24",
                createdAt = 1L,
            ),
        )
    }
}
