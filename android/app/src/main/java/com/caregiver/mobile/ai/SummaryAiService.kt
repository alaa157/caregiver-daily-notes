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

data class SummaryAiDiagnostics(
    val totalObservations: Int,
    val groundedObservations: Int,
    val providerFailure: Boolean = false,
    val providerReasonCode: String? = null,
)

sealed class SummaryAiResult {
    data class Success(
        val summary: SummaryDto,
        /**
         * [LlmStatus.REPAIRED] when the reply only became readable after the
         * single repair retry, [LlmStatus.OK] when it was clean.
         */
        val status: LlmStatus = LlmStatus.OK,
        /** Present only for callers that explicitly request probe diagnostics. */
        val diagnostics: SummaryAiDiagnostics? = null,
    ) : SummaryAiResult()

    data class Failure(
        val redFlags: List<String>,
        val reasonCode: String,
        /** Present only for callers that explicitly request probe diagnostics. */
        val diagnostics: SummaryAiDiagnostics? = null,
    ) : SummaryAiResult()
}

class SummaryAiService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** UI language of the generated human-readable summary text ("ar"/"en"). */
    private val language: String = "en",
    /** Opt-in diagnostics keep the normal summary result unchanged for ordinary callers. */
    private val includeDiagnostics: Boolean = false,
    /** Uncommitted settings-screen prompt preference, when the probe is testing edits. */
    private val promptPreferencesOverride: String? = null,
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
        val userPref = promptPreferencesOverride
            ?: keyStore?.summarySystemPrompt()?.takeIf { it.isNotBlank() }
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
                return SummaryAiResult.Failure(
                    redFlags,
                    reply.error ?: "unavailable",
                    diagnostics = if (includeDiagnostics) SummaryAiDiagnostics(0, 0, providerFailure = true) else null,
                )
            is LlmJson.Reply.Oversized ->
                return SummaryAiResult.Failure(redFlags, reply.error ?: "oversized")
            is LlmJson.Reply.Malformed ->
                return SummaryAiResult.Failure(
                    redFlags,
                    reply.error ?: "malformed_output",
                    diagnostics = if (includeDiagnostics && reply.providerErrorCode != null) {
                        SummaryAiDiagnostics(
                            totalObservations = 0,
                            groundedObservations = 0,
                            providerFailure = true,
                            providerReasonCode = reply.providerErrorCode,
                        )
                    } else {
                        null
                    },
                )
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
        val validObservations = mutableListOf<ModelObs>()
        var hadInvalid = false
        // Hard bound so a model that ignores the prompt (or a custom user
        // prompt) cannot turn the Overview and "Important notes" back into a
        // per-note dump. Keep in sync with Prompts.SUMMARY_SYSTEM.
        for (obs in parsed.observations.take(MAX_OBSERVATIONS)) {
            val claim = if (obs.claim.isNotBlank()) obs.claim else obs.text
            val rawQuote = if (obs.quote.isNotBlank()) obs.quote else obs.evidence
            if (claim.isBlank()) {
                hadInvalid = true
                continue
            }
            val note = notesById[obs.noteId]
            if (note == null) {
                hadInvalid = true
                continue
            }
            val evidenceType = StructuredEvidence.typeOf(obs.evidenceType, obs.structuredField)
            if (evidenceType == null) {
                hadInvalid = true
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
                    hadInvalid = true
                    continue
                }
                val actual = StructuredEvidence.actualValue(note, field)
                if (!StructuredEvidence.claimAligns(claim, field, actual)) {
                    hadInvalid = true
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
                hadInvalid = true
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
            if (ClaimGrounding.verdict(claim, rawQuote) == ClaimGrounding.Verdict.UNSUPPORTED) {
                hadInvalid = true
                continue
            }
            validObservations += ModelObs(
                text = claim,
                noteId = obs.noteId,
                quote = rawQuote,
                evidenceType = evidenceType.name,
            )
        }

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
        if (hadInvalid) {
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
        if (parsed.observations.isNotEmpty() && validObservations.isEmpty()) {
            return SummaryAiResult.Failure(
                redFlags,
                "invalid_evidence",
                diagnostics = if (includeDiagnostics) {
                    SummaryAiDiagnostics(parsed.observations.size, validObservations.size)
                } else {
                    null
                },
            )
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
            diagnostics = if (includeDiagnostics) {
                SummaryAiDiagnostics(parsed.observations.size, validObservations.size)
            } else {
                null
            },
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
