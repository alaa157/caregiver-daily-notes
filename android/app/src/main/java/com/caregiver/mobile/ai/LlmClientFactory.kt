package com.caregiver.mobile.ai

import com.caregiver.mobile.core.network.OkHttpConfig
import okhttp3.OkHttpClient

open class LlmClientFactory(private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient()) {
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
