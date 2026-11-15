package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "saved_summaries",
    indices = [Index(value = ["recipientId", "createdAt"])],
)
data class SavedSummaryEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val periodDays: Int,
    val periodStart: String,
    val periodEnd: String,
    val createdAt: Long,
    val aiGenerated: Boolean,
    val provider: String?,
    val model: String?,
    val payload: String,
)

@Dao
interface SavedSummaryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavedSummaryEntity)

    @Query("SELECT * FROM saved_summaries WHERE recipientId = :recipientId ORDER BY createdAt DESC")
    suspend fun forRecipient(recipientId: String): List<SavedSummaryEntity>

    @Query("SELECT * FROM saved_summaries WHERE id = :id")
    suspend fun byId(id: String): SavedSummaryEntity?

    @Query("DELETE FROM saved_summaries WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM saved_summaries WHERE recipientId = :recipientId")
    suspend fun deleteForRecipient(recipientId: String): Int

    @Query("SELECT COUNT(*) FROM saved_summaries WHERE recipientId = :recipientId")
    suspend fun countForRecipient(recipientId: String): Int

    @Query("SELECT * FROM saved_summaries")
    suspend fun allRows(): List<SavedSummaryEntity>
}
