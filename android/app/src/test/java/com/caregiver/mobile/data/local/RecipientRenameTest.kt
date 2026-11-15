package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task: `RecipientAdmin.rename` normalizes and validates the new name,
 * refuses archived and missing people, and leaves the person's notes
 * pointing at the same recipient id.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientRenameTest {
    private lateinit var db: AppDatabase
    private lateinit var apis: LocalBackendApis

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        apis = LocalBackendApis(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun renameNormalizesAndReturnsUpdatedRecipient() = runTest {
        seedRecipient("r1", "Old Name")

        val renamed = apis.rename("r1", " Mama\tAisha ")

        assertEquals("Mama Aisha", renamed.name)
        assertEquals("Mama Aisha", db.recipientDao().byServerId("r1")?.name)
    }

    @Test
    fun renameArchivedRecipientThrows() = runTest {
        seedRecipient("r1", "Old Name", archivedAt = 111L)

        try {
            apis.rename("r1", "New Name")
            error("expected ArchivedRecipientException")
        } catch (e: ArchivedRecipientException) {
            assertEquals("r1", e.recipientId)
        }
        assertEquals("Old Name", db.recipientDao().byServerId("r1")?.name)
    }

    @Test
    fun renameMissingRecipientThrows() = runTest {
        try {
            apis.rename("missing", "New Name")
            error("expected NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // expected
        }
    }

    @Test
    fun renameBlankNameThrows() = runTest {
        seedRecipient("r1", "Old Name")

        try {
            apis.rename("r1", "   ")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("recipient name", e.message)
        }
        assertEquals("Old Name", db.recipientDao().byServerId("r1")?.name)
    }

    @Test
    fun renameTooLongNameThrows() = runTest {
        seedRecipient("r1", "Old Name")

        try {
            apis.rename("r1", "a".repeat(121))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("recipient name", e.message)
        }
        assertEquals("Old Name", db.recipientDao().byServerId("r1")?.name)
    }

    @Test
    fun renameKeepsNotesOnTheSameRecipient() = runTest {
        seedRecipient("r1", "Old Name")
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "n1",
                    recipientId = "r1",
                    date = "2026-10-24",
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 1,
                    fall = false,
                    text = "note",
                ),
            ),
        )

        apis.rename("r1", "Mama Aisha")

        val note = db.noteDao().byServerId("n1")
        assertEquals("r1", note?.recipientId)
    }

    private suspend fun seedRecipient(
        id: String,
        name: String,
        archivedAt: Long? = null,
    ) {
        db.recipientDao().insertAll(
            listOf(
                RecipientEntity(
                    serverId = id,
                    name = name,
                    active = true,
                    archivedAt = archivedAt,
                ),
            ),
        )
    }
}
