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

sealed class SummaryAiResult {
    data class Success(
        val summary: SummaryDto,
        /**
         * [LlmStatus.REPAIRED] when the reply only became readable or grounded
         * after the single repair retry (malformed-JSON or grounding),
         * [LlmStatus.OK] when it was clean.
         */
        val status: LlmStatus = LlmStatus.OK,
    ) : SummaryAiResult()

    data class Failure(val redFlags: List<String>, val reasonCode: String) : SummaryAiResult()
}

/** Outcome of the one authoritative grounding validation for a parsed reply. */
private data class ObservationEvaluation(
    val validObservations: List<ModelObs>,
    val failures: List<GroundingFailure>,
)

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
        // Validate and reject unsupported observations. A claim must be
        // non-empty, cite an existing note, and carry evidence that checks
        // out for its declared type: a verbatim quote inside the cited text
        // (NOTE_TEXT / ADDENDUM / legacy combined), or a structured field
        // whose name and value match the note itself (STRUCTURED_FIELD).
        // String matching alone is never treated as proof of truth: any
        // observation that survives these checks is still labelled unverified.
        //
        // The same evaluation runs for the initial reply and for the single
        // grounding-repair reply, so a repair can never pass under weaker
        // rules than the original. When the initial reply is valid JSON but
        // any claim fails grounding, the model gets exactly one repair
        // attempt carrying the structured failures; the repair is validated
        // against the original notes, never against the rejected reply.
        val evaluation = evaluateObservations(
            parsed, notesById, noteTextsById, addendumTextsById, combinedTextsById,
        )
        if (evaluation.failures.isNotEmpty()) {
            return groundingRepairAttempt(
                request, parsed, evaluation.failures, redFlags, recipientId, periodDays,
                notesById, noteTextsById, addendumTextsById, combinedTextsById,
            )
        }
        return buildSuccess(parsed, evaluation.validObservations, replyStatus, redFlags, recipientId, periodDays)
    }

    /**
     * The single grounding-repair attempt. Exactly one provider call, parsed
     * once, validated through [evaluateObservations] — no format-repair retry
     * and no second grounding repair inside this attempt. Provider and
     * parsing failures keep the same reason codes as the initial attempt; a
     * repair that still fails grounding is `invalid_evidence` and exposes
     * none of its claims.
     */
    private suspend fun groundingRepairAttempt(
        request: com.caregiver.mobile.ai.LlmRequest,
        rejected: ModelSummary,
        failures: List<GroundingFailure>,
        redFlags: List<String>,
        recipientId: String,
        periodDays: Int,
        notesById: Map<String, NoteDto>,
        noteTextsById: Map<String, String>,
        addendumTextsById: Map<String, List<String>>,
        combinedTextsById: Map<String, String>,
    ): SummaryAiResult {
        val repairRequest = GroundingRepair.buildRepairRequest(
            request,
            json.encodeToString(ModelSummary.serializer(), rejected),
            failures,
        )
        val repairedReply = client.complete(repairRequest)
        if (repairedReply.status == LlmStatus.FALLBACK) {
            return SummaryAiResult.Failure(redFlags, repairedReply.error ?: "unavailable")
        }
        if (repairedReply.text.length > MAX_REPLY_CHARS) {
            return SummaryAiResult.Failure(redFlags, repairedReply.error ?: "oversized")
        }
        val candidate = ModelOutputSanitizer.extractJson(repairedReply.text)
        val repairedParsed = candidate?.let { text ->
            runCatching { json.decodeFromString(ModelSummary.serializer(), text) }.getOrNull()
        } ?: return SummaryAiResult.Failure(redFlags, "malformed_output")
        val repairedEvaluation = evaluateObservations(
            repairedParsed, notesById, noteTextsById, addendumTextsById, combinedTextsById,
        )
        if (repairedEvaluation.failures.isNotEmpty()) {
            return SummaryAiResult.Failure(redFlags, "invalid_evidence")
        }
        return buildSuccess(
            repairedParsed, repairedEvaluation.validObservations, LlmStatus.REPAIRED,
            redFlags, recipientId, periodDays,
        )
    }

    /** Assembles a successful summary from observations that passed validation. */
    private fun buildSuccess(
        parsed: ModelSummary,
        validObservations: List<ModelObs>,
        replyStatus: LlmStatus,
        redFlags: List<String>,
        recipientId: String,
        periodDays: Int,
    ): SummaryAiResult.Success {
        val evidence = validObservations.map {
            SummaryDto.EvidenceDto(
                noteId = it.noteId,
                quote = it.quote,
                evidenceType = it.evidenceType.ifBlank { EvidenceType.NOTE_TEXT.name },
                structuredField = it.structuredField,
                structuredValue = it.structuredValue,
            )
        }
        val uncertainties = parsed.uncertainties.map { SummaryDto.UncertaintyDto(topic = it.topic, detail = it.detail) }.toMutableList()
        if (validObservations.isNotEmpty()) {
            uncertainties += SummaryDto.UncertaintyDto("unverified_claim", "")
        }
        val text = validObservations.joinToString(" ") { observation ->
            val claim = observation.text.trim()
            if (claim.isNotEmpty() && claim.last() in SENTENCE_ENDINGS) claim else "$claim."
        }
        // The model may only ADD urgency — deterministic flags are never
        // removed or reordered; the flag id matches the backend mirror.
        val mergedFlags = if (parsed.urgent) redFlags + "MODEL_URGENT" else redFlags
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
            status = replyStatus,
        )
    }

    companion object {
        private val SENTENCE_ENDINGS = setOf('.', '!', '?', '؟', '。', '！', '…')

        /**
         * Upper bound on issued findings so a summary stays a summary. The
         * model is already told "at most 6" in [Prompts.SUMMARY_SYSTEM]; this
         * is the enforcement point when it ignores that instruction.
         */
        const val MAX_OBSERVATIONS = 6
    }
}

