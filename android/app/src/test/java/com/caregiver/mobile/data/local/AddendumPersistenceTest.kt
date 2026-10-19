package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddendumPersistenceTest {
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

    private fun noteRequest() = CreateNoteRequest(
        recipientId = "r1",
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 1,
        fall = false,
        text = "original",
    )

    @Test
    fun appendPersistsAcrossRepositoryRecreation() = runTest {
        val first = LocalBackendApis(db)
        val note = first.notes().create(noteRequest())
        val appended = first.notes().append(note.id, CreateAddendumRequest("correction"))

        val second = LocalBackendApis(db)
        val detail = second.notes().detail(note.id)

        assertEquals(1, detail.addenda.size)
        assertEquals(appended.id, detail.addenda.first().id)
        assertEquals("correction", detail.addenda.first().text)
        assertEquals("original", detail.text)
    }

    @Test
    fun detailReadsAddendaInDeterministicOrder() = runTest {
        val apis = LocalBackendApis(db)
        val note = apis.notes().create(noteRequest())
        apis.notes().append(note.id, CreateAddendumRequest("first"))
        apis.notes().append(note.id, CreateAddendumRequest("second"))

        val detail = apis.notes().detail(note.id)
        assertEquals(listOf("first", "second"), detail.addenda.map { it.text })
    }
}
