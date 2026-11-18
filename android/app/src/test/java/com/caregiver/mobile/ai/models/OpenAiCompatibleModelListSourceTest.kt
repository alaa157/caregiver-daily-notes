package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleModelListSourceTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun source(
        provider: AiProvider,
        client: OkHttpClient = OkHttpClient(),
    ) = OpenAiCompatibleModelListSource(provider, ModelListHttp(client))

    private fun baseUrl(): String = server.url("/v1").toString()

    private fun bodyWithIds(ids: List<String>): String =
        "{\"data\":[" + ids.joinToString(",") { "{\"id\":\"$it\"}" } + "]}"

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun fetch(
        provider: AiProvider,
        body: String,
        url: String = baseUrl(),
        key: String = "sk-test",
    ): ModelListResult {
        enqueue(body)
        return runBlocking { source(provider).fetch(url, key) }
    }

    private fun models(result: ModelListResult): List<AiModelOption> {
        assertTrue("expected Ok but was $result", result is ModelListResult.Ok)
        return (result as ModelListResult.Ok).models
    }

    @Test
    fun `a mixed list keeps only the chat model`() {
        val result = fetch(
            AiProvider.OPENAI,
            """{"data":[{"id":"gpt-4o"},{"id":"text-embedding-3-large"}]}""",
        )

        assertEquals(ModelListResult.Ok(listOf(AiModelOption("gpt-4o"))), result)
    }

    @Test
    fun `openai embedding audio and image models are filtered out`() {
        val result = fetch(
            AiProvider.OPENAI,
            bodyWithIds(
                listOf("text-embedding-3-small", "whisper-1", "tts-1", "dall-e-3", "gpt-4o"),
            ),
        )

        assertEquals(listOf("gpt-4o"), models(result).map { it.id })
    }

    @Test
    fun `xai image models are filtered out`() {
        val result = fetch(AiProvider.XAI, bodyWithIds(listOf("grok-2-image", "grok-4.7")))

        assertEquals(listOf("grok-4.7"), models(result).map { it.id })
    }

    @Test
    fun `openrouter free variants are flagged by suffix or zero prompt price`() {
        val body = """{"data":[
            {"id":"openai/gpt-4o-mini"},
            {"id":"nvidia/nemotron:free"},
            {"id":"some/model","pricing":{"prompt":"0","completion":"0"}},
            {"id":"paid/model","pricing":{"prompt":"0.001"}}
        ]}"""
        val result = fetch(AiProvider.OPEN_ROUTER, body)

        val byId = models(result).associateBy { it.id }
        assertFalse(byId.getValue("openai/gpt-4o-mini").free)
        assertTrue(byId.getValue("nvidia/nemotron:free").free)
        assertTrue(byId.getValue("some/model").free)
        assertFalse(byId.getValue("paid/model").free)
        byId.values.forEach {
            assertTrue(it.jsonMode)
            assertEquals(null, it.note)
            assertFalse(it.recommendedForSummaries)
        }
    }

    @Test
    fun `openai and xai models are never marked free`() {
        for (provider in listOf(AiProvider.OPENAI, AiProvider.XAI)) {
            val body = """{"data":[{"id":"m","pricing":{"prompt":"0"}}]}"""
            assertFalse(models(fetch(provider, body)).single().free)
        }
    }

    @Test
    fun `a 401 keeps the helpers invalid key reason`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = runBlocking { source(AiProvider.OPENAI).fetch(baseUrl(), "sk-test") }

        assertEquals(ModelListResult.Failed("invalid_key"), result)
    }

    @Test
    fun `an empty data array fails with empty`() {
        assertEquals(
            ModelListResult.Failed("empty"),
            fetch(AiProvider.OPENAI, """{"data":[]}"""),
        )
    }

    @Test
    fun `malformed bodies fail with malformed`() {
        listOf("{not-json", "[]", """{"data":"not-an-array"}""").forEach { body ->
            assertEquals(
                "body=$body",
                ModelListResult.Failed("malformed"),
                fetch(AiProvider.OPENAI, body),
            )
        }
    }

    @Test
    fun `duplicate ids appear once`() {
        val result = fetch(
            AiProvider.OPENAI,
            bodyWithIds(listOf("gpt-4o", "gpt-4o", "gpt-4o-mini")),
        )

        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), models(result).map { it.id })
    }

    @Test
    fun `more than five hundred unique ids are sorted and truncated`() {
        val ids = (1..600).map { "m%04d".format(it) }.shuffled()
        val result = fetch(AiProvider.OPENAI, bodyWithIds(ids))

        val returned = models(result).map { it.id }
        assertEquals(500, returned.size)
        assertEquals(ids.sorted().take(500), returned)
    }

    @Test
    fun `the models path is appended to the configured base url`() {
        var requestedUrl: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                requestedUrl = original.url.toString()
                // Keep the path, redirect the call to the local test server.
                val local = original.newBuilder()
                    .url(server.url(original.url.encodedPath))
                    .build()
                chain.proceed(local)
            }
            .build()
        enqueue("""{"data":[{"id":"gpt-4o"}]}""")

        val result = runBlocking {
            source(AiProvider.OPENAI, client).fetch("https://api.openai.com/v1", "sk-secret")
        }

        assertEquals(listOf("gpt-4o"), models(result).map { it.id })
        assertEquals("https://api.openai.com/v1/models", requestedUrl)
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-secret", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `unsupported providers fail before any request`() {
        val result = runBlocking { source(AiProvider.ANTHROPIC).fetch(baseUrl(), "sk-test") }

        assertEquals(ModelListResult.Failed("unsupported_provider"), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank base url fails before any request`() {
        listOf("", "   ").forEach { url ->
            assertEquals(
                "url='$url'",
                ModelListResult.Failed("invalid_url"),
                runBlocking { source(AiProvider.OPENAI).fetch(url, "sk-test") },
            )
        }
        assertEquals(0, server.requestCount)
    }
}
