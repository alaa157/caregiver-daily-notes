package com.caregiver.mobile.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cancellation has to stop the request that is already on the wire, not
 * only a coroutine waiting for a suspend point. This is the regression test
 * for a blocking Call.execute() that ignored cancellation.
 */
class CallCancellationTest {
    @Test
    fun cancellingTheCoroutineCancelsTheInFlightCall() = runBlocking {
        val server = MockWebServer()
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestStarted.countDown()
                // Hold the response until the test has cancelled the caller.
                releaseRequest.await(5, TimeUnit.SECONDS)
                return MockResponse().setBody("{}")
            }
        }
        server.start()
        try {
            val call = OkHttpClient().newCall(
                Request.Builder().url(server.url("/")).build(),
            )
            val job = launch(Dispatchers.IO) {
                val cancellation = CallCancellation()
                cancellation.install()
                try {
                    cancellation.track(call)
                    call.execute().use { }
                } finally {
                    cancellation.close()
                }
            }

            assertTrue(
                "request never reached the server",
                requestStarted.await(2, TimeUnit.SECONDS),
            )
            job.cancel()
            withTimeout(2_000) { job.join() }

            assertTrue(
                "the in-flight HTTP call survived coroutine cancellation",
                call.isCanceled(),
            )
        } finally {
            releaseRequest.countDown()
            server.shutdown()
        }
    }
}
