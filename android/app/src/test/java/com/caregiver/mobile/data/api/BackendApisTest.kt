package com.caregiver.mobile.data.api

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.SettingsStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Provider behavior against fake servers: invalid stored URLs fail before
 * any request, URL edits apply to the next call, and bearer tokens never
 * follow a redirect to a different origin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackendApisTest {

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer
    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var tokens: TokenHolder
    private lateinit var apis: RetrofitBackendApis

    @Before
    fun setUp() {
        serverA = MockWebServer()
        serverA.start()
        serverB = MockWebServer()
        serverB.start()
        file = File.createTempFile("backend-apis-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        tokens = TokenHolder()
        apis = RetrofitBackendApis(settings, tokens)
    }

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
        file.delete()
    }

    @Test
    fun invalidStoredUrlFailsBeforeAnyRequest(): Unit = runBlocking {
        settings.setBaseUrl("ftp://files.example.com/")

        try {
            apis.auth()
            fail("expected InvalidBaseUrlException")
        } catch (e: InvalidBaseUrlException) {
            assertEquals(UrlProblem.UnsupportedScheme, e.problem)
        }
        assertEquals(0, serverA.requestCount)
    }

    @Test
    fun urlChangeTakesEffectOnNextCallWithoutRestart(): Unit = runBlocking {
        serverA.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"a"}"""))
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"b"}"""))

        settings.setBaseUrl(serverA.url("/").toString())
        assertEquals("a", apis.auth().login(LoginRequest("e", "p")).token)
        settings.setBaseUrl(serverB.url("/").toString())
        assertEquals("b", apis.auth().login(LoginRequest("e", "p")).token)

        assertEquals(1, serverA.requestCount)
        assertEquals(1, serverB.requestCount)
    }

    @Test
    fun bearerNotForwardedToDifferentOriginOnRedirect(): Unit = runBlocking {
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        serverA.enqueue(
            MockResponse().setResponseCode(301)
                .addHeader("Location", serverB.url("/target").toString()),
        )
        tokens.token = "tok"

        val api = ApiClient.retrofit(serverA.url("/").toString(), tokens)
            .create(RecipientApi::class.java)
        api.list()

        assertEquals("/api/recipients", serverA.takeRequest().path)
        val redirected = serverB.takeRequest()
        assertEquals("/target", redirected.path)
        assertNull(redirected.getHeader("Authorization"))
        assertTrue(true)
    }

    @Test
    fun bearerSentToSameOrigin(): Unit = runBlocking {
        serverA.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        tokens.token = "tok"

        val api = ApiClient.retrofit(serverA.url("/").toString(), tokens)
            .create(RecipientApi::class.java)
        api.list()

        assertEquals("Bearer tok", serverA.takeRequest().getHeader("Authorization"))
    }
}
