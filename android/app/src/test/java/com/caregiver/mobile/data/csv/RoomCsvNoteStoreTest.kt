package com.caregiver.mobile.data.csv

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.RecipientEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room-backed import store: CSV dates are preserved verbatim, duplicates are
 * skipped by the store as well, and a mid-import failure rolls everything back.
 */
@RunWith(RobolectricTestRunner::class)
class RoomCsvNoteStoreTest {

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

    private fun planned(
        date: String = "2026-10-01",
        recipientId: String = "r1",
        text: String = "note",
    ) = PlannedNote(
        line = 2,
        date = date,
        recipientId = recipientId,
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = text,
    )

    private fun row(
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

    @Test
    fun preservesTheCsvDateAndExposesSnapshots() = runTest {
        val store = RoomCsvNoteStore(db)
        db.recipientDao().insertAll(listOf(RecipientEntity(serverId = "r1", name = "Mona", active = true)))

        val results = store.insertAll(listOf(planned(date = "2026-09-30")))

        assertTrue(store.preservesDates)
        assertEquals(listOf(CsvInsertResult.Inserted), results)
        assertEquals("2026-09-30", db.noteDao().query("r1", "2026-09-30", "2026-09-30").single().date)
        val snapshot = store.snapshot()
        assertEquals("r1", snapshot.notes.single().recipientId)
        assertEquals("Mona", snapshot.recipients.single().name)
    }

    @Test
    fun aNoteForTheSameRecipientAndDayIsSkippedByTheStoreToo() = runTest {
        db.noteDao().insertAll(listOf(row("existing")))
        val store = RoomCsvNoteStore(db)

        val results = store.insertAll(listOf(planned(date = "2026-10-01", text = "imported")))

        assertEquals(listOf(CsvInsertResult.SkippedDuplicate), results)
        assertEquals(1, db.noteDao().query(null, null, null).size)
        assertEquals("note", db.noteDao().query(null, null, null).single().text)
    }

    @Test
    fun aFailedInsertRollsBackTheWholeImport() = runTest {
        // A generator that collides on the second row forces a primary-key
        // abort mid-transaction; nothing may remain.
        val store = RoomCsvNoteStore(db) { "same-id" }

        val failure = runCatching {
            store.insertAll(
                listOf(
                    planned(recipientId = "r1", date = "2026-10-01"),
                    planned(recipientId = "r2", date = "2026-10-02"),
                ),
            )
        }.exceptionOrNull()

        assertNotNull("the colliding id must fail the import", failure)
        assertEquals(0, db.noteDao().query(null, null, null).size)
    }

    @Test
    fun importNeverOverwritesAnExistingRowWithTheSameId() = runTest {
        db.noteDao().insertAll(listOf(row("fixed", recipientId = "r1", date = "2026-10-01", text = "original")))
        val store = RoomCsvNoteStore(db) { "fixed" }

        val failure = runCatching {
            store.insertAll(
                listOf(planned(recipientId = "r2", date = "2026-10-05", text = "imported")),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("original", db.noteDao().query("r1", "2026-10-01", "2026-10-01").single().text)
        assertEquals(1, db.noteDao().query(null, null, null).size)
    }
}
