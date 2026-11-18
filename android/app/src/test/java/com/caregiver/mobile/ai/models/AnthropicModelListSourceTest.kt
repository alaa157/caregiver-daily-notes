package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicModelListSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: AnthropicModelListSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        source = AnthropicModelListSource(ModelListHttp(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString()

    private fun page(ids: List<String>, hasMore: Boolean = false, lastId: String? = null): String {
        val entries = ids.joinToString(",") { "{\"id\":\"$it\",\"display_name\":\"$it\"}" }
        val tail = if (hasMore) {
            ",\"has_more\":true,\"last_id\":\"$lastId\""
        } else {
            ",\"has_more\":false"
        }
        return "{\"data\":[$entries]$tail}"
    }

    @Test
    fun `first request uses the anthropic path and headers only`() {
        server.enqueue(MockResponse().setBody(page(listOf("claude-sonnet-5-5"))))

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        assertEquals(
            ModelListResult.Ok(listOf(AiModelOption(id = "claude-sonnet-5-5", jsonMode = true))),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path?.substringBefore("?"))
        assertEquals("100", request.requestUrl.queryParameter("limit"))
        assertEquals("sk-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `two pages return the union of both pages`() {
        server.enqueue(MockResponse().setBody(page(listOf("claude-a"), hasMore = true, lastId = "cursor-1")))
        server.enqueue(MockResponse().setBody(page(listOf("claude-b"))))

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        assertEquals(listOf("claude-a", "claude-b"), (result as ModelListResult.Ok).models.map { it.id })
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        assertNull(first.requestUrl.queryParameter("after_id"))
        val second = server.takeRequest()
        assertEquals("100", second.requestUrl.queryParameter("limit"))
        assertEquals("cursor-1", second.requestUrl.queryParameter("after_id"))
    }

    @Test
    fun `after_id is url encoded when it contains slashes and colons`() {
        server.enqueue(MockResponse().setBody(page(listOf("claude-a"), hasMore = true, lastId = "a/b:c")))
        server.enqueue(MockResponse().setBody(page(listOf("claude-b"))))

        runBlocking { source.fetch(baseUrl(), "sk-key") }

        server.takeRequest()
        val rawPath = server.takeRequest().path.orEmpty()
        assertTrue(rawPath.contains("after_id=a%2Fb%3A"))
        assertFalse(rawPath.contains("after_id=a/b:c"))
    }

    @Test
    fun `a never ending has_more loop stops after five pages`() {
        repeat(8) {
            server.enqueue(MockResponse().setBody(page(listOf("claude-$it"), hasMore = true, lastId = "c$it")))
        }

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        assertEquals(5, server.requestCount)
        assertTrue(result is ModelListResult.Ok)
    }

    @Test
    fun `a 401 returns invalid_key`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"type\":\"authentication_error\"}"))

        val result = runBlocking { source.fetch(baseUrl(), "bad-key") }

        assertEquals(ModelListResult.Failed("invalid_key"), result)
    }

    @Test
    fun `malformed json returns malformed`() {
        server.enqueue(MockResponse().setBody("{not json"))

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        assertEquals(ModelListResult.Failed("malformed"), result)
    }

    @Test
    fun `empty data with has_more false returns empty`() {
        server.enqueue(MockResponse().setBody("{\"data\":[]}"))

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        assertEquals(ModelListResult.Failed("empty"), result)
    }

    @Test
    fun `ids are deduplicated sorted and capped at 500`() {
        val ids = (0 until 520).map { "claude-${(519 - it).toString().padStart(4, '0')}" }.distinct()
        server.enqueue(MockResponse().setBody(page(ids)))

        val result = runBlocking { source.fetch(baseUrl(), "sk-key") }

        val models = (result as ModelListResult.Ok).models.map { it.id }
        assertEquals(500, models.size)
        assertEquals(models.sorted(), models)
        assertEquals(500, models.toSet().size)
    }

    @Test
    fun `a blank base url fails without a request`() {
        val result = runBlocking { source.fetch("   ", "sk-key") }

        assertEquals(ModelListResult.Failed("invalid_url"), result)
        assertEquals(0, server.requestCount)
    }
}
