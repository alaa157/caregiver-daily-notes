package com.caregiver.mobile.data.summary

import androidx.room.withTransaction
import com.caregiver.mobile.ai.TrendDto
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.SavedSummaryEntity
import com.caregiver.mobile.presentation.summary.SummaryResultData
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cap on saved summaries per recipient; the oldest are pruned on save. */
private const val MAX_PER_RECIPIENT = 20

/** Hard cap on the stored payload so one summary cannot bloat the table. */
private const val MAX_PAYLOAD_CHARS = 200_000

/**
 * The on-device store for generated summaries. It is the only writer and
 * reader of the `saved_summaries` table: the payload format (summary plus
 * trends as one JSON document) and the twenty-row cap live here so every
 * caller sees the same behaviour. Payloads and summary text are never
 * logged.
 */
class SavedSummaryStore(
    private val db: AppDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    @Serializable
    private data class SavedPayload(
        val summary: SummaryDto,
        val trends: List<TrendDto> = emptyList(),
    )

    /** One row in the saved-summaries list; no payload, so it is cheap to render. */
    data class SavedSummaryItem(
        val id: String,
        val createdAt: Long,
        val periodDays: Int,
        val periodStart: String,
        val periodEnd: String,
        val aiGenerated: Boolean,
        val hasFlags: Boolean,
    )

    /**
     * Saves [data] for [recipientId] and returns the new row id. Requires
     * the period bounds and generation time — a summary that cannot say
     * when it covers is not saveable. Keeps at most [MAX_PER_RECIPIENT]
     * rows per recipient, pruning the oldest inside the same transaction.
     */
    suspend fun save(recipientId: String, data: SummaryResultData): String {
        val periodStart = data.periodStart
            ?: throw IllegalArgumentException("periodStart is required")
        val periodEnd = data.periodEnd
            ?: throw IllegalArgumentException("periodEnd is required")
        val generatedAt = data.generatedAt
            ?: throw IllegalArgumentException("generatedAt is required")
        val payload = json.encodeToString(
            SavedPayload.serializer(),
            SavedPayload(data.summary, data.trends),
        )
        if (payload.length > MAX_PAYLOAD_CHARS) {
            throw IllegalArgumentException("payload too large")
        }
        val id = newId()
        db.withTransaction {
            db.savedSummaryDao().insert(
                SavedSummaryEntity(
                    id = id,
                    recipientId = recipientId,
                    periodDays = data.summary.periodDays,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    createdAt = generatedAt,
                    // AI-generated results carry the provider that produced
                    // them; backend summaries leave it null.
                    aiGenerated = data.provider != null,
                    provider = data.provider,
                    model = data.model,
                    payload = payload,
                ),
            )
            val rows = db.savedSummaryDao().forRecipient(recipientId)
            rows.drop(MAX_PER_RECIPIENT).forEach { db.savedSummaryDao().deleteById(it.id) }
        }
        return id
    }

    /** All saved summaries for [recipientId], newest first; corrupt rows are skipped. */
    suspend fun list(recipientId: String): List<SavedSummaryItem> =
        db.savedSummaryDao().forRecipient(recipientId).mapNotNull { it.toItem() }

    /** One saved summary by id, or null when missing or its payload does not parse. */
    suspend fun get(id: String): SummaryResultData? {
        val entity = db.savedSummaryDao().byId(id) ?: return null
        val payload = decode(entity.payload) ?: return null
        return SummaryResultData(
            summary = payload.summary,
            trends = payload.trends,
            periodStart = entity.periodStart,
            periodEnd = entity.periodEnd,
            provider = entity.provider,
            model = entity.model,
            generatedAt = entity.createdAt,
            savedId = entity.id,
        )
    }

    /** The newest saved summary for [recipientId], or null when there is none or it is corrupt. */
    suspend fun latest(recipientId: String): SavedSummaryItem? =
        db.savedSummaryDao().forRecipient(recipientId).firstNotNullOfOrNull { it.toItem() }

    suspend fun delete(id: String) {
        db.savedSummaryDao().deleteById(id)
    }

    private fun decode(payload: String): SavedPayload? =
        runCatching { json.decodeFromString(SavedPayload.serializer(), payload) }.getOrNull()

    private fun SavedSummaryEntity.toItem(): SavedSummaryItem? {
        val payload = decode(payload) ?: return null
        return SavedSummaryItem(
            id = id,
            createdAt = createdAt,
            periodDays = periodDays,
            periodStart = periodStart,
            periodEnd = periodEnd,
            aiGenerated = aiGenerated,
            hasFlags = payload.summary.redFlags.isNotEmpty(),
        )
    }
}
