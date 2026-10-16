package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import kotlinx.coroutines.flow.Flow

/**
 * Cached note (Task 8a). Fields mirror [NoteDto]; [rowId] is a local
 * surrogate key, [serverId] is the backend id, and [localId] is the
 * nullable client-side id mapping an outbox note (Task 8b) onto its server
 * row once Task 8c syncs it; null until then.
 */
@Entity(tableName = "notes", indices = [Index(value = ["serverId"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val serverId: String,
    val localId: String? = null,
    val recipientId: String,
    val date: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

fun NoteEntity.toDto(): NoteDto = NoteDto(
    id = serverId,
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
)

fun NoteDto.toEntity(): NoteEntity = NoteEntity(
    serverId = id,
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
)

/** Detail carries the note fields plus addenda; only the note fields are cached. */
fun NoteDetailDto.toEntity(): NoteEntity = NoteEntity(
    serverId = id,
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
)

/**
 * Room access for the note cache. Reads mirror the history endpoint's
 * filters (null = wildcard, ISO dates compare lexicographically) so a
 * failed refresh can serve exactly the scope it asked the server for.
 * [replaceScope] is the refresh write: it replaces only the queried scope,
 * so refreshing one window never evicts another screen's cached window.
 */
@Dao
interface NoteDao {
    /** Observes the whole cache, newest first. Reactive read path for Task 8b/8d. */
    @Query("SELECT * FROM notes ORDER BY date DESC, rowId")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE (:recipientId IS NULL OR recipientId = :recipientId)
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        ORDER BY date DESC, rowId
        """,
    )
    suspend fun query(recipientId: String?, from: String?, to: String?): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE serverId = :serverId")
    suspend fun byServerId(serverId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NoteEntity>)

    @Query(
        """
        DELETE FROM notes
        WHERE (:recipientId IS NULL OR recipientId = :recipientId)
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        """,
    )
    suspend fun deleteScope(recipientId: String?, from: String?, to: String?)

    /** Replaces the cache for one queried scope (replace on refresh). */
    @Transaction
    suspend fun replaceScope(recipientId: String?, from: String?, to: String?, items: List<NoteEntity>) {
        deleteScope(recipientId, from, to)
        insertAll(items)
    }
}
