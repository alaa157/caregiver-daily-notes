package com.caregiver.mobile.data.local

import com.caregiver.mobile.ai.SafetySignalEvaluator
import com.caregiver.mobile.ai.SummaryWindow
import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.PlanDto
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryApi
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.data.local.toDto
import com.caregiver.mobile.data.local.toEntity
import com.caregiver.mobile.data.plan.IllegalPlanTransitionException
import com.caregiver.mobile.data.plan.PlanStateMachine
import com.caregiver.mobile.data.plan.planTransitionError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

class LocalBackendApis(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : BackendApis, RecipientAdmin {
    private val json = Json { ignoreUnknownKeys = true }

    private val recipientDao get() = db.recipientDao()
    private val noteDao get() = db.noteDao()
    private val planDao get() = db.planDao()
    private val addendumDao get() = db.addendumDao()

    private suspend fun requireWritableRecipient(recipientId: String) {
        if (recipientDao.byServerId(recipientId)?.archivedAt != null) {
            throw ArchivedRecipientException(recipientId)
        }
    }

    override suspend fun archive(id: String): RecipientDto {
        val existing = recipientDao.byServerId(id) ?: throw NoSuchElementException("recipient $id")
        if (existing.archivedAt == null) {
            recipientDao.setArchivedAt(id, clock())
        }
        return recipientDao.byServerId(id)?.toDto()
            ?: throw NoSuchElementException("recipient $id")
    }

    override suspend fun unarchive(id: String): RecipientDto {
        val existing = recipientDao.byServerId(id) ?: throw NoSuchElementException("recipient $id")
        recipientDao.setArchivedAt(id, null)
        return existing.copy(archivedAt = null).toDto()
    }

    override suspend fun deleteArchived(id: String) {
        db.deleteArchivedRecipient(id)
    }

    override suspend fun counts(id: String): RecipientDeletionCounts =
        db.recipientDataCounts(id)

    override suspend fun auth(): AuthApi = object : AuthApi {
        override suspend fun login(body: LoginRequest): AuthResponse = AuthResponse("local-token")
        override suspend fun register(body: RegisterRequest): AuthResponse = AuthResponse("local-token")
    }

    override suspend fun recipients(): RecipientApi = object : RecipientApi {
        override suspend fun list(): List<RecipientDto> =
            recipientDao.all().map { it.toDto() }

        override suspend fun create(body: CreateRecipientRequest): RecipientDto {
            val dto = RecipientDto(id = UUID.randomUUID().toString(), name = body.name, active = true)
            recipientDao.insertAll(listOf(dto.toEntity()))
            return dto
        }

        override suspend fun get(id: String): RecipientDto =
            recipientDao.byServerId(id)?.toDto() ?: throw NoSuchElementException("recipient $id")

        override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto {
            val existing = recipientDao.byServerId(id) ?: throw NoSuchElementException("recipient $id")
            val updated = existing.toDto().copy(active = body.active)
            recipientDao.insertAll(listOf(updated.toEntity()))
            return updated
        }
    }

    override suspend fun notes(): NoteApi = object : NoteApi {
        override suspend fun create(body: CreateNoteRequest): NoteDto {
            requireWritableRecipient(body.recipientId)
            val id = UUID.randomUUID().toString()
            val date = LocalDate.now().toString()
            val dto = NoteDto(
                id = id,
                recipientId = body.recipientId,
                date = date,
                mood = body.mood,
                appetite = body.appetite,
                sleep = body.sleep,
                mobility = body.mobility,
                medicationTaken = body.medicationTaken,
                pain = body.pain,
                fall = body.fall,
                text = body.text,
            )
            noteDao.insertAll(listOf(dto.toEntity()))
            return dto
        }

        override suspend fun detail(id: String): NoteDetailDto {
            val note = noteDao.byServerId(id) ?: throw NoSuchElementException("note $id")
            val dto = note.toDto()
            return NoteDetailDto(
                id = dto.id,
                recipientId = dto.recipientId,
                date = dto.date,
                mood = dto.mood,
                appetite = dto.appetite,
                sleep = dto.sleep,
                mobility = dto.mobility,
                medicationTaken = dto.medicationTaken,
                pain = dto.pain,
                fall = dto.fall,
                text = dto.text,
                addenda = addendumDao.byNoteId(id).map { it.toDto() },
            )
        }

        override suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto {
            val note = noteDao.byServerId(id) ?: throw NoSuchElementException("note $id")
            requireWritableRecipient(note.recipientId)
            val addendum = AddendumDto(
                id = UUID.randomUUID().toString(),
                noteId = id,
                createdAt = java.time.LocalDateTime.now().toString(),
                text = body.text,
            )
            addendumDao.insert(addendum.toEntity())
            return addendum
        }

        override suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> {
            val all = noteDao.query(null, null, null).map { it.toDto() }
            return all.filter { note ->
                (recipientId == null || note.recipientId == recipientId) &&
                    (from == null || note.date >= from) &&
                    (to == null || note.date <= to)
            }.sortedByDescending { it.date }
        }
    }

    override suspend fun summaries(): SummaryApi = object : SummaryApi {
        override suspend fun signals(id: String, from: String?, to: String?): List<SignalDto> {
            val notes = noteDao.query(null, null, null).map { it.toDto() }.filter { n ->
                n.recipientId == id &&
                    (from == null || n.date >= from) &&
                    (to == null || n.date <= to)
            }.sortedBy { it.date }
            return notes.map { note ->
                SignalDto(
                    noteId = note.id,
                    recipientId = note.recipientId,
                    date = note.date,
                    fallReported = note.fall,
                    pain = note.pain,
                    medication = note.medicationTaken,
                    medicationUnverified = note.medicationTaken == "unsure",
                    poorAppetite = note.appetite == "poor",
                    text = note.text,
                )
            }
        }

        override suspend fun summarize(body: SummaryRequest): SummaryDto {
            // Local mode generates summaries on-device through the AI path.
            // This server-style fallback is reached when no API key is
            // configured, so there is no generated text — but the
            // deterministic safety flags must still be returned. An empty
            // payload here would silently hide a reported fall.
            val today = LocalDate.now()
            val from = SummaryWindow.start(today, body.periodDays).toString()
            val to = today.toString()
            val scopedSignals = signals(body.recipientId, from, to)
            return SummaryDto(
                id = UUID.randomUUID().toString(),
                recipientId = body.recipientId,
                periodDays = body.periodDays,
                text = "",
                redFlags = SafetySignalEvaluator.evaluate(scopedSignals),
                evidence = emptyList(),
                uncertainties = listOf(
                    SummaryDto.UncertaintyDto(
                        topic = "ai_not_configured",
                        detail = "",
                    ),
                ),
            )
        }
    }

    override suspend fun plans(): PlanApi = object : PlanApi {
        override suspend fun list(): List<PlanDto> =
            planDao.all().map { entity ->
                PlanDto(
                    id = entity.id,
                    recipientId = entity.recipientId,
                    versions = json.decodeFromString(entity.versionsJson),
                )
            }

        override suspend fun suggest(body: SuggestPlanRequest): PlanDto {
            requireWritableRecipient(body.recipientId)
            val plan = PlanDto(
                id = UUID.randomUUID().toString(),
                recipientId = body.recipientId,
                versions = listOf(
                    PlanVersionDto(
                        version = 1,
                        status = "Suggested",
                        items = body.items,
                        createdAt = LocalDate.now().toString(),
                        reason = "",
                    ),
                ),
            )
            savePlan(plan)
            return plan
        }

        override suspend fun versions(id: String): List<PlanVersionDto> =
            planDao.byId(id)?.let { json.decodeFromString<List<PlanVersionDto>>(it.versionsJson) }
                ?: throw NoSuchElementException("plan $id")

        override suspend fun appendVersion(id: String, body: AppendVersionRequest): PlanDto {
            val entity = planDao.byId(id) ?: throw NoSuchElementException("plan $id")
            requireWritableRecipient(entity.recipientId)
            val versions = json.decodeFromString<MutableList<PlanVersionDto>>(entity.versionsJson)
            val latest = versions.maxByOrNull { it.version } ?: throw NoSuchElementException("plan $id")
            val items = body.items ?: latest.items
            val status = body.status ?: PlanStateMachine.EDITED_ACCEPTED
            val updatedVersions = try {
                PlanStateMachine.append(versions, status, items, LocalDate.now().toString())
            } catch (e: IllegalPlanTransitionException) {
                throw planTransitionError(e)
            }
            val updated = PlanDto(id = entity.id, recipientId = entity.recipientId, versions = updatedVersions)
            savePlan(updated)
            return updated
        }

        override suspend fun accept(id: String): PlanDto = transition(id, PlanStateMachine.ACCEPTED)
        override suspend fun editAccept(id: String): PlanDto = transition(id, PlanStateMachine.EDITED_ACCEPTED)
        override suspend fun dismiss(id: String): PlanDto = transition(id, PlanStateMachine.DISMISSED)
        override suspend fun archive(id: String): PlanDto = transition(id, PlanStateMachine.ARCHIVED)

        private suspend fun transition(id: String, status: String): PlanDto {
            val entity = planDao.byId(id) ?: throw NoSuchElementException("plan $id")
            requireWritableRecipient(entity.recipientId)
            val versions = json.decodeFromString<MutableList<PlanVersionDto>>(entity.versionsJson)
            if (versions.isEmpty()) throw NoSuchElementException("plan $id")
            val updatedVersions = try {
                PlanStateMachine.transition(versions, status)
            } catch (e: IllegalPlanTransitionException) {
                throw planTransitionError(e)
            }
            val updated = PlanDto(id = entity.id, recipientId = entity.recipientId, versions = updatedVersions)
            savePlan(updated)
            return updated
        }

        private suspend fun savePlan(plan: PlanDto) {
            planDao.upsert(
                PlanEntity(
                    id = plan.id,
                    recipientId = plan.recipientId,
                    versionsJson = json.encodeToString(plan.versions),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
