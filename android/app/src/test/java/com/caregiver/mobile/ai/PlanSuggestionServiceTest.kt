package com.caregiver.mobile.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSuggestionServiceTest {
    @Test
    fun returnsItemsFromModel() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.OK, "item one\nitem two")
        }
        val service = PlanSuggestionService(client)
        val items = service.suggestItems("summary")
        assertEquals(2, items.size)
    }

    @Test
    fun fallsBackOnFailure() = runBlocking {
        val client = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult =
                LlmResult(LlmStatus.FALLBACK, "", "network", null)
        }
        val service = PlanSuggestionService(client)
        val items = service.suggestItems("summary")
        assertTrue(items.isEmpty())
    }
}