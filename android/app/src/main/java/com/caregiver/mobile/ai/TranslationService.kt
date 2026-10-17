package com.caregiver.mobile.ai

class TranslationService(private val client: LlmClient) {
    suspend fun translate(text: String, from: String, to: String): String? {
        val res = client.complete(
            LlmRequest(Prompts.TRANSLATE_SYSTEM, "Translate from $from to $to:\n$text"),
        )
        return if (res.status == LlmStatus.FALLBACK) null else res.text
    }
}
