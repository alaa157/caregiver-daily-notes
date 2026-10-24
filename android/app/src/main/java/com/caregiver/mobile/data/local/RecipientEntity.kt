package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.caregiver.mobile.data.api.RecipientDto
import kotlinx.coroutines.flow.Flow

/**
 * Cached recipient (Task 8a). Fields mirror [RecipientDto]; [rowId] is a
 * local surrogate key that preserves the server's list order across
 * refreshes, [serverId] is the backend id, and [localId] is the nullable
 * client-side id Task 8b assigns to rows that exist locally before the
 * server confirms them (always null until then).
 */
@Entity(tableName = "recipients", indices = [Index(value = ["serverId"], unique = true)])
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val serverId: String,
    val localId: String? = null,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)

fun RecipientEntity.toDto(): RecipientDto =
    RecipientDto(id = serverId, name = name, active = active, archivedAt = archivedAt)

fun RecipientDto.toEntity(): RecipientEntity =
    RecipientEntity(serverId = id, name = name, active = active, archivedAt = archivedAt)

/**
 * Room access for the recipient cache. [replaceAll] is the refresh write:
 * the recipients API returns the full list, so a successful refresh swaps
 * the cache wholesale and rows the server dropped disappear.
 */
@Dao
interface RecipientDao {
    /** Observes the cached list in server order ([rowId]). Reactive read path for Task 8b/8d. */
    @Query("SELECT * FROM recipients ORDER BY rowId")
    fun observeAll(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients ORDER BY rowId")
    suspend fun all(): List<RecipientEntity>

    @Query("SELECT * FROM recipients WHERE serverId = :serverId")
    suspend fun byServerId(serverId: String): RecipientEntity?

    @Query("UPDATE recipients SET archivedAt = :archivedAt WHERE serverId = :serverId")
    suspend fun setArchivedAt(serverId: String, archivedAt: Long?): Int

    /** In-place active toggle: keeps the row's list position (unlike a REPLACE insert). */
    @Query("UPDATE recipients SET active = :active WHERE serverId = :serverId")
    suspend fun setActive(serverId: String, active: Boolean): Int

    @Query("DELETE FROM recipients WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecipientEntity>)

    /**
     * Backup restore write: like [insertAll] but aborts on a unique-key
     * collision, so a duplicate id in a restored file rolls the whole restore
     * back instead of silently replacing a row.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImported(items: List<RecipientEntity>)

    @Query("DELETE FROM recipients")
    suspend fun clear()

    /** Replaces the cached list with the server's latest response (replace on refresh). */
    @Transaction
    suspend fun replaceAll(items: List<RecipientEntity>) {
        clear()
        insertAll(items)
    }
}
