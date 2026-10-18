package com.caregiver.mobile.ai

import com.caregiver.mobile.core.network.OkHttpConfig
import okhttp3.OkHttpClient

class LlmClientFactory(private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient()) {
    fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return OpenAiCompatibleLlmClient(baseUrl, model, apiKey, okHttpClient)
    }

    fun createGeminiClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return GeminiLlmClient(baseUrl, model, apiKey, okHttpClient)
    }

    fun createAnthropicClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return AnthropicLlmClient(baseUrl, model, apiKey, okHttpClient)
    }
}
