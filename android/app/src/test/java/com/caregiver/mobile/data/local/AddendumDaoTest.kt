package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 4: addendum counts and note-id deletes stay scoped to one recipient. */
@RunWith(RobolectricTestRunner::class)
class AddendumDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AddendumDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.addendumDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(serverId: String, recipientId: String) = NoteEntity(
        serverId = serverId,
        recipientId = recipientId,
        date = "2026-10-24",
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 1,
        fall = false,
        text = "note $serverId",
    )

    private fun addendum(id: String, noteId: String) = AddendumEntity(
        id = id,
        noteId = noteId,
        createdAt = "2026-10-24T10:00:00",
        text = "addendum $id",
    )

    @Test
    fun countsAndDeletesOnlyTheSelectedNoteIds() = runTest {
        db.noteDao().insertAll(listOf(note("n1", "r1"), note("n2", "r2")))
        dao.insert(addendum("a1", "n1"))
        dao.insert(addendum("a2", "n1"))
        dao.insert(addendum("a3", "n2"))

        assertEquals(2, dao.countForRecipient("r1"))
        assertEquals(1, dao.countForRecipient("r2"))
        assertEquals(0, dao.countForRecipient("missing"))

        dao.deleteByNoteIds(emptyList())
        assertEquals(3, dao.all().size)

        dao.deleteByNoteIds(listOf("n1"))
        assertTrue(dao.byNoteId("n1").isEmpty())
        assertEquals(listOf("a3"), dao.byNoteId("n2").map { it.id })

        dao.deleteByNoteIds(listOf("n1"))
        assertEquals(1, dao.all().size)
    }
}
