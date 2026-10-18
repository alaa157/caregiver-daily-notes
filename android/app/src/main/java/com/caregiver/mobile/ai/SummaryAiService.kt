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
    ): SummaryDto {
        // Window here as well as at the fetch: the model and the deterministic
        // flags must never see data outside the selected period, even if a
        // caller (or a backend that ignores from/to) hands over more history.
        val windowedNotes = notes.filter { SummaryWindow.contains(it.date, endInclusive, periodDays) }
        val windowedSignals = signals.filter { SummaryWindow.contains(it.date, endInclusive, periodDays) }
        val redFlags = SafetySignalEvaluator.evaluate(windowedSignals)
        if (windowedNotes.isEmpty()) {
            return SummaryDto(
                id = "",
                recipientId = recipientId,
                periodDays = periodDays,
                text = "",
                redFlags = redFlags,
                evidence = emptyList(),
                uncertainties = listOf(SummaryDto.UncertaintyDto("no_source_data", "No source notes available")),
            )
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
            return SummaryDto(
                id = "",
                recipientId = recipientId,
                periodDays = periodDays,
                text = "AI unavailable",
                redFlags = redFlags,
                evidence = emptyList(),
                uncertainties = listOf(SummaryDto.UncertaintyDto("availability", "AI unavailable")),
            )
        }
        val noteTextsById = windowedNotes.associate { it.id to it.text }
        val normalizedNotes = noteTextsById.mapValues { ArabicNormalizer.normalize(it.value) }

        val parsed = runCatching {
            json.decodeFromString(ModelSummary.serializer(), res.text.trim())
        }.getOrNull()

        if (parsed == null) {
            return SummaryDto(
                id = "",
                recipientId = recipientId,
                periodDays = periodDays,
                text = "",
                redFlags = redFlags,
                evidence = emptyList(),
                uncertainties = listOf(SummaryDto.UncertaintyDto("malformed_output", "AI output was not valid JSON")),
            )
        }

        // Validate and reject unsupported observations; keep only grounded ones.
        val validObservations = mutableListOf<ModelObs>()
        var hadInvalid = false
        for (obs in parsed.observations) {
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
        val text = validObservations.joinToString(" ") { it.text }
        return SummaryDto(
            id = "",
            recipientId = recipientId,
            periodDays = periodDays,
            text = text,
            redFlags = redFlags,
            evidence = evidence,
            uncertainties = uncertainties,
        )
    }
}