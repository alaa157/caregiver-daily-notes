package com.caregiver.mobile.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSuggestionServiceTest {
    @Test
    fun returnsSuggestionFromValidJson() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(
                    LlmStatus.OK,
                    """{"changes":[{"actionId":"monitor","params":{},"reason":"watch closely","evidenceNoteIds":["n1"]}],"proposedMedications":[]}""",
                )
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Success)
        assertEquals(1, (result as PlanSuggestionResult.Success).suggestion.changes.size)
    }

    @Test
    fun returnsFailureOnInvalidJson() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, "not json")
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Failure)
        assertEquals("parse_error", (result as PlanSuggestionResult.Failure).reasonCode)
    }

    @Test
    fun returnsFailureOnFallback() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.FALLBACK, "", "network", null)
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Failure)
        assertEquals("llm_fallback", (result as PlanSuggestionResult.Failure).reasonCode)
    }

    @Test
    fun rejectsNonEmptyProposedMedications() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, """{"changes":[],"proposedMedications":[{"name":"X","dose":"1","schedule":"daily"}]}""")
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", emptySet())
        assertTrue(result is PlanSuggestionResult.Failure)
        assertEquals("medications_rejected", (result as PlanSuggestionResult.Failure).reasonCode)
    }

    @Test
    fun acceptsEmptyProposedMedications() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, """{"changes":[{"actionId":"monitor","params":{},"reason":"r","evidenceNoteIds":[]}],"proposedMedications":[]}""")
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", emptySet())
        assertTrue(result is PlanSuggestionResult.Success)
        assertTrue((result as PlanSuggestionResult.Success).suggestion.proposedMedications.isEmpty())
    }

    @Test
    fun rejectsMalformedMedicationData() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, """{"changes":[],"proposedMedications":"bad"}""")
        }
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", emptySet())
        assertTrue(result is PlanSuggestionResult.Failure)
    }
}