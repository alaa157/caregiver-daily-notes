package com.caregiver.mobile.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleLlmClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun success() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hello"}}]}"""))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("hello", res.text)
    }

    @Test
    fun unauthorized() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(401))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_key", res.error)
    }

    @Test
    fun rateLimit() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(429))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("rate_limit", res.error)
    }

    @Test
    fun modelNotFound404PointsAtTheModelNotTheUrl() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":{"code":"model_not_found","message":"The model 'm' does not exist"}}"""),
        )
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_model", res.error)
    }

    @Test
    fun plain404WithoutAModelMarkerStaysOnTheUrlCopy() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"message":"unknown path"}"""),
        )
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("http_404", res.error)
    }

    @Test
    fun networkFailure() {
        server.start()
        server.shutdown()
        val client = OpenAiCompatibleLlmClient("http://127.0.0.1:1", "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("network", res.error)
    }

    @Test
    fun jsonModeAsksForJsonObjectFormat() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking {
            client.complete(LlmRequest("sys", "data", jsonMode = true))
        }
        assertEquals(LlmStatus.OK, res.status)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun jsonModeMentionsJsonInTheMessages() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking {
            client.complete(LlmRequest("Return ONLY valid JSON", "data", jsonMode = true))
        }
        assertEquals(LlmStatus.OK, res.status)
        // OpenAI rejects a json-mode request whose messages never say "json".
        assertTrue(server.takeRequest().body.readUtf8().contains("only json"))
    }

    @Test
    fun plainRequestHasNoResponseFormat() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hello"}}]}"""))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertFalse(server.takeRequest().body.readUtf8().contains("response_format"))
    }

    @Test
    fun unsupportedResponseFormatIsRetriedWithoutIt() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"Unsupported parameter: 'response_format'"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
        val client = OpenAiCompatibleLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking {
            client.complete(LlmRequest("sys", "data", jsonMode = true))
        }
        assertEquals(LlmStatus.OK, res.status)
        assertTrue(server.takeRequest().body.readUtf8().contains("response_format"))
        assertFalse(server.takeRequest().body.readUtf8().contains("response_format"))
    }
}
