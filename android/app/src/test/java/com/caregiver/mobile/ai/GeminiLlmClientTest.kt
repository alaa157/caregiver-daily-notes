package com.caregiver.mobile.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun modelNotFound404PointsAtTheModelNotTheUrl() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":{"code":404,"status":"NOT_FOUND","message":"Models 'models/m' not found"}}"""),
        )
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("invalid_model", res.error)
    }

    @Test fun networkFailure() {
        val client = GeminiLlmClient("http://127.0.0.1:1", "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals("network", res.error)
    }

    @Test fun jsonModeAsksForJsonMimeType() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking {
            client.complete(LlmRequest("sys", "data", jsonMode = true))
        }
        assertEquals(LlmStatus.OK, res.status)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"responseMimeType\":\"application/json\""))
    }

    @Test fun plainRequestHasNoJsonMimeType() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("responseMimeType"))
    }

    @Test fun unsupportedJsonModeIsRetriedWithoutIt() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"Unknown parameter responseMimeType"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking {
            client.complete(LlmRequest("sys", "data", jsonMode = true))
        }
        assertEquals(LlmStatus.OK, res.status)
        assertTrue(server.takeRequest().body.readUtf8().contains("responseMimeType"))
        assertFalse(server.takeRequest().body.readUtf8().contains("responseMimeType"))
    }

    @Test fun blockedPromptIsReportedAndNotRetried() {
        server.start()
        server.enqueue(
            MockResponse().setBody(
                """{"promptFeedback":{"blockReason":"SAFETY","blockReasonMessage":"notes about a fall"}}""",
            ),
        )
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals(GeminiLlmClient.BLOCKED, res.error)
        // Retrying a request the provider refused only wastes the caregiver's
        // time and quota.
        assertEquals(1, server.requestCount)
    }

    @Test fun safetyFinishReasonIsReportedAndNotRetried() {
        server.start()
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}""",
            ),
        )
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.FALLBACK, res.status)
        assertEquals(GeminiLlmClient.BLOCKED, res.error)
        assertEquals(1, server.requestCount)
    }

    @Test fun severalPartsAreJoined() {
        server.start()
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"{\"observations\":"},{"text":"[]}"}]}}]}""",
            ),
        )
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("""{"observations":[]}""", res.text)
    }

    @Test fun thinkingPartIsSkipped() {
        server.start()
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"thinking about it","thought":true},{"text":"{\"ok\":true}"}]}}]}""",
            ),
        )
        val client = GeminiLlmClient(server.url("/").toString().trimEnd('/'), "m", "key")
        val res = kotlinx.coroutines.runBlocking { client.complete(LlmRequest("sys", "data")) }
        assertEquals(LlmStatus.OK, res.status)
        assertEquals("""{"ok":true}""", res.text)
    }
}
