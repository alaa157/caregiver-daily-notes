package com.caregiver.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.OutboxStatus
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 8a: a successful refresh writes notes into Room, and a failing
 * network call still serves the last data from Room. Task 8b: an offline
 * create lands in the outbox and shows up as a pending note.
 */
@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {
    private lateinit var db: AppDatabase
    private val noteApi = FakeNoteApi()
    private val apis = FakeBackendApis(FakeAuthApi()).also { it.noteApi = noteApi }
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-10-07T09:30:00Z"), ZoneOffset.UTC)

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

    private fun createRequest() = CreateNoteRequest(
        recipientId = "r1",
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = "saved offline",
    )

    private suspend fun expectIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("expected the transport failure to propagate")
        } catch (e: IOException) {
            // Expected: nothing is cached, so the failure surfaces as before Task 8a.
        }
    }

    @Test
    fun networkRefreshWritesRoomAndNetworkFailureServesIt() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1", text = "Alpha note")) }
        val repo = NoteRepository(apis, db.noteDao())

        // Online: the live result is returned and cached.
        assertEquals(listOf("n1"), repo.history(null, null, null).map { it.id })
        assertEquals(listOf("n1"), repo.observeAll().first().map { it.id })

        // Offline: the same query is served from Room, not an error.
        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        val offline = repo.history(null, null, null)
        assertEquals(listOf("n1"), offline.map { it.id })
        assertEquals("Alpha note", offline.single().text)
    }

    @Test
    fun fallbackHonorsTheQueryScopeAndServesEmptyWithoutCache() = runTest {
        noteApi.historyHandler = { recipientId, _, _ ->
            if (recipientId == "r1") listOf(testNote("n1", recipientId = "r1")) else emptyList()
        }
        val repo = NoteRepository(apis, db.noteDao())
        repo.history("r1", null, null)

        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        assertEquals(listOf("n1"), repo.history("r1", null, null).map { it.id })
        // Another scope has no cached rows: its empty copy is served, not an
        // error (only a missing cache at all — demo mode — propagates).
        assertEquals(emptyList<String>(), repo.history("r2", null, null).map { it.id })
    }

    @Test
    fun refreshReplacesTheQueriedScope() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1"), testNote("n2")) }
        val repo = NoteRepository(apis, db.noteDao())
        repo.history(null, null, null)

        // The server no longer returns n1: the refresh replaces the scope.
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n2")) }
        repo.history(null, null, null)
        assertEquals(listOf("n2"), repo.observeAll().first().map { it.id })

        // And the offline path serves that replaced copy.
        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        assertEquals(listOf("n2"), repo.history(null, null, null).map { it.id })
    }

    @Test
    fun demoModeWithoutDaoIsPurePassthrough() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1")) }
        // Demo graphs inject no DAO: calls go straight to the backend, no disk.
        val repo = NoteRepository(apis, dao = null)

        assertEquals(listOf("n1"), repo.history(null, null, null).map { it.id })
        assertEquals(0, repo.observeAll().first().size)

        noteApi.historyHandler = { _, _, _ -> throw IOException("offline") }
        expectIOException { repo.history(null, null, null) }
    }

    @Test
    fun onlineCreateWritesThroughToTheCacheWithoutAnOutboxRow() = runTest {
        noteApi.createHandler = { testNote("n9", text = "synced") }
        val repo = NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock)

        val created = repo.create(createRequest())

        assertFalse(created.pending)
        assertEquals(listOf("n9"), repo.observeAll().first().map { it.id })
        assertEquals(0, db.outboxDao().query(null, null, null).size)
    }

    @Test
    fun offlineCreateEnqueuesTheOutboxAndReturnsAPendingNote() = runTest {
        noteApi.createHandler = { throw IOException("offline") }
        val repo = NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock)

        val created = repo.create(createRequest())

        assertTrue(created.pending)
        assertEquals("2026-10-07", created.date)
        assertEquals("r1", created.recipientId)
        assertEquals("saved offline", created.text)
        val row = db.outboxDao().byLocalId(created.id)
        assertNotNull(row)
        assertEquals(OutboxStatus.PENDING, row!!.status)
        assertEquals(created.id, row.localId)
        // Pending notes live in the outbox only: the server cache stays untouched.
        assertEquals(0, repo.observeAll().first().size)
    }

    @Test
    fun pendingNotesStayInHistoryAcrossRefreshAndOffline() = runTest {
        noteApi.createHandler = { throw IOException("offline") }
        val repo = NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock)
        val pending = repo.create(createRequest())

        // An online refresh of the same scope keeps the pending note, first
        // (its date is newer than the server row's).
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1", date = "2026-10-01")) }
        val online = repo.history("r1", null, null)
        assertEquals(listOf(pending.id, "n1"), online.map { it.id })
        assertTrue(online.first().pending)
        assertFalse(online.last().pending)

        // Offline: the cached row and the pending note both survive.
        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        assertEquals(listOf(pending.id, "n1"), repo.history("r1", null, null).map { it.id })

        // A scope with neither cache nor pending rows serves its empty copy
        // (Task 8a review fix); only a missing cache at all propagates.
        assertEquals(emptyList<String>(), repo.history("r2", null, null).map { it.id })
    }

    @Test
    fun offlineHistoryServesPendingNotesWithoutACachedCopy() = runTest {
        noteApi.createHandler = { throw IOException("offline") }
        val repo = NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock)
        val pending = repo.create(createRequest())

        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        val result = repo.history(null, "2026-10-01", "2026-10-31")

        assertEquals(listOf(pending.id), result.map { it.id })
        assertTrue(result.single().pending)
    }

    @Test
    fun detailOfAPendingNoteComesFromTheOutboxWithoutNetwork() = runTest {
        noteApi.createHandler = { throw IOException("offline") }
        val repo = NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock)
        val pending = repo.create(createRequest())

        val detail = repo.detail(pending.id)

        assertEquals("saved offline", detail.text)
        assertEquals("2026-10-07", detail.date)
        assertEquals(0, detail.addenda.size)
        assertEquals(0, noteApi.detailCalls)
    }

    @Test
    fun demoModeWithoutDaoRethrowsCreateFailures() = runTest {
        noteApi.createHandler = { throw IOException("offline") }
        // Demo graphs have no outbox: an offline create fails exactly as before 8b.
        val repo = NoteRepository(apis, dao = null)

        expectIOException { repo.create(createRequest()) }
        assertNull(db.outboxDao().byLocalId("unused"))
    }
}
