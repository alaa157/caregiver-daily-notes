package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AddendumDao {
    @Query("SELECT * FROM addenda WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    suspend fun byNoteId(noteId: String): List<AddendumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addendum: AddendumEntity)
}