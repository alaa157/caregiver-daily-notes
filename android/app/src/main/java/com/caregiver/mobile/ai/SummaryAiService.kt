package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.combinedText
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ModelObs(
    val text: String = "",
    val claim: String = "",
    val noteId: String = "",
    val quote: String = "",
    val evidence: String = "",
    // Typed evidence (see StructuredEvidence). Blank on legacy payloads.
    val evidenceType: String = "",
    val structuredField: String = "",
    val structuredValue: String = "",
)

@Serializable
private data class ModelUnc(val topic: String = "", val detail: String = "")

@Serializable
private data class ModelSummary(
    val observations: List<ModelObs> = emptyList(),
    val uncertainties: List<ModelUnc> = emptyList(),
    // The model may RAISE urgency (README contract: raise, never lower);
    // missing/older payloads parse as false.
    val urgent: Boolean = false,
)

@Serializable
private data class RejectedFeedback(
    val claim: String,
    val reason: String,
    val missingWords: List<String> = emptyList(),
)

@Serializable
private data class GroundingRetryPayload(
    val rejected: List<RejectedFeedback>,
    val instruction: String,
)

private data class RejectedItem(
    val claim: String,
    val noteId: String,
    val reason: String,
    val missingWords: List<String> = emptyList(),
)

private data class ValidationOutcome(
    val validObservations: List<ModelObs>,
    val rejected: List<RejectedItem>,
)

sealed class SummaryAiResult {
    data class Success(
        val summary: SummaryDto,
        /**
         * [LlmStatus.REPAIRED] when the reply only became readable after the
         * single repair retry, [LlmStatus.OK] when it was clean.
         */
        val status: LlmStatus = LlmStatus.OK,
    ) : SummaryAiResult()

    data class Failure(val redFlags: List<String>, val reasonCode: String) : SummaryAiResult()
}

