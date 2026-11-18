package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider

/**
 * The outcome of asking a provider for its model list.
 *
 * Fetchers return a result instead of throwing so a caller can tell an empty
 * catalog apart from a failed request and show the right message.
 */
sealed interface ModelListResult {
    data class Ok(val models: List<AiModelOption>) : ModelListResult
    data class Failed(val reasonCode: String) : ModelListResult
}

/**
 * A source that lists the models a provider actually serves.
 *
 * Each provider fetcher implements this interface so callers get one result
 * type whichever provider they ask, rather than a different type per source.
 */
interface ModelListSource {
    /** [baseUrl] is the user's override or the provider default, without a trailing slash. */
    suspend fun fetch(baseUrl: String, apiKey: String): ModelListResult
}

/** Model-id fragments that mark a model as something other than a chat/completion model. */
private val NON_CHAT_TOKENS = listOf(
    "embedding", "whisper", "tts-", "dall-e", "moderation", "realtime",
    "audio", "transcribe", "image", "sora", "davinci", "babbage",
)

/**
 * True when [id] is a chat/completion model, not an embedding, audio or image
 * model. Ids are matched case-insensitively because providers are not
 * consistent about casing.
 *
 * Gemini needs no id filter: its capability comes from
 * `supportedGenerationMethods`, which the Gemini source checks itself.
 */
internal fun isChatModel(provider: AiProvider, id: String): Boolean = when (provider) {
    AiProvider.ANTHROPIC, AiProvider.GEMINI -> true
    AiProvider.OPENAI, AiProvider.XAI, AiProvider.OPEN_ROUTER -> {
        val lower = id.lowercase()
        NON_CHAT_TOKENS.none { lower.contains(it) }
    }
}

/** Returns [baseUrl] without a trailing `/`, or `""` when it is blank. */
internal fun normalizeBaseUrl(baseUrl: String): String =
    if (baseUrl.isBlank()) "" else baseUrl.trimEnd('/')
