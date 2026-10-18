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
        if (res.text.length > MAX_OUTPUT_CHARS) return null
        val payload = try {
            json.decodeFromString(PlanSuggestionPayload.serializer(), res.text)
        } catch (e: Exception) {
            return null
        }
        if (payload.changes.size > MAX_CHANGES) return null
        // Validate actions against strict schemas.
        for (change in payload.changes) {
            if (change.actionId.isBlank() || change.actionId !in allowedActions) return null
            if (change.reason.isBlank() || change.reason.length > MAX_REASON_LENGTH) return null
            if (change.evidenceNoteIds.size > MAX_EVIDENCE_IDS) return null
            for (id in change.evidenceNoteIds) {
                if (id !in noteIds) return null
            }
            val allowedParams = ALLOWED_PARAMS[change.actionId] ?: return null
            if (change.params.keys != allowedParams) return null
            for ((k, v) in change.params) {
                if (v.isBlank() || v.length > MAX_PARAM_VALUE_LENGTH) return null
            }
        }
        // Medications are never AI-proposed: any non-empty proposed list is
        // rejected outright, regardless of the current medication list.
        if (payload.proposedMedications.isNotEmpty()) {
            return null
        }
        return PlanSuggestion(payload.changes, emptyList())
    }

    companion object {
        val DEFAULT_ALLOWED_ACTIONS = setOf(
            "monitor",
            "adjust_routine",
            "review_with_clinician",
            "add_safety_measure",
            "schedule_followup",
        )

        // Strict per-action parameter allowlists. Empty means the action takes
        // no parameters; any unexpected key is rejected.
        val ALLOWED_PARAMS: Map<String, Set<String>> = mapOf(
            "monitor" to emptySet(),
            "adjust_routine" to emptySet(),
            "review_with_clinician" to emptySet(),
            "add_safety_measure" to emptySet(),
            "schedule_followup" to emptySet(),
        )

        const val MAX_CHANGES = 20
        const val MAX_EVIDENCE_IDS = 50
        const val MAX_REASON_LENGTH = 500
        const val MAX_PARAM_VALUE_LENGTH = 200
        const val MAX_OUTPUT_CHARS = 100_000
    }
}
