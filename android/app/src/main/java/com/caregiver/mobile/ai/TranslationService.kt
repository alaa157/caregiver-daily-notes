package com.caregiver.mobile.ai

class TranslationService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
) {
    suspend fun translate(text: String, from: String, to: String): String? {
        val prompt = keyStore?.translationSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
            ?: Prompts.TRANSLATE_SYSTEM
        val res = client.complete(
            LlmRequest(prompt, "Translate from $from to $to:\n$text"),
        )
        return if (res.status == LlmStatus.FALLBACK) null else res.text
    }
}
