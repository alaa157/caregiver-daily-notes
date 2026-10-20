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

/**
 * What a provider offers to someone who does not want to pay yet.
 */
enum class AiFreeTier {
    /** The provider serves these models for free. */
    FREE_MODELS,

    /** A new API account gets credits to try the paid models with. */
    TRIAL_CREDITS,
}

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    /**
     * The model the app falls back to. Kept in step with
     * [AiModelCatalog]: the ids the providers still serve in 2026 (the old
     * defaults — gpt-4o-mini, gemini-2.0-flash, grok-3 — are retired or
     * deprecated).
     */
    val defaultModel: String,
    /** What this provider offers for free, if anything. */
    val freeTier: AiFreeTier? = null,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-6-luna", AiFreeTier.TRIAL_CREDITS),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-3.8-flash", AiFreeTier.FREE_MODELS),
    XAI("xAI", "https://api.x.ai/v1", "grok-4.7"),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", AiFreeTier.FREE_MODELS),
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
    /**
     * A budget for this call, in seconds; 0 means the client's own timeout.
     * A connection test uses a short one: a ping that has not answered in
     * twenty seconds is not going to answer.
     */
    val timeoutSeconds: Int = 0,
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
