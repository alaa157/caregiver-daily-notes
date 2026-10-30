package com.caregiver.mobile.data.local

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Local-first store behind the home medication card: the tracked
 * medications for every recipient plus the per-day take/un-take marks.
 * The tables are standalone (see [MedicationEntity]) — no server resource
 * mirrors them, so nothing here touches the outbox or the APIs, and the
 * store only exists when the graph has a local database.
 */
class MedicationStore(private val db: AppDatabase) {
    private val dao = db.medicationDao()

    /**
     * A deleted recipient is not writable, and a missing one is a stale
     * write rather than a no-op: its children would outlive it with no
     * screen able to reach them. Callers run this inside the same
     * transaction as their write so a delete cannot interleave.
     */
    private suspend fun requireWritableRecipient(recipientId: String) {
        val recipient = db.recipientDao().byServerId(recipientId)
            ?: throw NoSuchElementException("recipient $recipientId")
        if (recipient.archivedAt != null) {
            throw ArchivedRecipientException(recipientId)
        }
    }

    private suspend fun requireWritableMedication(medicationId: String): MedicationEntity {
        val medication = dao.byId(medicationId)
            ?: throw NoSuchElementException("medication $medicationId")
        requireWritableRecipient(medication.recipientId)
        return medication
    }

    fun observeActive(): Flow<List<MedicationEntity>> = dao.observeActive()

    fun observeIntakes(date: String): Flow<List<MedicationIntakeEntity>> =
        dao.observeIntakes(date)

    /** Inserts a tracked medication and returns its id. */
    suspend fun add(
        recipientId: String,
        name: String,
        dose: String? = null,
        schedule: String? = null,
        dosesPerDay: Int = 1,
    ): String = db.withTransaction {
        requireWritableRecipient(recipientId)
        val entity = MedicationEntity(
            id = UUID.randomUUID().toString(),
            recipientId = recipientId,
            name = name.trim(),
            dose = dose?.trim()?.takeIf { it.isNotEmpty() },
            schedule = schedule?.trim()?.takeIf { it.isNotEmpty() },
            active = true,
            createdAt = System.currentTimeMillis(),
            dosesPerDay = dosesPerDay.coerceIn(1, MAX_DOSES_PER_DAY),
        )
        dao.upsert(entity)
        entity.id
    }

    /** Edit in place (name/dose/schedule/doses/active) without touching the id. */
    suspend fun update(entity: MedicationEntity) {
        db.withTransaction {
            requireWritableMedication(entity.id)
            requireWritableRecipient(entity.recipientId)
            dao.update(
                entity.copy(dosesPerDay = entity.dosesPerDay.coerceIn(1, MAX_DOSES_PER_DAY)),
            )
        }
    }

    /**
     * Deleting a medication also forgets its history — nothing dangles. Both
     * statements commit in one transaction: a cancellation or storage error
     * between them can no longer strip the intake history off a medication
     * that stays behind.
     */
    suspend fun delete(id: String) {
        db.withTransaction {
            requireWritableMedication(id)
            dao.clearIntakesFor(id)
            dao.delete(id)
        }
    }

    /** Marks the first dose of the day. */
    suspend fun setTaken(medicationId: String, date: LocalDate, taken: Boolean) =
        setTaken(medicationId, date, 0, taken)

    /**
     * Marks one dose of the day. Each dose of a medication is its own mark,
     * so a twice-daily medication can have its morning dose taken and its
     * evening dose still due.
     */
    suspend fun setTaken(medicationId: String, date: LocalDate, doseIndex: Int, taken: Boolean) {
        db.withTransaction {
            requireWritableMedication(medicationId)
            val id = intakeId(medicationId, date, doseIndex)
            if (taken) {
                dao.upsertIntake(
                    MedicationIntakeEntity(
                        id = id,
                        medicationId = medicationId,
                        date = date.toString(),
                        times = 1,
                    ),
                )
            } else {
                dao.clearIntake(id)
            }
        }
    }

    companion object {
        /** The date a tick belongs to — refreshed per action, so midnight is even. */
        fun today(): LocalDate = LocalDate.now()

        /** The most doses a day the tracker tracks. */
        const val MAX_DOSES_PER_DAY = 4

        /** The intake key: one row per medication, per date, per dose. */
        fun intakeId(medicationId: String, date: LocalDate, doseIndex: Int): String =
            "$medicationId:$date:$doseIndex"
    }
}
