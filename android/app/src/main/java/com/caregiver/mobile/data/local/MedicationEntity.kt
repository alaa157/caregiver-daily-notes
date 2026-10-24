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
 * (name, optional dose and schedule, how many doses a day, active flag);
 * [MedicationIntakeEntity] is one row per medication per dose per day —
 * absence of a row means "not taken".
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
    /**
     * How many doses a day this medication is taken. A twice-daily
     * medication needs two marks a day, one for each dose.
     *
     * The column is last so the 5→6 migration is a plain ALTER TABLE.
     */
    val dosesPerDay: Int = 1,
)

/**
 * The primary key is "<medicationId>:<date>:<doseIndex>", so each dose of a
 * day is its own row: taking and untaking one dose never disturbs the
 * other, and clearing is a single delete.
 */
@Entity(tableName = "medication_intakes")
data class MedicationIntakeEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    /** Local date the dose belongs to, ISO yyyy-MM-dd. */
    val date: String,
    /** How many times this dose was marked taken; one per row. */
    val times: Int,
)

/**
 * The dose this row belongs to, parsed from the intake key
 * ("<medicationId>:<date>:<doseIndex>"). Null for a key that carries no
 * dose, which is treated as "not taken" rather than guessed at.
 */
fun MedicationIntakeEntity.doseIndex(): Int? =
    id.substringAfterLast(':').toIntOrNull()

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

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun byId(id: String): MedicationEntity?

    /** Every medication, including inactive ones; stable for a backup snapshot. */
    @Query("SELECT * FROM medications ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImported(entities: List<MedicationEntity>)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    @Query("SELECT id FROM medications WHERE recipientId = :recipientId")
    suspend fun idsByRecipient(recipientId: String): List<String>

    @Query("SELECT COUNT(*) FROM medications WHERE recipientId = :recipientId")
    suspend fun countByRecipient(recipientId: String): Int

    @Query(
        "SELECT COUNT(*) FROM medication_intakes WHERE medicationId IN " +
            "(SELECT id FROM medications WHERE recipientId = :recipientId)",
    )
    suspend fun countIntakesForRecipient(recipientId: String): Int

    @Query("DELETE FROM medication_intakes WHERE medicationId IN (:medicationIds)")
    suspend fun clearIntakesForMedications(medicationIds: List<String>)

    @Query("DELETE FROM medications WHERE recipientId = :recipientId")
    suspend fun deleteByRecipient(recipientId: String)

    @Query("SELECT * FROM medication_intakes WHERE date = :date")
    fun observeIntakes(date: String): Flow<List<MedicationIntakeEntity>>

    /** Every intake mark, ordered by its stable local key. */
    @Query("SELECT * FROM medication_intakes ORDER BY id ASC")
    suspend fun allIntakes(): List<MedicationIntakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedIntakes(entities: List<MedicationIntakeEntity>)

    @Query("DELETE FROM medication_intakes")
    suspend fun deleteAllIntakes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntake(entity: MedicationIntakeEntity)

    @Query("DELETE FROM medication_intakes WHERE id = :id")
    suspend fun clearIntake(id: String)

    @Query("DELETE FROM medication_intakes WHERE medicationId = :medicationId")
    suspend fun clearIntakesFor(medicationId: String)
}
