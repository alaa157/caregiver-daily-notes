package com.caregiver.mobile.data.api

import com.caregiver.mobile.core.network.TokenHolder
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * HTTP seam against a fake server: exact paths, request bodies, and the
 * Authorization header lifecycle (absent on login, Bearer once signed in).
 */
class AuthApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(tokens: TokenHolder = TokenHolder()): AuthApi =
        ApiClient.retrofit(server.url("/").toString(), tokens).create(AuthApi::class.java)

    @Test
    fun loginPostsNormalizedBodyToAuthPathWithoutHeader(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok"}"""))

        val response = api().login(LoginRequest("ali@example.com", "pw"))

        assertEquals("tok", response.token)
        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            """{"email":"ali@example.com","password":"pw"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun registerPostsToRegisterPath(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"token":"tok"}"""))

        api().register(RegisterRequest("a@b.c", "pw"))

        assertEquals("/api/auth/register", server.takeRequest().path)
    }

    @Test
    fun bearerHeaderSentWhenTokenPresent(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok"}"""))
        val tokens = TokenHolder().apply { token = "tok" }

        api(tokens).login(LoginRequest("a@b.c", "pw"))

        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun loginFailureSurfacesInvalidCredentialsBody(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"code":"INVALID_CREDENTIALS","message":"Invalid email or password."}"""),
        )

        try {
            api().login(LoginRequest("a@b.c", "wrong"))
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
            assertEquals("INVALID_CREDENTIALS", ApiErrors.parse(e)?.code)
        }
        assertTrue(true)
    }
}
