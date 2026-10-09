package com.caregiver.mobile.data.demo

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Offline demo storage (Step 6). Room only; nothing here touches the
 * network. The real backend (backend/ + data/api) is untouched.
 */

@Entity(tableName = "recipients")
data class RecipientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = RecipientEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipientId"), Index("date")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    /** ISO day yyyy-MM-dd. */
    val date: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
    val createdAt: Long,
)

@Entity(
    tableName = "addenda",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class AddendumEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val createdAt: Long,
)

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY name")
    fun observeAll(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE id = :id")
    suspend fun getById(id: String): RecipientEntity?

    @Query("SELECT COUNT(*) FROM recipients")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecipientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecipientEntity)

    @Query("DELETE FROM recipients")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE recipientId = :recipientId ORDER BY date DESC, createdAt DESC")
    fun observeByRecipient(recipientId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

@Dao
interface AddendumDao {
    @Query("SELECT * FROM addenda WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeByNote(noteId: String): Flow<List<AddendumEntity>>

    @Query("SELECT * FROM addenda WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getByNote(noteId: String): List<AddendumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AddendumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AddendumEntity)

    @Query("DELETE FROM addenda")
    suspend fun clear()
}

@Database(
    entities = [RecipientEntity::class, NoteEntity::class, AddendumEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DemoDatabase : RoomDatabase() {
    abstract fun recipients(): RecipientDao
    abstract fun notes(): NoteDao
    abstract fun addenda(): AddendumDao
}
