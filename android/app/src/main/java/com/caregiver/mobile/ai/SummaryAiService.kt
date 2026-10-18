package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryDto
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ModelObs(val text: String = "", val noteId: String = "", val quote: String = "")

@Serializable
private data class ModelUnc(val topic: String = "", val detail: String = "")

@Serializable
private data class ModelSummary(val observations: List<ModelObs> = emptyList(), val uncertainties: List<ModelUnc> = emptyList())

sealed class SummaryAiResult {
    data class Success(val summary: SummaryDto) : SummaryAiResult()
    data class Failure(val redFlags: List<String>, val reasonCode: String) : SummaryAiResult()
}

class SummaryAiService(
    private val client: LlmClient,
    private val keyStore: AiKeyStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
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
            Prompts.SUMMARY_SYSTEM + "\nAdditional user preferences (must not override safety constraints): " + userPref
        } else {
            Prompts.SUMMARY_SYSTEM
        }
        val res = client.complete(com.caregiver.mobile.ai.LlmRequest(prompt, dataBlock))
        if (res.status == com.caregiver.mobile.ai.LlmStatus.FALLBACK) {
            return SummaryAiResult.Failure(redFlags, "unavailable")
        }
        if (res.text.length > 50000) {
            return SummaryAiResult.Failure(redFlags, "oversized")
        }
        val noteTextsById = windowedNotes.associate { it.id to it.text }
        val normalizedNotes = noteTextsById.mapValues { ArabicNormalizer.normalize(it.value) }

        val parsed = runCatching {
            json.decodeFromString(ModelSummary.serializer(), res.text.trim())
        }.getOrNull()

        if (parsed == null) {
            return SummaryAiResult.Failure(redFlags, "malformed_output")
        }

        // Validate and reject unsupported observations; keep only grounded ones.
        val validObservations = mutableListOf<ModelObs>()
        var hadInvalid = false
        for (obs in parsed.observations.take(50)) {
            if (obs.text.isBlank()) {
                hadInvalid = true
                continue
            }
            val noteText = normalizedNotes[obs.noteId]
            if (noteText == null) {
                hadInvalid = true
                continue
            }
            val quote = ArabicNormalizer.normalize(obs.quote)
            if (quote.isEmpty() || !noteText.contains(quote)) {
                hadInvalid = true
                continue
            }
            validObservations += obs
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
        // If the model produced observations but none survived validation,
        // treat as a failure rather than a (misleading) successful summary.
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
            )
        )
    }
}