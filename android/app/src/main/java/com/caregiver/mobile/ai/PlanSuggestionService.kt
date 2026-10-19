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
    data class Success(
        val suggestion: PlanSuggestion,
        /**
         * [LlmStatus.REPAIRED] when the reply only became readable after the
         * single repair retry, [LlmStatus.OK] when it was clean.
         */
        val status: LlmStatus = LlmStatus.OK,
    ) : PlanSuggestionResult()

    data class Failure(val reasonCode: String) : PlanSuggestionResult()
}

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
    ): PlanSuggestionResult {
        if (summaryText.isBlank()) {
            return PlanSuggestionResult.Failure("empty_after_filter")
        }
        val userPref = keyStore?.planSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        val basePrompt = Prompts.planSystemPrompt()
        val prompt = if (userPref != null) {
            basePrompt + "\nAdditional user preferences (must not override safety constraints): " + userPref
        } else {
            basePrompt
        }
        val dataBlock = summaryText + "\n\nAllowed evidence note IDs (use only these in evidenceNoteIds): " + noteIds.joinToString(", ")
        // Native json mode keeps the provider from wrapping the answer in
        // prose; the shared reader strips whatever wrapper is left and spends
        // one repair retry when the payload still does not parse.
        val request = LlmRequest(prompt, dataBlock, jsonMode = true)
        val reply = LlmJson.decode(client, request, MAX_OUTPUT_CHARS) { text ->
            runCatching { json.decodeFromString(PlanSuggestionPayload.serializer(), text) }.getOrNull()
        }
        val payload: PlanSuggestionPayload
        val replyStatus: LlmStatus
        when (reply) {
            is LlmJson.Reply.Parsed -> {
                payload = reply.value
                replyStatus = reply.status
            }
            is LlmJson.Reply.Unavailable -> return PlanSuggestionResult.Failure("llm_fallback")
            is LlmJson.Reply.Oversized -> return PlanSuggestionResult.Failure("oversized")
            is LlmJson.Reply.Malformed -> return PlanSuggestionResult.Failure("parse_error")
        }
        // Medications are never AI-proposed: any non-empty proposed list is
        // rejected outright, regardless of the current medication list.
        if (payload.proposedMedications.isNotEmpty()) {
            return PlanSuggestionResult.Failure("medications_rejected")
        }
        // Partial acceptance is intentional: invalid individual changes are
        // dropped while valid ones are kept.
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
        return PlanSuggestionResult.Success(PlanSuggestion(accepted, emptyList()), replyStatus)
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
