package com.caregiver.mobile.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlanMedication(val name: String, val dose: String, val schedule: String)

@Serializable
data class PlanChange(
    val actionId: String,
    val params: Map<String, String> = emptyMap(),
    val reason: String,
    val evidenceNoteIds: List<String> = emptyList(),
)

@Serializable
data class PlanSuggestionPayload(
    val changes: List<PlanChange> = emptyList(),
    val proposedMedications: List<PlanMedication> = emptyList(),
)

data class PlanSuggestion(
    val changes: List<PlanChange>,
    val proposedMedications: List<PlanMedication>,
)

class PlanSuggestionService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
    private val allowedActions: Set<String> = DEFAULT_ALLOWED_ACTIONS,
) {
    private val json = Json { ignoreUnknownKeys = false }

    suspend fun suggest(
        summaryText: String,
        noteIds: Set<String>,
        currentMedications: List<PlanMedication> = emptyList(),
    ): PlanSuggestion? {
        val prompt = keyStore?.planSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
            ?: Prompts.PLAN_SYSTEM
        val res = client.complete(LlmRequest(prompt, summaryText))
        if (res.status == LlmStatus.FALLBACK) return null
        val payload = try {
            json.decodeFromString(PlanSuggestionPayload.serializer(), res.text)
        } catch (e: Exception) {
            return null
        }
        // Validate actions
        for (change in payload.changes) {
            if (change.actionId.isBlank() || change.actionId !in allowedActions) return null
            if (change.reason.isBlank()) return null
            for (id in change.evidenceNoteIds) {
                if (id !in noteIds) return null
            }
        }
        // Enforce medication prohibition: proposed must equal current when current provided
        if (currentMedications.isNotEmpty() && payload.proposedMedications != currentMedications) {
            return null
        }
        return PlanSuggestion(payload.changes, payload.proposedMedications)
    }

    companion object {
        val DEFAULT_ALLOWED_ACTIONS = setOf(
            "monitor",
            "adjust_routine",
            "review_with_clinician",
            "add_safety_measure",
            "schedule_followup",
        )
    }
}
