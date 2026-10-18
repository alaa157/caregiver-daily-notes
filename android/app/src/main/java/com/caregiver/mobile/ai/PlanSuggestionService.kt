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

sealed class PlanSuggestionResult {
    data class Success(val suggestion: PlanSuggestion) : PlanSuggestionResult()
    data class Failure(val reasonCode: String) : PlanSuggestionResult()
}

class PlanSuggestionService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
    private val allowedActions: Set<String> = DEFAULT_ALLOWED_ACTIONS,
) {
    private val json = Json { ignoreUnknownKeys = false }

    /**
     * Partial acceptance is intentional: invalid individual changes are dropped
     * while valid ones are kept. The whole payload is still rejected when
     * proposedMedications is non-empty.
     */
    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        val withoutFence = if (trimmed.startsWith("```")) {
            val body = trimmed.removePrefix("```json").removePrefix("```").trim()
            val end = body.lastIndexOf("```")
            if (end >= 0) body.substring(0, end).trim() else body
        } else {
            trimmed
        }
        // Defensive: extract the outermost JSON object if present.
        val first = withoutFence.indexOf('{')
        val last = withoutFence.lastIndexOf('}')
        return if (first >= 0 && last > first) withoutFence.substring(first, last + 1) else withoutFence
    }

    suspend fun suggest(
        summaryText: String,
        noteIds: Set<String>,
        currentMedications: List<PlanMedication> = emptyList(),
    ): PlanSuggestionResult {
        val userPref = keyStore?.planSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        val basePrompt = Prompts.planSystemPrompt()
        val prompt = if (userPref != null) {
            basePrompt + "\nAdditional user preferences (must not override safety constraints): " + userPref
        } else {
            basePrompt
        }
        val dataBlock = summaryText + "\n\nAllowed evidence note IDs (use only these in evidenceNoteIds): " + noteIds.joinToString(", ")
        val res = client.complete(LlmRequest(prompt, dataBlock))
        if (res.status == LlmStatus.FALLBACK) return PlanSuggestionResult.Failure("llm_fallback")
        if (res.text.length > MAX_OUTPUT_CHARS) return PlanSuggestionResult.Failure("oversized")
        val cleaned = stripFences(res.text)
        val payload = try {
            json.decodeFromString(PlanSuggestionPayload.serializer(), cleaned)
        } catch (e: Exception) {
            return PlanSuggestionResult.Failure("parse_error")
        }
        // Medications are never AI-proposed: any non-empty proposed list is
        // rejected outright, regardless of the current medication list.
        if (payload.proposedMedications.isNotEmpty()) {
            return PlanSuggestionResult.Failure("medications_rejected")
        }
        // Validate each change independently; drop invalid ones, keep valid.
        val accepted = mutableListOf<PlanChange>()
        for (change in payload.changes) {
            if (accepted.size >= MAX_CHANGES) break
            if (change.actionId.isBlank() || change.actionId !in allowedActions) continue
            if (change.reason.isBlank() || change.reason.length > MAX_REASON_LENGTH) continue
            if (change.evidenceNoteIds.size > MAX_EVIDENCE_IDS) continue
            if (change.evidenceNoteIds.any { it !in noteIds }) continue
            val allowedParams = ALLOWED_PARAMS[change.actionId] ?: continue
            if (change.params.keys != allowedParams) continue
            if (change.params.any { it.value.isBlank() || it.value.length > MAX_PARAM_VALUE_LENGTH }) continue
            accepted.add(change)
        }
        if (accepted.isEmpty()) return PlanSuggestionResult.Failure("empty_after_filter")
        return PlanSuggestionResult.Success(PlanSuggestion(accepted, emptyList()))
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
