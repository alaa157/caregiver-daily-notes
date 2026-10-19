package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SummaryDto
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryAiServiceTest {
    private val otherNote = NoteDto(
        id = "n2", recipientId = "r2", date = "2026-10-01",
        mood = "ok", appetite = "good", sleep = "good", mobility = "ok",
        medicationTaken = "yes", pain = 1, fall = false, text = "Other recipient secret",
    )

    private val note = NoteDto(
        id = "n1", recipientId = "r1", date = "2026-10-01",
        mood = "ok", appetite = "good", sleep = "good", mobility = "ok",
        medicationTaken = "yes", pain = 1, fall = false, text = "Patient walked today",
    )

    private fun service(response: String): SummaryAiService {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, response)
        }
        return SummaryAiService(client)
    }

    @Test
    fun malformedJsonReturnsFailure() = runBlocking {
        val svc = service("not json")
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("malformed_output", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun fabricatedNoteIdIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"x","noteId":"nope","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun incorrectQuoteIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"x","noteId":"n1","quote":"never happened"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun validObservationIsKept() = runBlocking {
        val json = """{"observations":[{"text":"walked","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Success)
        val summary = (result as SummaryAiResult.Success).summary
        assertEquals(1, summary.evidence.size)
        assertEquals("n1", summary.evidence[0].noteId)
    }

    @Test
    fun emptyNotesReturnsNoSourceData() = runBlocking {
        val json = """{"observations":[],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, emptyList(), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("no_source_data", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun emptyClaimIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun emptyQuoteIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"walked","noteId":"n1","quote":""}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun unrelatedClaimIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"took medication","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

    @Test
    fun contradictoryClaimIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"did not walk","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }


    @Test
    fun mixedRecipientNotesAreFilteredBeforePrompt() = runBlocking {
        val captured = arrayOfNulls<LlmRequest>(1)
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult {
                captured[0] = request
                return LlmResult(LlmStatus.OK, """{"observations":[{"text":"walked","noteId":"n1","quote":"walked"}],"uncertainties":[]}""")
            }
        }
        val svc = SummaryAiService(client)
        val result = svc.summarize("r1", 7, listOf(note, otherNote), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Success)
        val request = captured[0]!!
        assertTrue(!request.dataBlock.contains("Other recipient secret"))
        assertTrue(!request.dataBlock.contains("n2"))
    }

    @Test
    fun mixedRecipientSignalsDoNotReachSafetyEvaluation() = runBlocking {
        val r1Signal = com.caregiver.mobile.data.api.SignalDto(
            noteId = "n1", recipientId = "r1", date = "2026-10-01",
            fallReported = false, pain = 1, medication = "taken", medicationUnverified = false,
            poorAppetite = false, text = "r1",
        )
        val r2Signal = com.caregiver.mobile.data.api.SignalDto(
            noteId = "n2", recipientId = "r2", date = "2026-10-01",
            fallReported = true, pain = 9, medication = "missed", medicationUnverified = true,
            poorAppetite = true, text = "r2",
        )
        val json = """{"observations":[{"text":"walked","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note), listOf(r1Signal, r2Signal), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Success)
        val summary = (result as SummaryAiResult.Success).summary
        assertTrue(!summary.redFlags.contains("FALL_REPORTED"))
        assertTrue(!summary.redFlags.contains("HIGH_PAIN"))
    }

    @Test
    fun evidenceForOtherRecipientIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"x","noteId":"n2","quote":"secret"}],"uncertainties":[]}"""
        val svc = service(json)
        val result = svc.summarize("r1", 7, listOf(note, otherNote), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(result is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (result as SummaryAiResult.Failure).reasonCode)
    }

}