/**
 * The one authoritative grounding validation for a parsed summary reply.
 * Both the initial reply and the grounding-repair reply go through this
 * function, so a repair can never be accepted under weaker rules, and the
 * only source of truth is [notesById] (the original permitted notes).
 * Returns the observations that passed plus one [GroundingFailure] per
 * dropped observation, in reply order.
 */
private fun evaluateObservations(
    parsed: ModelSummary,
    notesById: Map<String, NoteDto>,
    noteTextsById: Map<String, String>,
    addendumTextsById: Map<String, List<String>>,
    combinedTextsById: Map<String, String>,
): ObservationEvaluation {
    val validObservations = mutableListOf<ModelObs>()
    val failures = mutableListOf<GroundingFailure>()
    // Hard bound so a model that ignores the prompt (or a custom user
    // prompt) cannot turn the Overview and "Important notes" back into a
    // per-note dump. Keep in sync with Prompts.SUMMARY_SYSTEM.
    for (obs in parsed.observations.take(SummaryAiService.MAX_OBSERVATIONS)) {
        val claim = if (obs.claim.isNotBlank()) obs.claim else obs.text
        val rawQuote = if (obs.quote.isNotBlank()) obs.quote else obs.evidence
        fun failure(reason: GroundingFailureReason) = GroundingFailure(
            reason = reason,
            noteId = obs.noteId,
            claim = claim,
            evidenceType = obs.evidenceType,
            structuredField = obs.structuredField,
            structuredValue = obs.structuredValue,
        )
        if (claim.isBlank()) {
            failures += failure(GroundingFailureReason.EMPTY_CLAIM)
            continue
        }
        val note = notesById[obs.noteId]
        if (note == null) {
            failures += failure(GroundingFailureReason.UNKNOWN_NOTE)
            continue
        }
        val evidenceType = StructuredEvidence.typeOf(obs.evidenceType, obs.structuredField)
        if (evidenceType == null) {
            failures += failure(GroundingFailureReason.UNKNOWN_EVIDENCE_TYPE)
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
            if (field == null) {
                failures += failure(GroundingFailureReason.UNKNOWN_STRUCTURED_FIELD)
                continue
            }
            if (!StructuredEvidence.matches(note, field, obs.structuredValue)) {
                failures += failure(GroundingFailureReason.STRUCTURED_VALUE_MISMATCH)
                continue
            }
            val actual = StructuredEvidence.actualValue(note, field)
            if (!StructuredEvidence.claimAligns(claim, field, actual)) {
                failures += failure(GroundingFailureReason.STRUCTURED_CLAIM_MISMATCH)
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
            failures += failure(GroundingFailureReason.QUOTE_NOT_IN_EVIDENCE)
            continue
        }
        // Only an UNSUPPORTED verdict rejects the observation.
        // NOT_COMPARABLE means the claim and quote are in different
        // scripts, so the lexical check cannot judge the claim at
        // all — rejecting it would drop every observation of a
        // perfectly good cross-language summary, which is exactly
        // what ClaimGrounding's contract forbids. The quote itself
        // was verified against the note above, and the observation
        // stays labelled unverified.
        if (ClaimGrounding.verdict(claim, rawQuote) == ClaimGrounding.Verdict.UNSUPPORTED) {
            failures += failure(GroundingFailureReason.CLAIM_NOT_SUPPORTED_BY_QUOTE)
            continue
        }
        validObservations += ModelObs(
            text = claim,
            noteId = obs.noteId,
            quote = rawQuote,
            evidenceType = evidenceType.name,
        )
    }
    return ObservationEvaluation(validObservations, failures)
}

/** Render structured note fields as readable source data for the model. The
 * rendered section is never quotable evidence — structured citations use
 * evidenceType STRUCTURED_FIELD with the field name and value, validated
 * against the NoteDto itself. */
private fun NoteDto.aiSourceText(): String = buildString {
    append(combinedText())
    append("\n\nRecorded observations:\n")
    appendObservation("Mood", mood)
    appendObservation("Appetite", appetite)
    appendObservation("Sleep", sleep)
    appendObservation("Mobility", mobility)
    appendObservation("Medication status", medicationTaken)
    append("- Pain level: ").append(pain).append(" out of 10.\n")
    append("- Fall: ")
    append(if (fall) "a fall was reported.\n" else "no fall was reported.\n")
}

private fun StringBuilder.appendObservation(label: String, value: String) {
    val recordedValue = value.trim()
    if (recordedValue.isEmpty()) return
    append("- ").append(label).append(": ").append(recordedValue).append(".\n")
}
