package com.caregiver.mobile.ai

import kotlinx.serialization.Serializable

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-1.5-flash"),
    XAI("xAI", "https://api.x.ai/v1", "grok-2-latest"),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
}

data class LlmRequest(val systemPrompt: String, val dataBlock: String) {
    init {
        require(systemPrompt.isNotBlank()) { "systemPrompt must be non-blank" }
    }
}

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