class SummaryAiService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** UI language of the generated human-readable summary text ("ar"/"en"). */
    private val language: String = "en",
) {
    suspend fun summarize(
        recipientId: String,
        periodDays: Int,
        notes: List<NoteDto>,
        signals: List<SignalDto>,
        endInclusive: LocalDate,
    ): SummaryAiResult {
        // Window here as well as at the fetch: the model and the deterministic
        // flags must never see data outside the selected period, even if a
        // caller (or a backend that ignores from/to) hands over more history.
        // Recipient isolation: only records belonging to the requested
        // recipient may reach the model or safety evaluation.
        val scopedNotes = notes.filter { it.recipientId == recipientId }
        val scopedSignals = signals.filter { it.recipientId == recipientId }
        val windowedNotes = scopedNotes.filter { SummaryWindow.contains(it.date, endInclusive, periodDays) }
        val windowedSignals = scopedSignals.filter { SummaryWindow.contains(it.date, endInclusive, periodDays) }
        // Addenda carry corrections and late details. The backend's signal
        // text may not include them yet, so append any addendum the signal
        // text is missing before the deterministic rules run.
        val addendaByNoteId = windowedNotes.associate { it.id to it.addenda }
        val signalsWithAddenda = windowedSignals.map { signal ->
            val missingAddenda = addendaByNoteId[signal.noteId].orEmpty()
                .map { it.text.trim() }
                .filter { it.isNotEmpty() && !signal.text.contains(it) }
            if (missingAddenda.isEmpty()) {
                signal
            } else {
                signal.copy(
                    text = (signal.text.trimEnd() + "\n" + missingAddenda.joinToString("\n")).trim(),
                )
            }
        }
        val redFlags = SafetySignalEvaluator.evaluate(signalsWithAddenda)
        if (windowedNotes.isEmpty()) {
            return SummaryAiResult.Failure(redFlags, "no_source_data")
        }
        // JSON escaping, not text delimiters: note text is untrusted and may
        // contain "[/NOTE]" or a forged "[NOTE ...]" block. Serialized as a
        // string value it can never terminate the data payload. Addenda are
        // folded into the same text so the model and the quote check see them.
        val dataBlock = PromptNotes.dataBlock(
            windowedNotes.map { note -> PromptNote(id = note.id, date = note.date, text = note.aiSourceText()) },
        )
        if (dataBlock.length > MAX_INPUT_CHARS) {
            return SummaryAiResult.Failure(redFlags, "oversized")
        }
        val userPref = keyStore?.summarySystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        // The caregiver's preferences go before the fixed prompt, never after:
        // the last instruction in a system message carries the most weight, so
        // a preference that follows the rules could talk the model out of them.
        val prompt = Prompts.withPreferences(Prompts.summarySystemPrompt(language), userPref)
        // Native json mode keeps the provider from wrapping the answer in
        // prose; the shared reader strips whatever wrapper is left and spends
        // one repair retry when the payload still does not parse.
        val request = com.caregiver.mobile.ai.LlmRequest(prompt, dataBlock, jsonMode = true)
        val reply = LlmJson.decode(client, request, MAX_REPLY_CHARS) { text ->
            runCatching { json.decodeFromString(ModelSummary.serializer(), text) }.getOrNull()
        }
        val parsed: ModelSummary
        val replyStatus: LlmStatus
        when (reply) {
            is LlmJson.Reply.Parsed -> {
                parsed = reply.value
                replyStatus = reply.status
            }
            // Keep the provider's own reason (invalid_key, rate_limit,
            // http_400, ...) so the UI can explain the fix instead of
            // collapsing every failure into a generic "unavailable".
            is LlmJson.Reply.Unavailable ->
                return SummaryAiResult.Failure(redFlags, reply.error ?: "unavailable")
            is LlmJson.Reply.Oversized ->
                return SummaryAiResult.Failure(redFlags, reply.error ?: "oversized")
            is LlmJson.Reply.Malformed ->
                return SummaryAiResult.Failure(redFlags, reply.error ?: "malformed_output")
        }
        val notesById = windowedNotes.associateBy { it.id }
        // Normalized source texts per evidence type: the original note text
        // (NOTE_TEXT), each addendum on its own (ADDENDUM), and the legacy
        // combined text for payloads that declare no evidence type. The
        // rendered structured-observations line is deliberately in none of
        // them: it is model-visible data, never quotable evidence.
        val noteTextsById = windowedNotes.associate { it.id to ArabicNormalizer.normalizeForEvidence(it.text) }
        val addendumTextsById = windowedNotes.associate {
            it.id to it.addenda.map { addendum -> ArabicNormalizer.normalizeForEvidence(addendum.text) }
        }
        val combinedTextsById = windowedNotes.associate { it.id to ArabicNormalizer.normalizeForEvidence(it.combinedText()) }
        // Validate and reject unsupported observations; keep only grounded ones.
        // A claim must be non-empty, cite an existing note, and carry evidence
        // that checks out for its declared type: a verbatim quote inside the
        // cited text (NOTE_TEXT / ADDENDUM / legacy combined), or a structured
        // field whose name and value match the note itself (STRUCTURED_FIELD).
        // String matching alone is never treated as proof of truth: any
        // observation that survives these checks is still labelled unverified.
        var finalParsed = parsed
        var finalOutcome = validateObservations(
            parsed.observations,
            notesById,
            noteTextsById,
            addendumTextsById,
            combinedTextsById,
        )
        var finalStatus = replyStatus
        var usedGroundingRetry = false
        // Single grounding retry: only when the first reply was mostly
        // rejected (nothing survived, or rejected >= valid). At most one
        // extra model call, inside the caller's existing deadline.
        if (finalOutcome.rejected.isNotEmpty() &&
            (finalOutcome.validObservations.isEmpty() ||
                finalOutcome.rejected.size >= finalOutcome.validObservations.size)
        ) {
            val feedbackItems = finalOutcome.rejected.take(GROUNDING_RETRY_MAX_ITEMS).map { rejected ->
                RejectedFeedback(
                    claim = rejected.claim.take(GROUNDING_RETRY_MAX_CLAIM_CHARS),
                    reason = rejected.reason,
                    missingWords = rejected.missingWords,
                )
            }
            val feedbackJson = json.encodeToString(
                GroundingRetryPayload.serializer(),
                GroundingRetryPayload(feedbackItems, GROUNDING_RETRY_INSTRUCTION),
            )
            val retryRequest = request.copy(dataBlock = request.dataBlock + "\n\n" + feedbackJson)
            val retryReply = LlmJson.decode(client, retryRequest, MAX_REPLY_CHARS) { text ->
                runCatching { json.decodeFromString(ModelSummary.serializer(), text) }.getOrNull()
            }
            when (retryReply) {
                is LlmJson.Reply.Parsed -> {
                    val retryOutcome = validateObservations(
                        retryReply.value.observations,
                        notesById,
                        noteTextsById,
                        addendumTextsById,
                        combinedTextsById,
                    )
                    // Keep the retry only when it yields more grounded findings.
                    if (retryOutcome.validObservations.size > finalOutcome.validObservations.size) {
                        finalParsed = retryReply.value
                        finalOutcome = retryOutcome
                        usedGroundingRetry = true
                    }
                }
                is LlmJson.Reply.Unavailable,
                is LlmJson.Reply.Oversized,
                is LlmJson.Reply.Malformed,
                -> {
                    // Retry must never make a previously successful summary fail.
                    // Fall through with the first-pass outcome.
                }
            }
        }
        if (usedGroundingRetry) {
            finalStatus = LlmStatus.REPAIRED
        }
        val validObservations = finalOutcome.validObservations

        val evidence = validObservations.map {
            SummaryDto.EvidenceDto(
                noteId = it.noteId,
                quote = it.quote,
                evidenceType = it.evidenceType.ifBlank { EvidenceType.NOTE_TEXT.name },
                structuredField = it.structuredField,
                structuredValue = it.structuredValue,
            )
        }
        val uncertainties = finalParsed.uncertainties.map { SummaryDto.UncertaintyDto(topic = it.topic, detail = it.detail) }.toMutableList()
        if (finalOutcome.rejected.isNotEmpty()) {
            uncertainties += SummaryDto.UncertaintyDto("invalid_evidence", "")
        }
        if (validObservations.isNotEmpty()) {
            uncertainties += SummaryDto.UncertaintyDto("unverified_claim", "")
        }
        val text = validObservations.joinToString(" ") { observation ->
            val claim = observation.text.trim()
            if (claim.isNotEmpty() && claim.last() in SENTENCE_ENDINGS) claim else "$claim."
        }
        // A structurally valid response with no surviving observations is a
        // legitimate empty result, not an AI failure: the model found no
        // supported observations. Only reject when observations were present
        // but none survived validation.
        if (finalParsed.observations.isNotEmpty() && validObservations.isEmpty()) {
            return SummaryAiResult.Failure(redFlags, "invalid_evidence")
        }

        // The model may only ADD urgency — deterministic flags are never
        // removed or reordered; the flag id matches the backend mirror.
        val mergedFlags = if (finalParsed.urgent) redFlags + "MODEL_URGENT" else redFlags
        return SummaryAiResult.Success(
            SummaryDto(
                id = "",
                recipientId = recipientId,
                periodDays = periodDays,
                text = text,
                redFlags = mergedFlags,
                evidence = evidence,
                uncertainties = uncertainties,
            ),
            status = finalStatus,
        )
    }

    private fun validateObservations(
        observations: List<ModelObs>,
        notesById: Map<String, NoteDto>,
        noteTextsById: Map<String, String>,
        addendumTextsById: Map<String, List<String>>,
        combinedTextsById: Map<String, String>,
    ): ValidationOutcome {
        val validObservations = mutableListOf<ModelObs>()
        val rejected = mutableListOf<RejectedItem>()
        // Hard bound so a model that ignores the prompt (or a custom user
        // prompt) cannot turn the Overview and "Important notes" back into a
        // per-note dump. Keep in sync with Prompts.SUMMARY_SYSTEM.
        for (obs in observations.take(MAX_OBSERVATIONS)) {
            val claim = if (obs.claim.isNotBlank()) obs.claim else obs.text
            val rawQuote = if (obs.quote.isNotBlank()) obs.quote else obs.evidence
            if (claim.isBlank()) {
                rejected += RejectedItem(claim, obs.noteId, "empty_claim")
                continue
            }
            val note = notesById[obs.noteId]
            if (note == null) {
                rejected += RejectedItem(claim, obs.noteId, "unknown_note")
                continue
            }
            val evidenceType = StructuredEvidence.typeOf(obs.evidenceType, obs.structuredField)
            if (evidenceType == null) {
                rejected += RejectedItem(claim, obs.noteId, "unknown_evidence_type")
                continue
            }
            if (evidenceType == EvidenceType.STRUCTURED_FIELD) {
                // The model cites a recorded field by name and value, in
                // its own words in the claim. Both halves are checked
                // against the note: the value must be the recorded one,
                // and the claim must actually talk about that field/value.
                // The stored quote is rebuilt from the note's own values,
                // never copied from the model.
                val field = StructuredEvidence.canonicalField(obs.structuredField)
                if (field == null || !StructuredEvidence.matches(note, field, obs.structuredValue)) {
                    rejected += RejectedItem(claim, obs.noteId, "structured_mismatch")
                    continue
                }
                val actual = StructuredEvidence.actualValue(note, field)
                if (!StructuredEvidence.claimAligns(claim, field, actual)) {
                    rejected += RejectedItem(claim, obs.noteId, "structured_mismatch")
                    continue
                }
                validObservations += ModelObs(
                    text = claim,
                    noteId = obs.noteId,
                    quote = StructuredEvidence.canonicalQuote(note, field),
                    evidenceType = EvidenceType.STRUCTURED_FIELD.name,
                    structuredField = field,
                    structuredValue = actual,
                )
                continue
            }
            // Quote-based evidence. A declared type is strict about where
            // the quote may come from; a legacy payload without a type may
            // quote the combined text, as before.
            val quote = ArabicNormalizer.normalizeForEvidence(rawQuote)
            val quoteContained = when {
                obs.evidenceType.isBlank() -> combinedTextsById[obs.noteId]?.contains(quote) == true
                evidenceType == EvidenceType.ADDENDUM ->
                    addendumTextsById[obs.noteId]?.any { it.contains(quote) } == true
                else -> noteTextsById[obs.noteId]?.contains(quote) == true
            }
            if (quote.isEmpty() || !quoteContained) {
                rejected += RejectedItem(claim, obs.noteId, "quote_not_in_note")
                continue
            }
            // The prompt requires the claim to stay in the quote's language
            // and script, but a model can disobey. When the two scripts are
            // not comparable the lexical check cannot judge the claim, and
            // dropping the observation would lose a good summary for a reason
            // that says nothing about the model. ClaimGrounding still checks
            // numbers and polarity first. The quote itself was verified
            // against the note above, and the observation stays labelled
            // unverified.
            // Only an UNSUPPORTED verdict rejects the observation.
            // NOT_COMPARABLE means the claim and quote are in different
            // scripts, so the lexical check cannot judge the claim at
            // all — rejecting it would drop every observation of a
            // perfectly good cross-language summary, which is exactly
            // what ClaimGrounding's contract forbids. The quote itself
            // was verified against the note above, and the observation
            // stays labelled unverified.
            val explanation = ClaimGrounding.explain(claim, rawQuote)
            if (explanation.verdict == ClaimGrounding.Verdict.UNSUPPORTED) {
                rejected += RejectedItem(claim, obs.noteId, explanation.reason.name, explanation.missingWords)
                continue
            }
            validObservations += ModelObs(
                text = claim,
                noteId = obs.noteId,
                quote = rawQuote,
                evidenceType = evidenceType.name,
            )
        }
        return ValidationOutcome(validObservations, rejected)
    }

    companion object {
        private val SENTENCE_ENDINGS = setOf('.', '!', '?', '؟', '。', '！', '…')

        /**
         * Upper bound on issued findings so a summary stays a summary. The
         * model is already told "at most 6" in [Prompts.SUMMARY_SYSTEM]; this
         * is the enforcement point when it ignores that instruction.
         */
        const val MAX_OBSERVATIONS = 6

        private const val GROUNDING_RETRY_MAX_ITEMS = 6
        private const val GROUNDING_RETRY_MAX_CLAIM_CHARS = 200
        private const val GROUNDING_RETRY_INSTRUCTION =
            "The following claims were rejected during validation. " +
                "Rewrite each claim using only words from the cited note, " +
                "copy quotes verbatim from that note, " +
                "and drop any finding you cannot support."
    }
}

/** Render structured note fields as explicit source data for the model. The
 * rendered line is how the model learns the values; it is never quotable
 * evidence — structured citations use evidenceType STRUCTURED_FIELD with
 * the field name and value, validated against the NoteDto itself. */
private fun NoteDto.aiSourceText(): String = buildString {
    append(combinedText())
    append("\nStructured observations: mood=").append(mood)
    append("; appetite=").append(appetite)
    append("; sleep=").append(sleep)
    append("; mobility=").append(mobility)
    append("; medicationTaken=").append(medicationTaken)
    append("; pain=").append(pain).append("/10")
    append("; fall=").append(fall)
}
