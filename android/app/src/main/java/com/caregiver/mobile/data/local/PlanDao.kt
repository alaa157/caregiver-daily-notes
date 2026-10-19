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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanEntity)

    /** Backup restore write: aborts on a duplicate id instead of replacing. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImported(items: List<PlanEntity>)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: String)

    /** Backup restore write: clears the table inside the restore transaction. */
    @Query("DELETE FROM plans")
    suspend fun deleteAll()
}
