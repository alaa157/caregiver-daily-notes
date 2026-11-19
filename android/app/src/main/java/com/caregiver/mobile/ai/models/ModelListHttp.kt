package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.classifyLlmHttpError
import com.caregiver.mobile.core.network.OkHttpConfig
import com.caregiver.mobile.data.api.ApiClient
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

/** The largest model-list body the app will hold in memory. */
internal const val MAX_MODELS_BODY_CHARS = 2_000_000

/** The result of a model-list GET: either a bounded body or a reason code. */
sealed interface HttpJson {
    data class Ok(val body: String) : HttpJson
    data class Failed(val reasonCode: String) : HttpJson
}

/**
 * One validated, size-capped GET shared by every model-list source.
 *
 * Keeping the URL checks, the key check and the body cap here means a new
 * provider cannot accidentally ship a weaker copy (SSRF or unbounded memory).
 * This helper knows nothing about providers: it does not parse the body, pick
 * a path or choose which header holds the key, and it never logs the URL, the
 * headers or the body.
 */
class ModelListHttp(
    private val client: OkHttpClient = OkHttpConfig.defaultOkHttpClient(),
) {
    suspend fun get(url: String, headers: Map<String, String>, bearerKey: String? = null): HttpJson =
        suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: HttpJson) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            // The same validation as the completion clients: no whitespace,
            // fragment or userinfo, http(s) only, and cleartext only to dev hosts.
            // A query string is allowed here: model-list APIs paginate with
            // `pageSize`/`pageToken`, and the caller URL-encodes those values.
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl == null || httpUrl.host.isEmpty()) {
                resumeOnce(HttpJson.Failed("invalid_url"))
                return@suspendCancellableCoroutine
            }
            if (url.any { it.isWhitespace() } ||
                httpUrl.fragment != null ||
                httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
                resumeOnce(HttpJson.Failed("invalid_url"))
                return@suspendCancellableCoroutine
            }
            if (httpUrl.scheme !in listOf("http", "https")) {
                resumeOnce(HttpJson.Failed("invalid_url"))
                return@suspendCancellableCoroutine
            }
            if (httpUrl.scheme == "http" && !ApiClient.isDevHost(httpUrl.host)) {
                resumeOnce(HttpJson.Failed("invalid_url"))
                return@suspendCancellableCoroutine
            }
            // The caller owns provider-specific header names; every value still
            // has to be a printable ASCII key value, as for a completion request.
            if (headers.values.any { value ->
                    value.isBlank() || value.any { c ->
                        c.isWhitespace() || c.isISOControl() || c.code !in 0x21..0x7e
                    }
                }) {
                resumeOnce(HttpJson.Failed("invalid_key"))
                return@suspendCancellableCoroutine
            }
            // Bearer-style providers pass the RAW key here: it is validated with
            // the same character rules, and the "Bearer " prefix is assembled only
            // at request build — the prefix must never go through the key check
            // (its space would fail every OpenAI/xAI/OpenRouter fetch). DEF-009.
            if (bearerKey != null &&
                (bearerKey.isBlank() || bearerKey.any { c ->
                    c.isWhitespace() || c.isISOControl() || c.code !in 0x21..0x7e
                })) {
                resumeOnce(HttpJson.Failed("invalid_key"))
                return@suspendCancellableCoroutine
            }

            val request = Request.Builder().url(url).get().apply {
                headers.forEach { (name, value) -> header(name, value) }
                if (bearerKey != null) header("Authorization", "Bearer $bearerKey")
            }.build()

            // The blocking execute() below used to keep running after the
            // caller (for example the model sheet's LaunchedEffect) was
            // cancelled. Enqueue instead and tie the OkHttp call to the caller.
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Includes socket/connect/read timeouts. Cancellation is
                    // not an IOException, so a cancelled caller stays cancelled.
                    resumeOnce(HttpJson.Failed("network"))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { resp ->
                            val body = resp.body
                            if (body == null) {
                                resumeOnce(HttpJson.Failed("network"))
                                return@use
                            }
                            val bounded = readBounded(body)
                            val result = when {
                                !resp.isSuccessful ->
                                    HttpJson.Failed(classifyLlmHttpError(resp.code, bounded.body))
                                bounded.tooLarge -> HttpJson.Failed("too_large")
                                else -> HttpJson.Ok(bounded.body)
                            }
                            resumeOnce(result)
                        }
                    } catch (e: IOException) {
                        // A failed bounded read or close must still complete
                        // the continuation exactly once; nothing may escape
                        // an OkHttp callback thread.
                        resumeOnce(HttpJson.Failed("network"))
                    }
                }
            })
        }

    private data class BoundedBody(val body: String, val tooLarge: Boolean)

    /**
     * Reads at most one character past the cap; never materializes the whole
     * body with `body.string()`, so an oversized reply cannot exhaust memory.
     * The reader is closed here and the response is closed by `use`.
     */
    private fun readBounded(body: ResponseBody): BoundedBody {
        val builder = StringBuilder()
        val buffer = CharArray(8_192)
        val reader = body.charStream()
        var tooLarge = false
        try {
            while (builder.length <= MAX_MODELS_BODY_CHARS) {
                val count = reader.read(
                    buffer,
                    0,
                    minOf(buffer.size, MAX_MODELS_BODY_CHARS + 1 - builder.length),
                )
                if (count <= 0) break
                builder.append(buffer, 0, count)
            }
            tooLarge = builder.length > MAX_MODELS_BODY_CHARS
        } finally {
            reader.close()
        }
        return BoundedBody(builder.toString(), tooLarge)
    }
}
