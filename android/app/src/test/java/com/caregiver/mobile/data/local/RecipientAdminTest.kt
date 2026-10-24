package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.data.demo.DemoBackendApis
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
 * Task 2: the local admin archives, unarchives, counts, and deletes only
 * through Room; every recipient-owned write in the local graph refuses an
 * archived recipient. The demo graph applies the same guards in memory.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientAdminTest {
    private lateinit var db: AppDatabase
    private lateinit var apis: LocalBackendApis
    private lateinit var medications: MedicationStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        apis = LocalBackendApis(db, clock = { FIXED_NOW })
        medications = MedicationStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun archiveSetsArchivedAtAndReturnsArchivedRecipient() = runTest {
        seedRecipient("r1")

        val archived = apis.archive("r1")

        assertEquals(FIXED_NOW, archived.archivedAt)
        assertTrue(archived.active)
        assertEquals(FIXED_NOW, db.recipientDao().byServerId("r1")?.archivedAt)
    }

    @Test
    fun archiveIsIdempotent() = runTest {
        seedRecipient("r1", archivedAt = 111L)

        val archived = apis.archive("r1")

        assertEquals(111L, archived.archivedAt)
        assertEquals(111L, db.recipientDao().byServerId("r1")?.archivedAt)
    }

    @Test
    fun unarchiveClearsArchivedAt() = runTest {
        seedRecipient("r1", archivedAt = 111L)

        val unarchived = apis.unarchive("r1")

        assertNull(unarchived.archivedAt)
        assertNull(db.recipientDao().byServerId("r1")?.archivedAt)
    }

    @Test
    fun unarchivePreservesActiveFlag() = runTest {
        seedRecipient("r1", archivedAt = 111L, active = false)

        val unarchived = apis.unarchive("r1")

        assertEquals(false, unarchived.active)
        assertEquals(false, db.recipientDao().byServerId("r1")?.active)
        assertNull(unarchived.archivedAt)
    }

    @Test
    fun archiveMissingRecipientThrows() = runTest {
        try {
            apis.archive("missing")
            fail("Expected NoSuchElementException")
        } catch (expected: NoSuchElementException) {
            assertTrue(expected.message.orEmpty().contains("missing"))
        }
    }

    @Test
    fun deleteArchivedRemovesRecipientAndOwnedRows() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)
        seedData("r1")

        apis.deleteArchived("r1")

        assertNull(db.recipientDao().byServerId("r1"))
        assertEquals(RecipientDeletionCounts(0, 0, 0, 0, 0, 0), apis.counts("r1"))
        assertTrue(db.noteDao().serverIdsByRecipient("r1").isEmpty())
        assertTrue(db.planDao().byId("p-r1") == null)
        assertTrue(db.medicationDao().idsByRecipient("r1").isEmpty())
        assertTrue(db.outboxDao().query("r1", null, null).isEmpty())
    }

    @Test
    fun deleteUnarchivedRecipientIsRejected() = runTest {
        seedRecipient("r1")
        seedData("r1")

        try {
            apis.deleteArchived("r1")
            fail("Expected ActiveRecipientDeleteException")
        } catch (expected: ActiveRecipientDeleteException) {
            assertEquals("r1", expected.recipientId)
        }

        assertNotNull(db.recipientDao().byServerId("r1"))
        assertEquals(RecipientDeletionCounts(1, 1, 1, 1, 1, 1), apis.counts("r1"))
    }

    @Test
    fun countsMatchStoredRows() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)
        seedData("r1")
        seedNote("n2-r1", "r1")
        seedPlan("p2-r1", "r1")

        assertEquals(
            RecipientDeletionCounts(notes = 2, addenda = 1, plans = 2, medications = 1, intakes = 1, outbox = 1),
            apis.counts("r1"),
        )
    }

    @Test
    fun createNoteForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)

        try {
            apis.notes().create(noteRequest("r1"))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertTrue(db.noteDao().query("r1", null, null).isEmpty())
    }

    @Test
    fun appendNoteForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)
        seedNote("n-r1", "r1")

        try {
            apis.notes().append("n-r1", CreateAddendumRequest("late note"))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertTrue(db.addendumDao().byNoteId("n-r1").isEmpty())
    }

    @Test
    fun createPlanForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)

        try {
            apis.plans().suggest(SuggestPlanRequest("r1", listOf("walk")))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertTrue(db.planDao().all().isEmpty())
    }

    @Test
    fun planTransitionForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)
        seedPlan("p-r1", "r1")

        try {
            apis.plans().accept("p-r1")
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            apis.plans().appendVersion("p-r1", AppendVersionRequest(items = listOf("more")))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertEquals(listOf("Suggested"), apis.plans().versions("p-r1").map { it.status })
    }

    @Test
    fun addMedicationForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)

        try {
            medications.add("r1", "Medication")
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertTrue(db.medicationDao().idsByRecipient("r1").isEmpty())
    }

    @Test
    fun medicationUpdateOrIntakeForArchivedRecipientIsRejected() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW)
        seedMedication("m-r1", "r1")

        try {
            medications.update(db.medicationDao().byId("m-r1")!!.copy(name = "Changed"))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            medications.setTaken("m-r1", LocalDate.of(2026, 10, 24), taken = true)
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            medications.delete("m-r1")
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertEquals("Med", db.medicationDao().byId("m-r1")?.name)
        assertTrue(
            db.medicationDao().observeIntakes("2026-10-24").first().isEmpty(),
        )
    }

    @Test
    fun recipientUpdateIsRejectedForArchivedRecipient() = runTest {
        seedRecipient("r1", archivedAt = FIXED_NOW, active = true)

        try {
            apis.recipients().update("r1", UpdateRecipientRequest(active = false))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }

        assertEquals(true, db.recipientDao().byServerId("r1")?.active)
    }

    @Test
    fun recipientUpdateKeepsTheListPosition() = runTest {
        seedRecipient("r1")
        seedRecipient("r2")

        val updated = apis.recipients().update("r1", UpdateRecipientRequest(active = false))

        assertEquals(false, updated.active)
        assertEquals(listOf("r1", "r2"), apis.recipients().list().map { it.id })
        assertEquals(false, db.recipientDao().byServerId("r1")?.active)
    }

    @Test
    fun demoArchiveAndDeleteWorkInMemory() = runTest {
        val demo = DemoBackendApis()

        val archived = demo.archive("r1")
        assertEquals(true, archived.archivedAt != null)
        assertEquals(
            RecipientDeletionCounts(notes = 2, addenda = 1, plans = 1, medications = 0, intakes = 0, outbox = 0),
            demo.counts("r1"),
        )

        demo.deleteArchived("r1")

        assertTrue(demo.recipients().list().none { it.id == "r1" })
        assertTrue(demo.notes().history("r1", null, null).isEmpty())
        assertTrue(demo.plans().list().none { it.recipientId == "r1" })
    }

    @Test
    fun demoWritesRejectArchivedRecipient() = runTest {
        val demo = DemoBackendApis()
        demo.archive("r1")

        try {
            demo.notes().create(noteRequest("r1"))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            demo.notes().append("n1", CreateAddendumRequest("late"))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            demo.plans().suggest(SuggestPlanRequest("r1", listOf("walk")))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            demo.plans().accept("p1")
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
        try {
            demo.recipients().update("r1", UpdateRecipientRequest(active = false))
            fail("Expected ArchivedRecipientException")
        } catch (expected: ArchivedRecipientException) {
            assertEquals("r1", expected.recipientId)
        }
    }

    private fun noteRequest(recipientId: String) = CreateNoteRequest(
        recipientId = recipientId,
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 1,
        fall = false,
        text = "note",
    )

    private suspend fun seedRecipient(
        id: String,
        archivedAt: Long? = null,
        active: Boolean = true,
    ) {
        db.recipientDao().insertAll(
            listOf(
                RecipientEntity(
                    serverId = id,
                    name = "Name $id",
                    active = active,
                    archivedAt = archivedAt,
                ),
            ),
        )
    }

    private suspend fun seedNote(id: String, recipientId: String) {
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = id,
                    recipientId = recipientId,
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
    }

    private suspend fun seedPlan(id: String, recipientId: String) {
        db.planDao().upsert(
            PlanEntity(
                id = id,
                recipientId = recipientId,
                versionsJson = """[{"version":1,"status":"Suggested","items":["walk"],"createdAt":"2026-10-24","reason":""}]""",
                updatedAt = 1L,
            ),
        )
    }

    private suspend fun seedMedication(id: String, recipientId: String) {
        db.medicationDao().upsert(
            MedicationEntity(
                id = id,
                recipientId = recipientId,
                name = "Med",
                dose = null,
                schedule = null,
                active = true,
                createdAt = 1L,
            ),
        )
    }

    private suspend fun seedData(recipientId: String) {
        seedNote("n-$recipientId", recipientId)
        db.addendumDao().insert(
            AddendumEntity(
                id = "a-$recipientId",
                noteId = "n-$recipientId",
                createdAt = "2026-10-24T10:00:00",
                text = "addendum",
            ),
        )
        seedPlan("p-$recipientId", recipientId)
        seedMedication("m-$recipientId", recipientId)
        db.medicationDao().upsertIntake(
            MedicationIntakeEntity(
                id = "m-$recipientId:2026-10-24:0",
                medicationId = "m-$recipientId",
                date = "2026-10-24",
                times = 1,
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                localId = "o-$recipientId",
                recipientId = recipientId,
                mood = "good",
                appetite = "good",
                sleep = "good",
                mobility = "walks",
                medicationTaken = "taken",
                pain = 1,
                fall = false,
                text = "outbox",
                date = "2026-10-24",
                createdAt = 1L,
            ),
        )
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000
    }
}
