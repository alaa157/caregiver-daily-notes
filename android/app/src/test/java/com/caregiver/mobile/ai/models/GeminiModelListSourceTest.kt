package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelListSourceTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun entry(name: String, vararg methods: String): String {
        val methodsJson = methods.joinToString(",") { "\"$it\"" }
        return "{\"name\":\"$name\",\"supportedGenerationMethods\":[$methodsJson]}"
    }

    private fun page(vararg entries: String, nextToken: String? = null): String {
        val models = entries.joinToString(",")
        val suffix = if (nextToken == null) "" else ",\"nextPageToken\":\"$nextToken\""
        return "{\"models\":[$models]$suffix}"
    }

    private fun fetch(key: String = "key-123") = runBlocking {
        GeminiModelListSource().fetch(baseUrl(), key)
    }

    @Test
    fun `first request uses the v1beta models path with page size`() {
        server.enqueue(MockResponse().setBody(page(entry("models/gemini-2.5-flash", "generateContent"))))

        assertTrue(fetch() is ModelListResult.Ok)
        assertEquals("/v1beta/models?pageSize=200", server.takeRequest().path)
    }

    @Test
    fun `the key is sent as x-goog-api-key and never in the url`() {
        server.enqueue(MockResponse().setBody(page(entry("models/gemini-2.5-flash", "generateContent"))))

        assertTrue(fetch("secret-key-999") is ModelListResult.Ok)
        val request = server.takeRequest()
        assertEquals("secret-key-999", request.getHeader("x-goog-api-key"))
        assertFalse(request.path.orEmpty().contains("secret-key-999"))
        assertFalse(request.requestUrl.toString().contains("secret-key-999"))
    }

    @Test
    fun `the models prefix is stripped from the id`() {
        server.enqueue(MockResponse().setBody(page(entry("models/gemini-2.5-flash", "generateContent"))))

        assertEquals(
            ModelListResult.Ok(listOf(AiModelOption("gemini-2.5-flash", jsonMode = true, free = false))),
            fetch(),
        )
    }

    @Test
    fun `entries without generateContent are filtered out`() {
        server.enqueue(MockResponse().setBody(page(
            entry("models/text-embedding-004", "embedContent"),
            entry("models/aqa-model", "aqa"),
        )))

        assertEquals(ModelListResult.Failed("empty"), fetch())
    }

    @Test
    fun `generateContent alongside other methods is kept`() {
        server.enqueue(MockResponse().setBody(page(
            entry("models/gemini-2.5-flash", "generateContent", "countTokens"),
            entry("models/gemini-2.5-pro", "countTokens", "generateContent"),
        )))

        assertEquals(
            ModelListResult.Ok(listOf(
                AiModelOption("gemini-2.5-flash", jsonMode = true, free = false),
                AiModelOption("gemini-2.5-pro", jsonMode = true, free = false),
            )),
            fetch(),
        )
    }

    @Test
    fun `two pages joined by nextPageToken return the union`() {
        server.enqueue(MockResponse().setBody(page(
            entry("models/gemini-2.5-flash", "generateContent"),
            nextToken = "tok-2",
        )))
        server.enqueue(MockResponse().setBody(page(entry("models/gemini-2.5-pro", "generateContent"))))

        assertEquals(
            ModelListResult.Ok(listOf(
                AiModelOption("gemini-2.5-flash", jsonMode = true, free = false),
                AiModelOption("gemini-2.5-pro", jsonMode = true, free = false),
            )),
            fetch(),
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `pageToken is url encoded`() {
        server.enqueue(MockResponse().setBody(page(
            entry("models/gemini-2.5-flash", "generateContent"),
            nextToken = "a/b:c",
        )))
        server.enqueue(MockResponse().setBody(page(entry("models/gemini-2.5-pro", "generateContent"))))

        assertTrue(fetch() is ModelListResult.Ok)
        server.takeRequest()
        assertEquals(
            "/v1beta/models?pageSize=200&pageToken=a%2Fb%3Ac",
            server.takeRequest().path,
        )
    }

    @Test
    fun `a 401 or 403 returns invalid key`() {
        listOf(401, 403).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code))

            assertEquals(ModelListResult.Failed("invalid_key"), fetch())
        }
    }

    @Test
    fun `a body with no models key returns malformed`() {
        server.enqueue(MockResponse().setBody("{\"foo\":1}"))

        assertEquals(ModelListResult.Failed("malformed"), fetch())
    }

    @Test
    fun `invalid json returns malformed`() {
        server.enqueue(MockResponse().setBody("not json"))

        assertEquals(ModelListResult.Failed("malformed"), fetch())
    }

    @Test
    fun `an empty models array returns empty`() {
        server.enqueue(MockResponse().setBody("{\"models\":[]}"))

        assertEquals(ModelListResult.Failed("empty"), fetch())
    }

    @Test
    fun `a blank base url returns invalid url`() = runBlocking {
        assertEquals(
            ModelListResult.Failed("invalid_url"),
            GeminiModelListSource().fetch("  ", "key-123"),
        )
    }
}
