package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AddendumDao {
    @Query("SELECT * FROM addenda WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    suspend fun byNoteId(noteId: String): List<AddendumEntity>

    /** Whole-table read for a backup snapshot; same order as [byNoteId]. */
    @Query("SELECT * FROM addenda ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<AddendumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addendum: AddendumEntity)

    /** Backup restore write: aborts on a duplicate id instead of replacing. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImported(items: List<AddendumEntity>)

    /** Backup restore write: clears the table inside the restore transaction. */
    @Query("DELETE FROM addenda")
    suspend fun deleteAll()
}