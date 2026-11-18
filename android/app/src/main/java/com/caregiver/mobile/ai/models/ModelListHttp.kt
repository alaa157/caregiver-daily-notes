package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.classifyLlmHttpError
import com.caregiver.mobile.core.network.OkHttpConfig
import com.caregiver.mobile.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

/** The largest model-list body the app will hold in memory. */
internal const val MAX_MODELS_BODY_CHARS = 2_000_000

/** The result of a model-list GET: either a bounded body or a reason code. */
internal sealed interface HttpJson {
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
internal class ModelListHttp(
    private val client: OkHttpClient = OkHttpConfig.defaultOkHttpClient(),
) {
    suspend fun get(url: String, headers: Map<String, String>): HttpJson = withContext(Dispatchers.IO) {
        // The same validation as the completion clients: no whitespace,
        // fragment or userinfo, http(s) only, and cleartext only to dev hosts.
        // A query string is allowed here: model-list APIs paginate with
        // `pageSize`/`pageToken`, and the caller URL-encodes those values.
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.host.isEmpty()) {
            return@withContext HttpJson.Failed("invalid_url")
        }
        if (url.any { it.isWhitespace() } ||
            httpUrl.fragment != null ||
            httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
            return@withContext HttpJson.Failed("invalid_url")
        }
        if (httpUrl.scheme !in listOf("http", "https")) {
            return@withContext HttpJson.Failed("invalid_url")
        }
        if (httpUrl.scheme == "http" && !ApiClient.isDevHost(httpUrl.host)) {
            return@withContext HttpJson.Failed("invalid_url")
        }
        // The caller owns provider-specific header names; every value still
        // has to be a printable ASCII key value, as for a completion request.
        if (headers.values.any { value ->
                value.isBlank() || value.any { c ->
                    c.isWhitespace() || c.isISOControl() || c.code !in 0x21..0x7e
                }
            }) {
            return@withContext HttpJson.Failed("invalid_key")
        }

        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body
                    ?: return@withContext HttpJson.Failed("network")
                val bounded = readBounded(body)
                when {
                    !response.isSuccessful ->
                        HttpJson.Failed(classifyLlmHttpError(response.code, bounded.body))
                    bounded.tooLarge -> HttpJson.Failed("too_large")
                    else -> HttpJson.Ok(bounded.body)
                }
            }
        } catch (e: IOException) {
            // Includes socket/connect/read timeouts. Cancellation is not an
            // IOException, so a cancelled caller stays cancelled.
            HttpJson.Failed("network")
        }
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
