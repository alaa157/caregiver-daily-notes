package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.SummaryApi
import com.caregiver.mobile.data.api.UpdateRecipientRequest

/**
 * Puts the Room-backed repositories (Task 8a) behind the app's existing
 * [BackendApis] seam, so every current caller — ViewModels, AuthRepository,
 * screens — keeps its contract while recipients/notes reads become
 * offline-capable. Auth, plans and summaries are out of 8a's scope and
 * pass straight through. In demo builds the repositories carry no DAO, so
 * this is a pure passthrough over [com.caregiver.mobile.data.demo.DemoBackendApis]
 * with no disk writes.
 */
class RoomBackedBackendApis(
    private val live: BackendApis,
    private val recipients: RecipientRepository,
    private val notes: NoteRepository,
) : BackendApis {
    override suspend fun auth(): AuthApi = live.auth()

    override suspend fun summaries(): SummaryApi = live.summaries()

    override suspend fun plans(): PlanApi = live.plans()

    override suspend fun recipients(): RecipientApi = object : RecipientApi {
        override suspend fun list(): List<RecipientDto> = recipients.list()

        override suspend fun create(body: CreateRecipientRequest): RecipientDto =
            recipients.create(body)

        override suspend fun get(id: String): RecipientDto = recipients.get(id)

        override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto =
            recipients.update(id, body)
    }

    override suspend fun notes(): NoteApi = object : NoteApi {
        override suspend fun create(body: CreateNoteRequest): NoteDto = notes.create(body)

        override suspend fun detail(id: String): NoteDetailDto = notes.detail(id)

        override suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto =
            notes.append(id, body)

        override suspend fun history(
            recipientId: String?,
            from: String?,
            to: String?,
        ): List<NoteDto> = notes.history(recipientId, from, to)
    }
}
