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
     *
     * A provider that serves free models defaults to the free one, so the
     * app does not spend the caregiver's money before they ask it to.
     */
    val defaultModel: String,
    /** What this provider offers for free, if anything. */
    val freeTier: AiFreeTier? = null,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-6-luna", AiFreeTier.TRIAL_CREDITS),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-3.1-flash-lite", AiFreeTier.FREE_MODELS),
    XAI("xAI", "https://api.x.ai/v1", "grok-4.7"),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/free", AiFreeTier.FREE_MODELS),
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

/**
 * Internal marker for a provider that rejected a custom temperature.
 *
 * Newer and reasoning models refuse a temperature other than their default
 * and answer 400. Which models do this changes with every release, so the
 * client reacts to the answer instead of keeping a list of model ids: the
 * request is retried without the parameter before giving up. The marker
 * never reaches the UI.
 */
internal const val TEMPERATURE_UNSUPPORTED = "temperature_unsupported"

/**
 * How much a request may ask for when the provider cut the answer short.
 *
 * A reply that stops at the token limit, or that comes back empty because
 * the budget went on the model's own reasoning, is not a transient failure:
 * asking the same question with the same budget fails the same way. The
 * client doubles the budget instead, up to this ceiling, and a bigger
 * budget than this is not what a caregiver's summary needs.
 */
const val MAX_TOKEN_BUDGET = 16_384

/** The output budget a request starts with. */
const val DEFAULT_TOKEN_BUDGET = 4_096

enum class LlmStatus { OK, REPAIRED, FALLBACK }

/**
 * HTTP status → reason code for every LLM client, with 404 refined by the
 * error body. A mistaken model id almost always arrives as a 404 whose body
 * names the model — OpenAI-family bodies carry `error.code = model_not_found`,
 * Anthropic uses `error.type = not_found_error`, and Gemini's message says the
 * model does not exist — while a wrong base URL produces a bare 404. Without a
 * body marker the code stays `http_404`: those users are pointed at the base
 * URL, model users at the model id (Finding: the misleading 404 copy).
 */
internal fun classifyLlmHttpError(code: Int, body: String): String {
    if (code == 401 || code == 403) return "invalid_key"
    if (code == 429) return "rate_limit"
    if (code == 404) {
        val haystack = body.lowercase()
        val namesModel = listOf("model_not_found", "not_found_error").any(haystack::contains) ||
            (
                haystack.contains("model") &&
                    (haystack.contains("not found") || haystack.contains("does not exist"))
                )
        return if (namesModel) "invalid_model" else "http_404"
    }
    return "http_$code"
}

data class LlmResult(
    val status: LlmStatus,
    val text: String = "",
    val error: String? = null,
    val httpCode: Int? = null,
)

interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResult
}
