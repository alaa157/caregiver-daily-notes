package com.caregiver.mobile.ai.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ModelListHttpTest {
    private val server = MockWebServer()
    private val headers = mapOf("x-api-key" to "key")

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun modelUrl(): String = server.url("/models").toString()

    @Test
    fun `a successful GET returns the body and sends the headers`() {
        server.start()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"m"}]}"""))

        val result = runBlocking { ModelListHttp().get(modelUrl(), headers) }

        assertEquals(HttpJson.Ok("""{"data":[{"id":"m"}]}"""), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("key", request.getHeader("x-api-key"))
    }

    @Test
    fun `unsafe urls are rejected before any request is made`() {
        server.start()
        val base = modelUrl()
        val port = server.port
        val unsafe = listOf(
            "",
            "   ",
            "file:///tmp/models",
            "http://example.com/models",
            "$base#top",
            "http://user:pass@127.0.0.1:$port/models",
            "http://127.0.0.1:$port/mod els",
        )

        unsafe.forEach { url ->
            assertEquals(
                "expected invalid_url for '$url'",
                HttpJson.Failed("invalid_url"),
                runBlocking { ModelListHttp().get(url, headers) },
            )
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a url with a query string is accepted and sent verbatim`() {
        server.start()
        server.enqueue(MockResponse().setBody("[]"))
        val url = server.url("/v1beta/models").toString() + "?pageSize=200&pageToken=a%2Fb"

        val result = runBlocking { ModelListHttp().get(url, headers) }

        assertEquals(HttpJson.Ok("[]"), result)
        val request = server.takeRequest()
        assertEquals("/v1beta/models?pageSize=200&pageToken=a%2Fb", request.path)
    }

    @Test
    fun `cleartext to a dev host is accepted`() {
        server.start()
        server.enqueue(MockResponse().setBody("[]"))

        val result = runBlocking { ModelListHttp().get(modelUrl(), headers) }

        assertEquals(HttpJson.Ok("[]"), result)
    }

    @Test
    fun `the android emulator host passes url validation`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .build()

        val result = runBlocking {
            ModelListHttp(client).get("http://10.0.2.2:8080/models", headers)
        }

        // It cannot be reached from the test JVM, but it must get past the
        // URL checks and fail as a network error, never as invalid_url.
        assertTrue(result is HttpJson.Failed && result.reasonCode == "network")
    }

    @Test
    fun `empty or non printable key values are rejected before any request`() {
        server.start()
        listOf("", " ", "Bearer key", "cl\u00e9", "key\nline").forEach { value ->
            assertEquals(
                HttpJson.Failed("invalid_key"),
                runBlocking { ModelListHttp().get(modelUrl(), mapOf("x-api-key" to value)) },
            )
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `http errors map to reason codes`() {
        server.start()
        val cases = listOf(
            MockResponse().setResponseCode(401) to "invalid_key",
            MockResponse().setResponseCode(429) to "rate_limit",
            MockResponse().setResponseCode(404).setBody("""{"message":"unknown path"}""") to "http_404",
            MockResponse().setResponseCode(404)
                .setBody("""{"error":{"code":"model_not_found"}}""") to "invalid_model",
        )

        cases.forEach { (response, reasonCode) ->
            server.enqueue(response)
            assertEquals(
                HttpJson.Failed(reasonCode),
                runBlocking { ModelListHttp().get(modelUrl(), headers) },
            )
        }
    }

    @Test
    fun `a body over the cap returns too_large without throwing`() {
        server.start()
        server.enqueue(MockResponse().setBody("x".repeat(MAX_MODELS_BODY_CHARS + 1)))

        val result = runBlocking { ModelListHttp().get(modelUrl(), headers) }

        assertEquals(HttpJson.Failed("too_large"), result)
    }

    @Test
    fun `a body exactly at the cap is accepted`() {
        server.start()
        val body = "x".repeat(MAX_MODELS_BODY_CHARS)
        server.enqueue(MockResponse().setBody(body))

        val result = runBlocking { ModelListHttp().get(modelUrl(), headers) }

        assertEquals(HttpJson.Ok(body), result)
    }

    @Test
    fun `a socket timeout returns network`() {
        server.start()
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(1, TimeUnit.SECONDS))
        val client = OkHttpClient.Builder()
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .build()

        val result = runBlocking { ModelListHttp(client).get(modelUrl(), headers) }

        assertEquals(HttpJson.Failed("network"), result)
    }

    @Test
    fun `a connection failure returns network`() {
        server.start()
        val deadUrl = modelUrl()
        server.shutdown()

        val result = runBlocking { ModelListHttp().get(deadUrl, headers) }

        assertEquals(HttpJson.Failed("network"), result)
    }

    @Test
    fun `the response is closed on success error and oversized bodies`() {
        server.start()
        var closes = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.proceed(chain.request())
                val tracked = object : ResponseBody() {
                    override fun contentType() = original.body?.contentType()
                    override fun contentLength() = original.body?.contentLength() ?: -1L
                    override fun source() = original.body!!.source()
                    override fun close() {
                        closes++
                        original.close()
                    }
                }
                original.newBuilder().body(tracked).build()
            }
            .build()
        val helper = ModelListHttp(client)

        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(HttpJson.Ok("[]"), runBlocking { helper.get(modelUrl(), headers) })

        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(HttpJson.Failed("invalid_key"), runBlocking { helper.get(modelUrl(), headers) })

        server.enqueue(MockResponse().setBody("x".repeat(MAX_MODELS_BODY_CHARS + 1)))
        assertEquals(HttpJson.Failed("too_large"), runBlocking { helper.get(modelUrl(), headers) })

        assertEquals(3, closes)
    }

    @Test
    fun `cancelling the caller cancels the request`() = runBlocking {
        withTimeout(5_000) {
            server.start()
            server.enqueue(
                MockResponse()
                    .setBodyDelay(10, TimeUnit.SECONDS)
                    .setBody("""{"data":[]}"""),
            )
            val job = launch(Dispatchers.IO) {
                ModelListHttp(OkHttpClient()).get(
                    server.url("/models").toString(),
                    mapOf("Authorization" to "Bearer sk-test"),
                )
            }
            delay(200)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
    }
}
