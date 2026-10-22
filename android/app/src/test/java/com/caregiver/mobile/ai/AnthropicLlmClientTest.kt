package com.caregiver.mobile.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicLlmClientTest {
    private val server = MockWebServer()
    @After fun tearDown() { server.shutdown() }

    @Test fun success() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"hi"}]}"""))
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("hi", res.text)
    }

    @Test fun unauthorized() {
        server.start(); server.enqueue(MockResponse().setResponseCode(401))
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_key", res.error)
    }

    @Test fun rateLimit() {
        server.start(); server.enqueue(MockResponse().setResponseCode(429))
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("rate_limit", res.error)
    }

    @Test fun networkFailure() {
        val client = AnthropicLlmClient("http://127.0.0.1:1", "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("network", res.error)
    }

    @Test fun modelNotFound404PointsAtTheModelNotTheUrl() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"type":"error","error":{"type":"not_found_error","message":"model: m"}}"""),
        )
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_model", res.error)
    }

    @Test fun aTruncatedAnswerIsAskedAgainWithALargerBudget() {
        // Asking again with the same budget cuts the answer off again.
        server.start()
        server.enqueue(
            MockResponse().setBody(
                """{"content":[{"type":"text","text":"half"}],"stop_reason":"max_tokens"}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"whole"}]}"""))
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("whole", res.text)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"max_tokens\":4096"))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"max_tokens\":8192"))
    }

    @Test fun aTruncatedAnswerGivesUpAtTheBudgetCeiling() {
        server.start()
        repeat(4) {
            server.enqueue(
                MockResponse().setBody(
                    """{"content":[{"type":"text","text":"half"}],"stop_reason":"max_tokens"}""",
                ),
            )
        }
        val client = AnthropicLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("truncated", res.error)
        assertEquals(3, server.requestCount)
    }
}
