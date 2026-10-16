package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.local.NoteDao
import com.caregiver.mobile.data.local.OutboxDao
import com.caregiver.mobile.data.local.OutboxEntity
import com.caregiver.mobile.data.local.toDto
import com.caregiver.mobile.data.local.toEntity
import com.caregiver.mobile.data.local.toNoteDetailDto
import com.caregiver.mobile.data.local.toNoteDto
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed repository for notes (Task 8a) with the offline outbox
 * (Task 8b): it reads from Room ([observeAll]), refreshes from the network
 * when online, writing the response into Room, and keeps notes that could
 * not be uploaded in the outbox so timelines can show them as pending.
 *
 * [history]: a successful call refreshes the cached copy for the queried
 * scope (null filters = wildcard, matching the endpoint) and returns it
 * merged with that scope's pending records; a transport failure
 * ([IOException], i.e. offline) serves the same scope from the cached copy
 * plus the outbox. With neither a cached copy nor pending records the
 * failure propagates exactly as before Task 8a, so the existing error/retry
 * UI is unchanged. A null [dao] (demo builds) makes every call a plain
 * passthrough: demo data never touches disk.
 *
 * [create]: offline saves enqueue the note in the outbox and return it as a
 * pending [NoteDto] (client UUID as id) — the "unsent post" model, so the
 * editor, note-saved and detail screens keep working. Task 8c drains the
 * outbox; this task records and displays only.
 *
 * [detail]: an id that belongs to the outbox is served locally (the server
 * cannot know it yet); every other id keeps the Task 8a behavior.
 *
 * TODO(api-gap): the API exposes no updated-since/deleted marker, so sync
 * is refresh-and-replace per scope rather than incremental; and
 * `POST /api/notes` accepts no client id or idempotency key, so a retried
 * upload cannot be deduplicated server-side (see docs/api/android-gaps.md).
 */
class NoteRepository(
    private val apis: BackendApis,
    private val dao: NoteDao?,
    private val outboxDao: OutboxDao? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /** Observes the cached notes, newest first; empty in demo mode (no cache). */
    fun observeAll(): Flow<List<NoteDto>> =
        dao?.observeAll()?.map { rows -> rows.map { it.toDto() } } ?: flowOf(emptyList())

    suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> {
        val api = apis.notes()
        return try {
            val fresh = api.history(recipientId, from, to)
            cacheWrite { dao?.replaceScope(recipientId, from, to, fresh.map { it.toEntity() }) }
            mergePending(recipientId, from, to, fresh)
        } catch (e: IOException) {
            val cached = dao?.query(recipientId, from, to)?.map { it.toDto() }.orEmpty()
            val pending = pendingFor(recipientId, from, to)
            // Without a cached copy and without pending records, the failure
            // surfaces as before Task 8a.
            if (cached.isEmpty() && pending.isEmpty()) throw e
            mergeDates(pending, cached)
        }
    }

    /**
     * Offline creation (Task 8b) enqueues exactly what create() would have
     * sent and returns the pending copy; a successful create refreshes the
     * cached row so the note is visible before the next history refresh.
     */
    suspend fun create(body: CreateNoteRequest): NoteDto {
        return try {
            val created = apis.notes().create(body)
            cacheWrite { dao?.insertAll(listOf(created.toEntity())) }
            created
        } catch (e: IOException) {
            // Demo graphs have no outbox: keep the pre-8b behavior exactly.
            val outbox = outboxDao ?: throw e
            val row = OutboxEntity(
                // Client-side identity for the pending record. The API takes
                // no idempotency key, so this UUID never leaves the device
                // (docs/api/android-gaps.md); Task 8c uploads the fields.
                localId = UUID.randomUUID().toString(),
                recipientId = body.recipientId,
                mood = body.mood,
                appetite = body.appetite,
                sleep = body.sleep,
                mobility = body.mobility,
                medicationTaken = body.medicationTaken,
                pain = body.pain,
                fall = body.fall,
                text = body.text,
                date = LocalDate.now(clock).toString(),
                createdAt = clock.millis(),
            )
            outbox.insert(row)
            row.toNoteDto()
        }
    }

    /**
     * Detail for an outbox id is served from the outbox: nothing server-side
     * knows that id yet, and this keeps note-saved / detail screens working
     * offline. Every other id always comes from the network: addenda are
     * not cached, so a cached fallback would silently drop corrections. A
     * successful call still refreshes the cached note fields.
     */
    suspend fun detail(id: String): NoteDetailDto {
        outboxDao?.byLocalId(id)?.let { return it.toNoteDetailDto() }
        val detail = apis.notes().detail(id)
        cacheWrite { dao?.insertAll(listOf(detail.toEntity())) }
        return detail
    }

    /** Addenda are not cached; appends pass straight through (see 8a notes). */
    suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto =
        apis.notes().append(id, body)

    private suspend fun pendingFor(recipientId: String?, from: String?, to: String?): List<NoteDto> =
        outboxDao?.query(recipientId, from, to)?.map { it.toNoteDto() }.orEmpty()

    private suspend fun mergePending(
        recipientId: String?,
        from: String?,
        to: String?,
        server: List<NoteDto>,
    ): List<NoteDto> = mergeDates(pendingFor(recipientId, from, to), server)

    /**
     * Pending notes keep their timeline slot until Task 8c syncs them:
     * newest first, and first within a date tie because they were saved
     * after the server rows already in the list.
     */
    private fun mergeDates(pending: List<NoteDto>, server: List<NoteDto>): List<NoteDto> =
        if (pending.isEmpty()) server else (pending + server).sortedByDescending { it.date }
}
