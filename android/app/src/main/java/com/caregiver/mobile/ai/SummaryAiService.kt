package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class ModelObs(val text: String, val noteId: String, val quote: String)

@Serializable
private data class ModelUnc(val topic: String, val detail: String)

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
    ): SummaryDto {
        val dataBlock = notes.joinToString("\n") { note ->
            "[NOTE id=\"${note.id}\" date=${note.date}]\n${note.text}\n[/NOTE]"
        }
        val prompt = keyStore?.systemPrompt()?.takeIf { it.isNotBlank() } ?: Prompts.SUMMARY_SYSTEM
        val res = client.complete(com.caregiver.mobile.ai.LlmRequest(prompt, dataBlock))
        val redFlags = SafetySignalEvaluator.evaluate(signals)
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
        val parsed = runCatching {
            json.decodeFromString(ModelSummary.serializer(), res.text)
        }.getOrElse {
            ModelSummary()
        }
        val evidence = parsed.observations.map { SummaryDto.EvidenceDto(noteId = it.noteId, quote = it.quote) }
        val uncertainties = parsed.uncertainties.map { SummaryDto.UncertaintyDto(topic = it.topic, detail = it.detail) }
        val text = parsed.observations.joinToString(" ") { it.text }
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
