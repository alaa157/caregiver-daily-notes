package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans")
    suspend fun all(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: String): PlanEntity?

    @Query("SELECT * FROM plans WHERE recipientId = :recipientId")
    suspend fun byRecipient(recipientId: String): List<PlanEntity>

    @Query("SELECT COUNT(*) FROM plans WHERE recipientId = :recipientId")
    suspend fun countByRecipient(recipientId: String): Int

    @Query("DELETE FROM plans WHERE recipientId = :recipientId")
    suspend fun deleteByRecipient(recipientId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanEntity)

    /** Creates a plan; an id collision aborts instead of replacing history. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlanEntity)

    /**
     * Compare-and-set write for an existing plan: the row changes only when
     * its version chain still equals [expectedVersionsJson]. A stale writer
     * gets 0 back instead of overwriting the version another write added.
     */
    @Query(
        "UPDATE plans SET versionsJson = :versionsJson, updatedAt = :updatedAt " +
            "WHERE id = :id AND versionsJson = :expectedVersionsJson",
    )
    suspend fun updateIfUnchanged(
        id: String,
        expectedVersionsJson: String,
        versionsJson: String,
        updatedAt: Long,
    ): Int

    /** Backup restore write: aborts on a duplicate id instead of replacing. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImported(items: List<PlanEntity>)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: String)

    /** Backup restore write: clears the table inside the restore transaction. */
    @Query("DELETE FROM plans")
    suspend fun deleteAll()
}
