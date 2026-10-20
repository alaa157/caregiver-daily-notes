package com.caregiver.mobile.data.local

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

    fun observeActive(): Flow<List<MedicationEntity>> = dao.observeActive()

    fun observeIntakes(date: String): Flow<List<MedicationIntakeEntity>> =
        dao.observeIntakes(date)

    /** Inserts a tracked medication and returns its id. */
    suspend fun add(
        recipientId: String,
        name: String,
        dose: String? = null,
        schedule: String? = null,
    ): String {
        val entity = MedicationEntity(
            id = UUID.randomUUID().toString(),
            recipientId = recipientId,
            name = name.trim(),
            dose = dose?.trim()?.takeIf { it.isNotEmpty() },
            schedule = schedule?.trim()?.takeIf { it.isNotEmpty() },
            active = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.id
    }

    /** Edit in place (name/dose/schedule/active) without touching the id. */
    suspend fun update(entity: MedicationEntity) = dao.update(entity)

    /** Deleting a medication also forgets its history — nothing dangles. */
    suspend fun delete(id: String) {
        dao.clearIntakesFor(id)
        dao.delete(id)
    }

    suspend fun setTaken(medicationId: String, date: LocalDate, taken: Boolean) {
        if (taken) {
            dao.upsertIntake(
                MedicationIntakeEntity(
                    id = "$medicationId:$date",
                    medicationId = medicationId,
                    date = date.toString(),
                    times = 1,
                ),
            )
        } else {
            dao.clearIntake("$medicationId:$date")
        }
    }

    companion object {
        /** The date a tick belongs to — refreshed per action, so midnight is even. */
        fun today(): LocalDate = LocalDate.now()
    }
}
