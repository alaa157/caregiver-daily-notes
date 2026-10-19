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

    /** Client that answers with the next scripted reply and records requests. */
    private class ScriptedClient(private val replies: List<String>) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        override suspend fun complete(request: LlmRequest): LlmResult {
            requests += request
            val index = (requests.size - 1).coerceAtMost(replies.lastIndex)
            return LlmResult(LlmStatus.OK, replies[index])
        }
    }

    private val validPlanJson =
        """{"changes":[{"actionId":"monitor","params":{},"reason":"watch closely","evidenceNoteIds":["n1"]}],"proposedMedications":[]}"""

    @Test
    fun fencedJsonIsAccepted() = runBlocking {
        val client = ScriptedClient(listOf("```json\n$validPlanJson\n```"))
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Success)
        assertEquals(1, (result as PlanSuggestionResult.Success).suggestion.changes.size)
    }

    @Test
    fun preambleAroundJsonIsAccepted() = runBlocking {
        val client = ScriptedClient(listOf("Sure!\n$validPlanJson\nHope that helps."))
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Success)
        assertEquals(1, (result as PlanSuggestionResult.Success).suggestion.changes.size)
    }

    @Test
    fun malformedOutputTriggersOneRepairRetry() = runBlocking {
        val client = ScriptedClient(listOf("not json", validPlanJson))
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Success)
        assertEquals(LlmStatus.REPAIRED, (result as PlanSuggestionResult.Success).status)
        assertEquals(2, client.requests.size)
        assertTrue(client.requests[1].dataBlock.contains(LlmJson.REPAIR_INSTRUCTION))
    }

    @Test
    fun repairRetryIsSpentOnlyOnce() = runBlocking {
        val client = ScriptedClient(listOf("not json"))
        val service = PlanSuggestionService(client)
        val result = service.suggest("summary", setOf("n1"))
        assertTrue(result is PlanSuggestionResult.Failure)
        assertEquals("parse_error", (result as PlanSuggestionResult.Failure).reasonCode)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun planRequestAsksForNativeJsonMode() = runBlocking {
        val client = ScriptedClient(listOf(validPlanJson))
        val service = PlanSuggestionService(client)
        service.suggest("summary", setOf("n1"))
        assertTrue(client.requests[0].jsonMode)
    }
}