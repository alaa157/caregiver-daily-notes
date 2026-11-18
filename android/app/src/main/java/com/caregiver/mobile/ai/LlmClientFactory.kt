package com.caregiver.mobile.ai

import com.caregiver.mobile.core.network.OkHttpConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

open class LlmClientFactory(
    private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient()
        .newBuilder()
        // LLM completions can stall with byte-trickles that keep resetting
        // the per-read 60s timer, leaving the summary screen spinning
        // forever. A total per-call cap bounds every request so the UI
        // lands in its "AI unavailable + retry" state instead.
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    open fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return OpenAiCompatibleLlmClient(baseUrl, model, apiKey, okHttpClient)
    }

    open fun createGeminiClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return GeminiLlmClient(baseUrl, model, apiKey, okHttpClient)
    }

    open fun createAnthropicClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return AnthropicLlmClient(baseUrl, model, apiKey, okHttpClient)
    }
}
