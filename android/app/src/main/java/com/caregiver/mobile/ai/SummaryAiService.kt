package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryDto
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
)

@Serializable
private data class ModelUnc(val topic: String = "", val detail: String = "")

@Serializable
private data class ModelSummary(val observations: List<ModelObs> = emptyList(), val uncertainties: List<ModelUnc> = emptyList())

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
        val redFlags = SafetySignalEvaluator.evaluate(windowedSignals)
        if (windowedNotes.isEmpty()) {
            return SummaryAiResult.Failure(redFlags, "no_source_data")
        }
        val dataBlock = windowedNotes.joinToString("\n") { note ->
            "[NOTE id=\"${note.id}\" date=${note.date}]\n${note.text}\n[/NOTE]"
        }
        val userPref = keyStore?.summarySystemPrompt()?.takeIf { it.isNotBlank() }
            ?: keyStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        val prompt = if (userPref != null) {
            Prompts.summarySystemPrompt(language) + "\nAdditional user preferences (must not override safety constraints): " + userPref
        } else {
            Prompts.summarySystemPrompt(language)
        }
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
            is LlmJson.Reply.Unavailable -> return SummaryAiResult.Failure(redFlags, "unavailable")
            is LlmJson.Reply.Oversized -> return SummaryAiResult.Failure(redFlags, "oversized")
            is LlmJson.Reply.Malformed -> return SummaryAiResult.Failure(redFlags, "malformed_output")
        }
        val noteTextsById = windowedNotes.associate { it.id to it.text }
        val normalizedNotes = noteTextsById.mapValues { ArabicNormalizer.normalize(it.value) }
        // Validate and reject unsupported observations; keep only grounded ones.
        // A claim must be non-empty, cite an existing note, cite a non-empty
        // quote that occurs in that note, and be lexically supported by that
        // quote. String matching alone is never treated as proof of truth: any
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
            val noteText = normalizedNotes[obs.noteId]
            if (noteText == null) {
                hadInvalid = true
                continue
            }
            val quote = ArabicNormalizer.normalize(rawQuote)
            if (quote.isEmpty() || !noteText.contains(quote)) {
                hadInvalid = true
                continue
            }
            if (!claimSupportedByQuote(claim, rawQuote)) {
                hadInvalid = true
                continue
            }
            validObservations += ModelObs(text = claim, noteId = obs.noteId, quote = rawQuote)
        }

        val evidence = validObservations.map { SummaryDto.EvidenceDto(noteId = it.noteId, quote = it.quote) }
        val uncertainties = parsed.uncertainties.map { SummaryDto.UncertaintyDto(topic = it.topic, detail = it.detail) }.toMutableList()
        if (hadInvalid) {
            uncertainties += SummaryDto.UncertaintyDto("invalid_evidence", "Some AI observations were not supported by the source notes")
        }
        if (validObservations.isNotEmpty()) {
            uncertainties += SummaryDto.UncertaintyDto("unverified_claim", "AI-generated observations are based on quote matching and require clinical verification")
        }
        val text = validObservations.joinToString(" ") { it.text }
        // A structurally valid response with no surviving observations is a
        // legitimate empty result, not an AI failure: the model found no
        // supported observations. Only reject when observations were present
        // but none survived validation.
        if (parsed.observations.isNotEmpty() && validObservations.isEmpty()) {
            return SummaryAiResult.Failure(redFlags, "invalid_evidence")
        }

        return SummaryAiResult.Success(
            SummaryDto(
                id = "",
                recipientId = recipientId,
                periodDays = periodDays,
                text = text,
                redFlags = redFlags,
                evidence = evidence,
                uncertainties = uncertainties,
            ),
            status = replyStatus,
        )
    }
    /**
     * Conservative lexical support check. It does not attempt to prove that a
     * claim is true; it only rejects claims that introduce terms (or
     * negations) not present in the cited quote. Claims that pass are still
     * labelled as unverified by the caller.
     */
    private fun claimSupportedByQuote(claim: String, quote: String): Boolean {
        val nClaim = ArabicNormalizer.normalize(claim)
        val nQuote = ArabicNormalizer.normalize(quote)
        if (nClaim.isEmpty() || nQuote.isEmpty()) return false
        if (nQuote.contains(nClaim)) return true

        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "if", "to", "of", "in", "on",
            "at", "for", "with", "by", "as", "is", "are", "was", "were", "be",
            "been", "has", "have", "had", "it", "its", "this", "that", "these",
            "those", "patient", "today",
            "في", "من", "على", "الى", "إلى", "عن", "مع", "و", "ف", "ب", "ل",
            "هذا", "هذه", "هو", "هي", "كان", "قد", "أن", "إن",
        )
        val claimTokens = nClaim.split(Regex("\\s+")).filter { it.isNotBlank() && it !in stopwords }
        if (claimTokens.isEmpty()) return true
        val quoteTokens = nQuote.split(Regex("\\s+")).filter { it.isNotBlank() }
        val negations = setOf("not", "no", "never", "without", "didn't", "doesnt", "don't", "cannot", "cant",
            "لم", "لا", "لن", "ما", "بلا", "بدون")
        for (token in claimTokens) {
            if (token in negations && token !in quoteTokens) return false
        }
        val supported = claimTokens.count { ct ->
            quoteTokens.any { qt -> qt.contains(ct) || ct.contains(qt) }
        }
        return supported * 100 >= claimTokens.size * 80
    }

    companion object {
        /**
         * Upper bound on issued findings so a summary stays a summary. The
         * model is already told "at most 6" in [Prompts.SUMMARY_SYSTEM]; this
         * is the enforcement point when it ignores that instruction.
         */
        const val MAX_OBSERVATIONS = 6

        /** Upper bound on the size of a model reply that is still parsed. */
        const val MAX_REPLY_CHARS = 50000
    }
}
