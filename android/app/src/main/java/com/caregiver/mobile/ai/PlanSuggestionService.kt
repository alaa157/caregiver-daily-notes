package com.caregiver.mobile.ai

class PlanSuggestionService(private val client: LlmClient) {
    suspend fun suggestItems(summaryText: String): List<String> {
        val res = client.complete(LlmRequest(Prompts.PLAN_SYSTEM, summaryText))
        if (res.status == LlmStatus.FALLBACK) return emptyList()
        // Best-effort parse: look for "items" array? Keep simple: return lines.
        return res.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6)
    }
}
