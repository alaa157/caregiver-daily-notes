package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import kotlinx.coroutines.flow.Flow

/**
 * Note saved while the device could not reach the server (Task 8b). The row
 * carries everything the create call needs, plus the client-generated
 * [localId] (a UUID; the API accepts no client id — see
 * docs/api/android-gaps.md) and the [date]/[createdAt] needed to place the
 * pending note in timelines before the server confirms it.
 *
 * Task 8c drains this table: [OutboxStatus.SYNCING] while an upload runs,
 * back to [OutboxStatus.PENDING] when the server could not be reached (the
 * worker retries with backoff), [OutboxStatus.FAILED] when the server
 * rejected the note, and a successful upload deletes the row. Demo builds
 * never reach this table: their graph has no DAO, so demo data still never
 * touches disk.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val localId: String,
    val recipientId: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
    val date: String,
    val createdAt: Long,
    val status: String = OutboxStatus.PENDING,
)

object OutboxStatus {
    const val PENDING = "pending"
    const val SYNCING = "syncing"
    const val FAILED = "failed"
}

/** Pending rows render exactly like server rows, plus the clock in the UI. */
fun OutboxEntity.toNoteDto(): NoteDto = NoteDto(
    id = localId,
    recipientId = recipientId,
    date = date,
    mood = mood,
    appetite = appetite,
    sleep = sleep,
    mobility = mobility,
    medicationTaken = medicationTaken,
    pain = pain,
    fall = fall,
    text = text,
    pending = true,
)

/** Detail for a not-yet-synced note: the local fields, and no addenda (yet). */
fun OutboxEntity.toNoteDetailDto(): NoteDetailDto = NoteDetailDto(
    id = localId,
    recipientId = recipientId,
    date = date,
    mood = mood,
    appetite = appetite,
    sleep = sleep,
    mobility = mobility,
    medicationTaken = medicationTaken,
    pain = pain,
    fall = fall,
    text = text,
    addenda = emptyList(),
)

/** The create call the drain (Task 8c) replays for this row. */
fun OutboxEntity.toCreateRequest(): CreateNoteRequest = CreateNoteRequest(
    recipientId = recipientId,
    mood = mood,
    appetite = appetite,
    sleep = sleep,
    mobility = mobility,
    medicationTaken = medicationTaken,
    pain = pain,
    fall = fall,
    text = text,
    idempotencyKey = localId,
)

/**
 * Room access for the outbox. [query] mirrors the history endpoint's
 * filters (null = wildcard) so a timeline request merges exactly the pending
 * notes that belong in its window; the status/delete writes are the Task 8c
 * contract.
 */
@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: OutboxEntity)

    @Query(
        """
        SELECT * FROM outbox
        WHERE (:recipientId IS NULL OR recipientId = :recipientId)
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        ORDER BY createdAt DESC
        """,
    )
    suspend fun query(recipientId: String?, from: String?, to: String?): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE localId = :localId")
    suspend fun byLocalId(localId: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM outbox WHERE recipientId = :recipientId")
    suspend fun countByRecipient(recipientId: String): Int

    @Query("DELETE FROM outbox WHERE recipientId = :recipientId")
    suspend fun deleteByRecipient(recipientId: String)

    /** Observes every queued row, newest first (Task 8d sync status). */
    @Query("SELECT * FROM outbox ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Query("UPDATE outbox SET status = :status WHERE localId = :localId")
    suspend fun updateStatus(localId: String, status: String)

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun delete(localId: String)
}
