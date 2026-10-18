package com.caregiver.mobile.presentation.settings

import com.caregiver.mobile.ai.AiProvider

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
