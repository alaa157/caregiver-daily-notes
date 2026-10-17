package com.caregiver.mobile.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiLlmClientTest {
    private val server = MockWebServer()
    @After fun tearDown() { server.shutdown() }

    @Test fun success() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"yo"}]}}]}"""))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("yo", res.text)
    }

    @Test fun unauthorized() {
        server.start(); server.enqueue(MockResponse().setResponseCode(401))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_key", res.error)
    }

    @Test fun rateLimit() {
        server.start(); server.enqueue(MockResponse().setResponseCode(429))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("rate_limit", res.error)
    }

    @Test fun networkFailure() {
        val client = GeminiLlmClient("http://127.0.0.1:1", "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("network", res.error)
    }
}
