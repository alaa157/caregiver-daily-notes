package com.caregiver.mobile.ai

import kotlin.text.RegexOption
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

    suspend fun suggest(summaryText: String): PlanSuggestionResult {
        if (summaryText.isBlank()) {
            return PlanSuggestionResult.Failure("empty_after_filter")
        }
        val userPref = keyStore?.planSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        // The caregiver's preferences go before the fixed prompt, never after:
        // the last instruction in a system message carries the most weight, so
        // a preference that follows the rules could talk the model out of them.
        val prompt = Prompts.withPreferences(Prompts.planSystemPrompt(), userPref)
        // The model sees the finished summary only. Note IDs are not sent:
        // it cannot see the raw notes, so whatever attribution it returned
        // would be decorative, and the payload contract drops it below.
        val dataBlock = summaryText
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
            // The proposedMedications lock covers the structured field, but
            // the reason is free text and params are already allowlisted to
            // empty sets — so the reason is screened for medication
            // directives ("Consider doubling her insulin") too.
            if (mentionsMedicationChange(change.reason)) continue
            // Evidence IDs are decorative: the model only receives the
            // finished summary, never the notes, so any IDs it emits cannot
            // be attributed. Nothing downstream may show them — drop.
            if (change.evidenceNoteIds.isNotEmpty()) continue
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
        const val MAX_REASON_LENGTH = 500
        const val MAX_PARAM_VALUE_LENGTH = 200
        const val MAX_OUTPUT_CHARS = 100_000

        // Free text can carry what the structured fields cannot: after the
        // params allowlists, the reason is the only unvalidated channel, so
        // it is screened for medication directives. Deliberately
        // conservative — a false positive drops one suggestion, a miss would
        // leak dosage advice into the plan.
        private val DOSE_WITH_UNIT = Regex(
            "[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?\\s*" +
                "(?:mg|mcg|milligram|ml|milliliter|iu|units?|doses?|tablets?|pills?|capsules?" +
                "|drops?|puffs?|ملغ|ميكروجرام|مليلتر|وحد|جرع|قرص|أقرص|حبوب|حبة|حبت|قطرة|بخاخ)",
            RegexOption.IGNORE_CASE,
        )

        // Change-intent verbs co-occurring with a medication noun ("doubling
        // her insulin", "زد الأنسولين"). Neither alone is enough; word
        // boundaries misbehave across scripts, so these are substring stems
        // and the co-occurrence test keeps false positives rare.
        private val MEDICATION_NOUN = Regex(
            "insulin|doses?|dosage|medications?|medicines?|pills?|tablets?|capsules?" +
                "|prescriptions?|drugs?|supplements?|نسولين|جرع|أقرص|قرص|حبت|حبوب|حبة|أدوي|دواء|علاج",
            RegexOption.IGNORE_CASE,
        )
        private val CHANGE_VERB = Regex(
            "doubl|halv|trip|increas|decreas|reduc|lower|rais|boost|cut|drop|stop|start|resume|skip|add|titrat" +
                "|ضاعف|زد|زيادة|زود|خفض|خفّض|خفف|تخفيف|قلل|أوقف|وقف|توقف|ابدأ|بدأ|استئناف|استأنف|أضف|ارفع|رفع",
            RegexOption.IGNORE_CASE,
        )

        internal fun mentionsMedicationChange(reason: String): Boolean =
            DOSE_WITH_UNIT.containsMatchIn(reason) ||
                (
                    MEDICATION_NOUN.containsMatchIn(reason) &&
                        CHANGE_VERB.containsMatchIn(reason)
                )
    }
}
