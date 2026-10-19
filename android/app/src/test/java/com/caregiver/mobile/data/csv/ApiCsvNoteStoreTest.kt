package com.caregiver.mobile.data.csv

import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.testNote
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * API-backed store: reads go through the repository/API seam, creates carry
 * every field, a pre-existing note returned for a duplicate day counts as
 * skipped (never imported), and failures are reported per row.
 */
class ApiCsvNoteStoreTest {

    private val notes = FakeNoteApi()
    private val recipients = FakeRecipientApi()
    private val apis = FakeBackendApis(FakeAuthApi()).also {
        it.noteApi = notes
        it.recipientApi = recipients
    }

    private fun planned(
        date: String = "2026-10-07",
        recipientId: String = "r1",
        text: String = "ate well اليوم",
        pain: Int = 2,
        fall: Boolean = false,
    ) = PlannedNote(
        line = 2,
        date = date,
        recipientId = recipientId,
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = pain,
        fall = fall,
        text = text,
    )

    @Test
    fun snapshotReadsRecipientsAndTheWholeHistory() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Mona", true)) }
        notes.historyHandler = { _, _, _ -> listOf(testNote(id = "n1")) }

        val snapshot = ApiCsvNoteStore(apis).snapshot()

        assertEquals(listOf("r1"), snapshot.recipients.map { it.id })
        assertEquals(listOf("n1"), snapshot.notes.map { it.id })
        assertEquals(Triple(null, null, null), notes.lastHistory)
    }

    @Test
    fun insertAllSendsEveryFieldThroughCreate() = runTest {
        notes.createHandler = { testNote(id = "created", pain = 5, fall = true) }
        val row = planned(pain = 5, fall = true, text = "ate well اليوم")

        val results = ApiCsvNoteStore(apis).insertAll(listOf(row))

        assertEquals(listOf(CsvInsertResult.Inserted), results)
        val sent = notes.lastCreate!!
        assertEquals("r1", sent.recipientId)
        assertEquals("good", sent.mood)
        assertEquals("good", sent.appetite)
        assertEquals("ok", sent.sleep)
        assertEquals("walks", sent.mobility)
        assertEquals("taken", sent.medicationTaken)
        assertEquals(5, sent.pain)
        assertEquals(true, sent.fall)
        assertEquals("ate well اليوم", sent.text)
    }

    @Test
    fun aPreExistingNoteReturnedForADuplicateDayIsCountedAsSkipped() = runTest {
        // The repository resolves a duplicate-day 422 to the older note, so
        // create() returns content that is not the row we sent.
        notes.createHandler = { testNote(id = "older", text = "different") }

        val results = ApiCsvNoteStore(apis).insertAll(listOf(planned()))

        assertEquals(listOf(CsvInsertResult.SkippedDuplicate), results)
    }

    @Test
    fun httpRejectionIsReportedPerRowWithoutFailingTheBatch() = runTest {
        notes.createHandler = { throw FakeAuthApi.httpError(422, "VALIDATION_ERROR") }
        val store = ApiCsvNoteStore(apis)

        val results = store.insertAll(listOf(planned(text = "a"), planned(date = "2026-10-08", text = "b")))

        assertEquals(2, results.size)
        results.forEach { result ->
            assertEquals(
                CsvFailureReason.INSERT_FAILED,
                (result as CsvInsertResult.Rejected).failure.reason,
            )
        }
    }

    @Test
    fun transportFailureIsReportedPerRow() = runTest {
        notes.createHandler = { throw IOException("offline") }

        val results = ApiCsvNoteStore(apis).insertAll(listOf(planned()))

        assertEquals(
            CsvFailureReason.INSERT_FAILED,
            (results.single() as CsvInsertResult.Rejected).failure.reason,
        )
    }

    @Test
    fun theServerAssignsTheDateSoDatesAreNotPreserved() {
        assertFalse(ApiCsvNoteStore(apis).preservesDates)
    }
}
