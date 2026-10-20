package com.caregiver.mobile.ai

import kotlinx.serialization.Serializable

/**
 * The largest model reply the app will read.
 *
 * Both payloads are far below this in practice — a summary is a handful of
 * observations, a plan a few dozen lines — so the cap guards against a
 * rambling reply rather than limiting real content. The same number for
 * both services keeps the behaviour identical whichever one is asked.
 */
const val MAX_REPLY_CHARS = 50_000

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-20241022"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-2.0-flash"),
    XAI("xAI", "https://api.x.ai/v1", "grok-3"),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
}

data class LlmRequest(
    val systemPrompt: String,
    val dataBlock: String,
    /**
     * Ask the provider for a json-only reply through its native json mode
     * (Gemini `responseMimeType`, OpenAI `response_format`). Providers that do
     * not implement it are detected and retried without it.
     */
    val jsonMode: Boolean = false,
) {
    init {
        require(systemPrompt.isNotBlank()) { "systemPrompt must be non-blank" }
    }
}

/**
 * Internal marker for a provider that rejected the native json-mode
 * parameter. It never reaches the UI: the client retries without the
 * parameter before giving up.
 */
internal const val JSON_MODE_UNSUPPORTED = "json_mode_unsupported"

enum class LlmStatus { OK, REPAIRED, FALLBACK }

data class LlmResult(
    val status: LlmStatus,
    val text: String = "",
    val error: String? = null,
    val httpCode: Int? = null,
)

interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResult
}
