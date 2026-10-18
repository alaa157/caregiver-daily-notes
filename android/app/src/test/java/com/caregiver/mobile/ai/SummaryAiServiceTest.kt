package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryDto
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryAiServiceTest {
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
    fun malformedJsonReturnsMalformedUncertainty() = runBlocking {
        val svc = service("not json")
        val summary = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(summary.uncertainties.any { it.topic == "malformed_output" })
    }

    @Test
    fun fabricatedNoteIdIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"x","noteId":"nope","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val summary = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(summary.evidence.isEmpty())
        assertTrue(summary.uncertainties.any { it.topic == "invalid_evidence" })
    }

    @Test
    fun incorrectQuoteIsRejected() = runBlocking {
        val json = """{"observations":[{"text":"x","noteId":"n1","quote":"never happened"}],"uncertainties":[]}"""
        val svc = service(json)
        val summary = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(summary.evidence.isEmpty())
        assertTrue(summary.uncertainties.any { it.topic == "invalid_evidence" })
    }

    @Test
    fun validObservationIsKept() = runBlocking {
        val json = """{"observations":[{"text":"walked","noteId":"n1","quote":"walked"}],"uncertainties":[]}"""
        val svc = service(json)
        val summary = svc.summarize("r1", 7, listOf(note), emptyList(), LocalDate.parse("2026-10-01"))
        assertEquals(1, summary.evidence.size)
        assertEquals("n1", summary.evidence[0].noteId)
    }

    @Test
    fun emptyNotesReturnsNoSourceData() = runBlocking {
        val json = """{"observations":[],"uncertainties":[]}"""
        val svc = service(json)
        val summary = svc.summarize("r1", 7, emptyList(), emptyList(), LocalDate.parse("2026-10-01"))
        assertTrue(summary.uncertainties.any { it.topic == "no_source_data" })
    }
}