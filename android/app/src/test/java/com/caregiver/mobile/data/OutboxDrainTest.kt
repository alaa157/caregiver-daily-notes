package com.caregiver.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.OutboxEntity
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 8c: the drain uploads outbox rows oldest first, deletes what the
 * server accepted (caching it under the client localId), keeps what it
 * could not reach for the backoff retry, and resolves the client id for
 * detail/append after a sync.
 */
@RunWith(RobolectricTestRunner::class)
class OutboxDrainTest {
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

    private fun newRepo(schedule: () -> Unit = {}) =
        NoteRepository(apis, db.noteDao(), db.outboxDao(), fixedClock, schedule)

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

    private fun outboxRow(
        localId: String,
        createdAt: Long,
        text: String = "note $localId",
        status: String = OutboxStatus.PENDING,
    ) = OutboxEntity(
        localId = localId,
        recipientId = "r1",
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = text,
        date = "2026-10-07",
        createdAt = createdAt,
        status = status,
    )

    @Test
    fun drainUploadsOldestFirstDeletesWhatTheServerTookAndCachesIt() = runTest {
        val uploaded = mutableListOf<String>()
        noteApi.createHandler = { body ->
            uploaded += body.text
            testNote(id = "srv-${body.text}", text = body.text)
        }
        db.outboxDao().insert(outboxRow("l2", createdAt = 2, text = "second"))
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "first"))
        val repo = newRepo()

        assertEquals(OutboxDrain.DONE, repo.drainOutbox())

        assertEquals(listOf("first", "second"), uploaded)
        assertEquals(0, db.outboxDao().query(null, null, null).size)
        // The server copy is cached under the client id, so timelines keep
        // the note and the old id keeps resolving.
        assertEquals("srv-first", db.noteDao().byLocalId("l1")?.serverId)
        assertEquals("srv-second", db.noteDao().byLocalId("l2")?.serverId)
        val cached = repo.observeAll().first()
        assertEquals(setOf("srv-first", "srv-second"), cached.map { it.id }.toSet())
        assertFalse(cached.any { it.pending })
        // The queued fields were replayed verbatim.
        assertEquals("r1", noteApi.lastCreate?.recipientId)
        assertEquals(2, noteApi.lastCreate?.pain)
    }

    @Test
    fun transportFailureKeepsTheRowAndAsksForABackoffRetry() = runTest {
        var calls = 0
        noteApi.createHandler = { calls++; throw IOException("still offline") }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1))
        db.outboxDao().insert(outboxRow("l2", createdAt = 2))

        assertEquals(OutboxDrain.RETRY, newRepo().drainOutbox())

        assertEquals(1, calls) // The pass stopped at the first failure.
        assertEquals(OutboxStatus.PENDING, db.outboxDao().byLocalId("l1")?.status)
        assertEquals(OutboxStatus.PENDING, db.outboxDao().byLocalId("l2")?.status)
        assertNull(db.noteDao().byLocalId("l1"))
    }

    @Test
    fun serverErrorAsksForABackoffRetry() = runTest {
        noteApi.createHandler = { throw FakeAuthApi.httpError(503, "UNAVAILABLE") }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1))

        assertEquals(OutboxDrain.RETRY, newRepo().drainOutbox())

        assertEquals(OutboxStatus.PENDING, db.outboxDao().byLocalId("l1")?.status)
    }

    @Test
    fun rejectedNoteIsMarkedFailedAndTheDrainContinues() = runTest {
        noteApi.createHandler = { body ->
            // The documented idempotency gap: a retried upload can hit the
            // one-note-per-day rule (docs/api/android-gaps.md).
            if (body.text == "bad") throw FakeAuthApi.httpError(422, "DUPLICATE_NOTE")
            testNote(id = "srv-good", text = body.text)
        }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "bad"))
        db.outboxDao().insert(outboxRow("l2", createdAt = 2, text = "good"))

        assertEquals(OutboxDrain.DONE, newRepo().drainOutbox())

        assertEquals(OutboxStatus.FAILED, db.outboxDao().byLocalId("l1")?.status)
        assertNull(db.outboxDao().byLocalId("l2"))
        assertEquals("srv-good", db.noteDao().byLocalId("l2")?.serverId)
    }

    @Test
    fun unauthorizedStopsTheDrainWithoutFailingTheRow() = runTest {
        noteApi.createHandler = { throw FakeAuthApi.httpError(401, "UNAUTHORIZED") }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1))

        assertEquals(OutboxDrain.STOPPED, newRepo().drainOutbox())

        // The note is fine; the session needs attention. WorkManager does
        // not burn retries, and the next trigger picks the row up again.
        assertEquals(OutboxStatus.PENDING, db.outboxDao().byLocalId("l1")?.status)
    }

    @Test
    fun failedAndCrashLeftSyncingRowsAreRetriedByTheNextDrain() = runTest {
        noteApi.createHandler = { body -> testNote(id = "srv-${body.text}", text = body.text) }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "failed", status = OutboxStatus.FAILED))
        db.outboxDao().insert(outboxRow("l2", createdAt = 2, text = "syncing", status = OutboxStatus.SYNCING))
        val repo = newRepo()

        assertEquals(OutboxDrain.DONE, repo.drainOutbox())

        assertEquals(0, db.outboxDao().query(null, null, null).size)
        assertEquals("srv-failed", db.noteDao().byLocalId("l1")?.serverId)
        assertEquals("srv-syncing", db.noteDao().byLocalId("l2")?.serverId)
    }

    @Test
    fun rowsSavedWhileTheDrainRunsArePickedUpByTheSamePass() = runTest {
        noteApi.createHandler = { body ->
            // Simulate an offline save landing mid-pass: it must not wait
            // for another trigger.
            if (db.outboxDao().byLocalId("late") == null) {
                db.outboxDao().insert(outboxRow("late", createdAt = 99, text = "late"))
            }
            testNote(id = "srv-${body.text}", text = body.text)
        }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "early"))
        val repo = newRepo()

        assertEquals(OutboxDrain.DONE, repo.drainOutbox())

        assertEquals(0, db.outboxDao().query(null, null, null).size)
        assertEquals("srv-late", db.noteDao().byLocalId("late")?.serverId)
    }

    @Test
    fun demoGraphsHaveNothingToDrainAndNeverUpload() = runTest {
        noteApi.createHandler = { error("demo data must never upload") }
        // Demo graphs inject no outbox at all.
        val demoRepo = NoteRepository(apis, dao = null)

        assertEquals(OutboxDrain.DONE, demoRepo.drainOutbox())
    }

    @Test
    fun detailResolvesASyncedLocalIdThroughTheCachedMapping() = runTest {
        noteApi.createHandler = { body -> testNote(id = "srv-first", text = body.text) }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "first"))
        val repo = newRepo()
        repo.drainOutbox()

        var requested: String? = null
        noteApi.detailHandler = { id ->
            requested = id
            testNoteDetail(testNote(id = id))
        }

        val detail = repo.detail("l1")

        assertEquals("srv-first", requested)
        assertEquals("srv-first", detail.id)
        // The detail re-cache preserves the mapping for the next screen.
        assertEquals("srv-first", db.noteDao().byLocalId("l1")?.serverId)
    }

    @Test
    fun appendResolvesASyncedLocalIdThroughTheCachedMapping() = runTest {
        noteApi.createHandler = { body -> testNote(id = "srv-first", text = body.text) }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "first"))
        val repo = newRepo()
        repo.drainOutbox()

        noteApi.appendHandler = { id, _ -> testAddendum(id = "a1", noteId = id) }

        repo.append("l1", CreateAddendumRequest("correction"))

        assertEquals("srv-first", noteApi.lastAppend?.first)
    }

    @Test
    fun refreshKeepsTheSyncedLocalIdMapping() = runTest {
        noteApi.createHandler = { body -> testNote(id = "srv-first", text = body.text) }
        db.outboxDao().insert(outboxRow("l1", createdAt = 1, text = "first"))
        val repo = newRepo()
        repo.drainOutbox()

        // A later online refresh returns the same server row: the mapping
        // must survive the replace.
        noteApi.historyHandler = { _, _, _ -> listOf(testNote(id = "srv-first")) }
        repo.history(null, null, null)

        assertEquals("srv-first", db.noteDao().byLocalId("l1")?.serverId)
    }

    @Test
    fun offlineCreateSchedulesADrainAndAnOnlineCreateDoesNot() = runTest {
        var scheduled = 0
        val repo = newRepo(schedule = { scheduled++ })

        noteApi.createHandler = { throw IOException("offline") }
        repo.create(createRequest())
        assertEquals(1, scheduled)

        noteApi.createHandler = { testNote("n9") }
        repo.create(createRequest())
        assertEquals(1, scheduled)
    }

    @Test
    fun observedOutboxStatusesReflectTheQueuedNotesAndAreEmptyInDemo() = runTest {
        db.outboxDao().insert(outboxRow("l1", createdAt = 1))
        db.outboxDao().insert(outboxRow("l2", createdAt = 2, status = OutboxStatus.FAILED))

        // Newest first (Task 8d top-bar status); demo graphs observe nothing.
        assertEquals(
            listOf(OutboxStatus.FAILED, OutboxStatus.PENDING),
            newRepo().observeOutboxStatuses().first(),
        )
        assertEquals(
            emptyList<String>(),
            NoteRepository(apis, dao = null).observeOutboxStatuses().first(),
        )
    }
}
