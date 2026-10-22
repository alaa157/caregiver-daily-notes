package com.caregiver.mobile.presentation.settings

import com.caregiver.mobile.ai.AiProvider

/**
 * The credentials a working AI setup consists of.
 *
 * A key that only lives in a text field is a key the rest of the app does
 * not have: the summary screen reads the stored key and would tell the
 * caregiver to set AI up again. Whatever the caregiver just checked has to
 * be stored, not just shown as working.
 */
data class AiCredentials(
    /** The provider that answered. */
    val provider: AiProvider,
    /** The key, trimmed. */
    val apiKey: String,
    /** The model to use; blank means "follow the provider default". */
    val model: String,
    /** The base URL to use; blank means "follow the provider default". */
    val baseUrl: String,
)

/**
 * Save-time validation for AI settings. A blank model or base URL means
 * "use the provider default", so only a non-blank value that cannot be used
 * is an error. Keeps the screen from silently persisting an empty or invalid
 * configuration while still allowing an explicit "AI off" state (no key).
 */
object AiSettingsValidation {
    /** False only for a non-blank URL that cannot work (bad scheme, no host, spaces). */
    fun isAcceptableBaseUrl(raw: String): Boolean {
        val url = raw.trim()
        if (url.isEmpty()) return true
        if (url.any { it.isWhitespace() }) return false
        val lower = url.lowercase()
        val schemeLength = when {
            lower.startsWith("https://") -> "https://".length
            lower.startsWith("http://") -> "http://".length
            else -> return false
        }
        return url.substring(schemeLength).substringBefore('/').isNotBlank()
    }

    /**
     * The credentials to store after a connection test succeeded, or null
     * when there is nothing worth storing.
     *
     * A test that worked is a setup the caregiver expects to stick: the
     * provider, the key, the model and the base URL are what the rest of the
     * app reads, so they are stored as they were tested.
     */
    fun credentialsFor(
        provider: AiProvider,
        apiKey: String,
        model: String,
        baseUrl: String,
    ): AiCredentials? {
        val key = apiKey.trim()
        if (key.isEmpty()) return null
        return AiCredentials(
            provider = provider,
            apiKey = key,
            model = normalizedModel(model, provider),
            baseUrl = normalizedBaseUrl(baseUrl, provider),
        )
    }

    /**
     * Model to persist: blank when the user kept the provider default (so
     * later changes to the default are picked up), the trimmed custom id
     * otherwise. Empty stored model means "follow app default".
     */
    fun normalizedModel(raw: String, provider: AiProvider): String {
        val model = raw.trim()
        return if (model.isEmpty() || model == provider.defaultModel) "" else model
    }

    /**
     * Base URL to persist: blank when the user kept the provider default, so
     * later changes to the default are picked up; the trimmed custom URL
     * otherwise.
     */
    fun normalizedBaseUrl(raw: String, provider: AiProvider): String {
        val url = raw.trim()
        return if (url.isEmpty() || url == provider.defaultBaseUrl) "" else url
    }
}
