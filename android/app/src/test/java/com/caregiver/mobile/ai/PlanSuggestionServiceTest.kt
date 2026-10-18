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
        val suggestion = service.suggest("summary", setOf("n1"))
        assertEquals(1, suggestion?.changes?.size)
        assertEquals("monitor", suggestion?.changes?.first()?.actionId)
    }

    @Test
    fun returnsNullOnInvalidJson() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, "not json")
        }
        val service = PlanSuggestionService(client)
        val suggestion = service.suggest("summary", setOf("n1"))
        assertNull(suggestion)
    }

    @Test
    fun fallsBackOnFailure() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.FALLBACK, "", "network", null)
        }
        val service = PlanSuggestionService(client)
        val suggestion = service.suggest("summary", setOf("n1"))
        assertNull(suggestion)
    }
}
