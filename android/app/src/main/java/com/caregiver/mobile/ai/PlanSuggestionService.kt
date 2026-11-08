package com.caregiver.mobile.ai

import kotlin.text.RegexOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

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
    /** UI language of the generated reason text ("ar"/"en"). */
    private val language: String = "en",
) {
    /**
     * Unknown keys are tolerated, like in the summary payload: the gate on
     * a plan change is the per-change validation below (catalog action,
     * exact params, reason, no medications), not the json shape.
     */
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun suggest(summaryText: String): PlanSuggestionResult {
        if (summaryText.isBlank()) {
            return PlanSuggestionResult.Failure("empty_after_filter")
        }
        if (summaryText.length > MAX_INPUT_CHARS) {
            return PlanSuggestionResult.Failure("oversized")
        }
        val userPref = keyStore?.planSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        // The caregiver's preferences go before the fixed prompt, never after:
        // the last instruction in a system message carries the most weight, so
        // a preference that follows the rules could talk the model out of them.
        val prompt = Prompts.withPreferences(Prompts.planSystemPrompt(language), userPref)
        // The model sees the finished summary only. Note IDs are not sent:
        // it cannot see the raw notes, so whatever attribution it returned
        // would be decorative, and the payload contract drops it below.
        val dataBlock = summaryText
        // Native json mode keeps the provider from wrapping the answer in
        // prose; the shared reader strips whatever wrapper is left and spends
        // one repair retry when the payload still does not parse.
        val request = LlmRequest(prompt, dataBlock, jsonMode = true)
        val reply = LlmJson.decode(client, request, MAX_REPLY_CHARS) { text ->
            runCatching { decodePayload(text) }.getOrNull()
        }
        val payload: PlanSuggestionPayload
        val replyStatus: LlmStatus
        when (reply) {
            is LlmJson.Reply.Parsed -> {
                payload = reply.value
                replyStatus = reply.status
            }
            is LlmJson.Reply.Unavailable -> return PlanSuggestionResult.Failure(reply.error ?: "unavailable")
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
        val seen = mutableSetOf<String>()
        var hadInvalidAction = false
        var hadInvalidEvidence = false
        for (change in payload.changes) {
            if (accepted.size >= MAX_CHANGES) break
            if (change.actionId.isBlank() || change.actionId !in allowedActions) {
                hadInvalidAction = true
                continue
            }
            if (change.reason.isBlank() || change.reason.length > MAX_REASON_LENGTH) {
                hadInvalidAction = true
                continue
            }
            // The proposedMedications lock covers the structured field, but
            // the reason is free text and params are already allowlisted to
            // empty sets — so the reason is screened for medication
            // directives ("Consider doubling her insulin") too.
            if (mentionsMedicationChange(change.reason)) {
                hadInvalidAction = true
                continue
            }
            // Evidence IDs are decorative: the model only receives the
            // finished summary, never the notes, so any IDs it emits cannot
            // be attributed. Nothing downstream may show them — drop.
            if (change.evidenceNoteIds.isNotEmpty()) {
                hadInvalidEvidence = true
                continue
            }
            val allowedParams = ALLOWED_PARAMS[change.actionId]
            if (allowedParams == null || change.params.keys != allowedParams ||
                change.params.any { it.value.isBlank() || it.value.length > MAX_PARAM_VALUE_LENGTH } ||
                !paramsAreWellFormed(change)
            ) {
                hadInvalidAction = true
                continue
            }
            // A model that repeats the same change must not turn it into
            // three plan items.
            if (!seen.add(changeKey(change))) continue
            accepted.add(change)
        }
        if (accepted.isEmpty()) {
            val reason = when {
                hadInvalidEvidence -> "invalid_evidence"
                hadInvalidAction -> "invalid_action"
                else -> "empty_after_filter"
            }
            return PlanSuggestionResult.Failure(reason)
        }
        return PlanSuggestionResult.Success(PlanSuggestion(accepted, emptyList()), replyStatus)
    }

    companion object {
        // decodePayload is a companion helper; it needs its own Json
        // instance rather than the service instance's field.
        private val json = Json { ignoreUnknownKeys = true }
        val DEFAULT_ALLOWED_ACTIONS = setOf(
            "monitor",
            "adjust_routine",
            "review_with_clinician",
            "add_safety_measure",
            "schedule_followup",
        )

        /**
         * Strict per-action parameter allowlists: the action's parameters
         * must match exactly, and any unexpected key is rejected.
         *
         * A follow-up without a date is not a follow-up, so
         * [SCHEDULE_FOLLOWUP] carries the day it refers to. The other actions
         * describe a routine, a watch item or a review, and their detail
         * belongs in the reason, not in a parameter the plan cannot use.
         */
        val ALLOWED_PARAMS: Map<String, Set<String>> = mapOf(
            "monitor" to emptySet(),
            "adjust_routine" to emptySet(),
            "review_with_clinician" to emptySet(),
            "add_safety_measure" to emptySet(),
            SCHEDULE_FOLLOWUP to setOf(DATE_PARAM),
        )

        /** A follow-up action; it carries the day the follow-up is for. */
        const val SCHEDULE_FOLLOWUP = "schedule_followup"

        /** The parameter that holds the follow-up date (ISO, yyyy-mm-dd). */
        const val DATE_PARAM = "date"

        const val MAX_CHANGES = 20
        const val MAX_REASON_LENGTH = 500
        const val MAX_PARAM_VALUE_LENGTH = 200

        /**
         * True when the change's parameters carry what the action needs: a
         * follow-up needs a real date, every other action has no parameters
         * to check.
         */
        internal fun paramsAreWellFormed(change: PlanChange): Boolean {
            if (change.actionId != SCHEDULE_FOLLOWUP) return true
            val date = change.params[DATE_PARAM] ?: return false
        return runCatching { java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now()) }.getOrDefault(false)
        }

        private fun decodePayload(text: String): PlanSuggestionPayload {
            val root = json.parseToJsonElement(text).jsonObject
            val changes = root["changes"] as? JsonArray ?: JsonArray(emptyList())
            val parsedChanges = changes.mapNotNull { element ->
                runCatching {
                    val item = element.jsonObject
                    val action = (item["actionId"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
                    val reason = (item["reason"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
                    val paramsObject = item["params"] as? JsonObject ?: JsonObject(emptyMap())
                    val params = paramsObject.mapValues { (_, value) ->
                        (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                            ?: return@runCatching null
                    }
                    val evidence = (item["evidenceNoteIds"] as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    } ?: emptyList()
                    PlanChange(action, params, reason, evidence)
                }.getOrNull()
            }
            val medications = (root["proposedMedications"] as? JsonArray).orEmpty()
            // Any proposed item, even malformed, keeps the medication lock
            // fail-closed rather than disappearing during permissive parsing.
            val proposed = medications.map { PlanMedication("invalid", "invalid", "invalid") }
            return PlanSuggestionPayload(parsedChanges, proposed)
        }

        /**
         * The identity of a change, so the same suggestion repeated by the
         * model is stored once.
         */
        internal fun changeKey(change: PlanChange): String = buildString {
            append(change.actionId.lowercase())
            append('|')
            change.params.entries.sortedBy { it.key }.forEach { (key, value) ->
                append(key.lowercase()).append('=').append(value.trim().lowercase()).append(';')
            }
            append('|').append(change.reason.trim().lowercase())
        }

        /**
         * The plan line shown to the caregiver: the model's reason only, never
         * the action id or `params=` syntax. A follow-up keeps its date in a
         * readable parenthetical.
         */
        internal fun planItemText(change: PlanChange): String {
            val reason = change.reason.trim()
            val date = change.params[DATE_PARAM]
            return if (change.actionId == SCHEDULE_FOLLOWUP && date != null) {
                "$reason ($date)"
            } else {
                reason
            }
        }

        // Free text can carry what the structured fields cannot: after the
        // params allowlists, the reason is the only unvalidated channel, so
        // it is screened for medication directives. Deliberately
        // conservative — a false positive drops one suggestion, a miss would
        // leak dosage advice into the plan.
        private val DOSE_WITH_UNIT = Regex(
            "[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?\\s*" +
                "(?:mg|mcg|milligram|ml|milliliter|iu|units?|doses?|tablets?|pills?|capsules?" +
                "|drops?|puffs?|mg|مجم|ملغ|ميكروجرام|مليلتر|وحد|جرع|قرص|اقراص|حبوب|حبه|حبت|قطره|بخاخ)",
            RegexOption.IGNORE_CASE,
        )

        // Change-intent verbs co-occurring with a medication noun ("doubling
        // her insulin", "زد الأنسولين"). Neither alone is enough; word
        // boundaries misbehave across scripts, so these are substring stems
        // and the co-occurrence test keeps false positives rare.
        private val MEDICATION_NOUN = Regex(
            "(?<![\\p{L}])(?:insulin|paracetamol|acetaminophen|doses?|dosage|medications?|medicines?|pills?|tablets?|capsules?" +
                "|prescriptions?|drugs?|supplements?|نسولين|جرع|اقراص|قرص|حبت|حبوب|حبه|ادويه|دوا|دواء|علاج)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        private val CHANGE_VERB = Regex(
            "(?<![\\p{L}])(?:double|doubles|doubling|halve|halves|halving|triples?|tripling|increase|increases|increasing|decrease|decreases|decreasing|reduce|reduces|reduced|lower|lowers|raise|raises|boost|cut|cuts|drop|drops|stop|stops|start|starts|resume|skip|add|adjust|change|swap|discontinue|give)(?![\\p{L}])" +
                "|ضاعف|زد|زيادة|زود|خفض|خفف|تخفيف|قلل|اوقف|وقف|توقف|ابدأ|بدأ|استئناف|استأنف|اضف|ارفع|رفع",
            RegexOption.IGNORE_CASE,
        )

        internal fun mentionsMedicationChange(reason: String): Boolean {
            val normalized = ArabicNormalizer.normalize(reason)
            return DOSE_WITH_UNIT.containsMatchIn(normalized) ||
                (
                    MEDICATION_NOUN.containsMatchIn(normalized) &&
                        CHANGE_VERB.containsMatchIn(normalized)
                )
        }
    }
}
