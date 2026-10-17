package com.caregiver.mobile.ai

class PlanSuggestionService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
) {
    suspend fun suggestItems(summaryText: String): List<String> {
        val prompt = keyStore?.systemPrompt()?.takeIf { it.isNotBlank() } ?: Prompts.PLAN_SYSTEM
        val res = client.complete(LlmRequest(prompt, summaryText))
        if (res.status == LlmStatus.FALLBACK) return emptyList()
        // Best-effort parse: look for "items" array? Keep simple: return lines.
        return res.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6)
    }
}
