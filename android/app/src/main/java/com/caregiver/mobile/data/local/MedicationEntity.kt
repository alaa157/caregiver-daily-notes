package com.caregiver.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Standalone medication tracker tables: local-only, so unlike
 * recipients/notes they carry no serverId and never ride the outbox.
 * [MedicationEntity] is the per-recipient list of tracked medications
 * (name, optional dose and schedule, active flag); [MedicationIntakeEntity]
 * is one row per medication per day — absence of a row means "not taken".
 */
@Entity(
    tableName = "medications",
    indices = [Index(value = ["recipientId"])],
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val name: String,
    val dose: String?,
    val schedule: String?,
    val active: Boolean,
    val createdAt: Long,
)

/**
 * The primary key is "<medicationId>:<date>", so taking/retaking the same
 * dose upserts instead of stacking rows and clearing is a single delete.
 */
@Entity(tableName = "medication_intakes")
data class MedicationIntakeEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    /** Local date the dose belongs to, ISO yyyy-MM-dd. */
    val date: String,
    val times: Int,
)

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationEntity)

    @Update
    suspend fun update(entity: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM medication_intakes WHERE date = :date")
    fun observeIntakes(date: String): Flow<List<MedicationIntakeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntake(entity: MedicationIntakeEntity)

    @Query("DELETE FROM medication_intakes WHERE id = :id")
    suspend fun clearIntake(id: String)

    @Query("DELETE FROM medication_intakes WHERE medicationId = :medicationId")
    suspend fun clearIntakesFor(medicationId: String)
}
