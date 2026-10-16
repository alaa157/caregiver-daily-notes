package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.local.NoteDao
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.OutboxDao
import com.caregiver.mobile.data.local.OutboxEntity
import com.caregiver.mobile.data.local.OutboxStatus
import com.caregiver.mobile.data.local.toCreateRequest
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
import retrofit2.HttpException

/** How one drain pass (Task 8c) ended; the worker maps these to WorkManager results. */
enum class OutboxDrain {
    /** Nothing left to retry automatically. */
    DONE,

    /** The server was unreachable or erroring: retry with backoff. */
    RETRY,

    /** The session is unauthorized: stop until the next trigger. */
    STOPPED,
}

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
 * plus the outbox — an empty cached copy still counts (Task 8a review fix),
 * so only a missing cache at all (demo builds) makes the failure propagate
 * as before Task 8a. A null [dao] (demo builds) makes every call a plain
 * passthrough: demo data never touches disk.
 *
 * [create]: offline saves enqueue the note in the outbox, ask
 * [scheduleOutboxSync] to schedule the drain, and return it as a pending
 * [NoteDto] (client UUID as id) — the "unsent post" model, so the editor,
 * note-saved and detail screens keep working.
 *
 * [drainOutbox] (Task 8c) is what the WorkManager worker runs: it uploads
 * the outbox oldest first. A success deletes the row and caches the server
 * copy under the client local id; a transport failure or 5xx keeps the row
 * pending for the backoff retry; 401/403 stops the pass without burning
 * retries; any other rejection marks the row failed and the pass goes on.
 * Rows saved while a pass runs are picked up by the same pass.
 *
 * [detail]: an id that belongs to the outbox is served locally (the server
 * cannot know it yet); a drained id resolves through the cached localId
 * mapping to its server row; every other id keeps the Task 8a behavior.
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
    /**
     * Asks for an outbox drain; the app graph points this at the
     * WorkManager worker (Task 8c). Tests can leave it unwired.
     */
    private val scheduleOutboxSync: () -> Unit = {},
) {
    /** Observes the cached notes, newest first; empty in demo mode (no cache). */
    fun observeAll(): Flow<List<NoteDto>> =
        dao?.observeAll()?.map { rows -> rows.map { it.toDto() } } ?: flowOf(emptyList())

    /**
     * Observed statuses of the rows in the outbox (Task 8d top-bar status);
     * always empty in demo mode, which has no outbox at all.
     */
    fun observeOutboxStatuses(): Flow<List<String>> =
        outboxDao?.observeAll()?.map { rows -> rows.map { it.status } } ?: flowOf(emptyList())

    suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> {
        val api = apis.notes()
        return try {
            val fresh = api.history(recipientId, from, to)
            cacheWrite { dao?.replaceScope(recipientId, from, to, fresh.map { it.toEntity() }) }
            mergePending(recipientId, from, to, fresh)
        } catch (e: IOException) {
            // Task 8a review fix: a cached scope is served even when empty;
            // only a missing cache at all (demo builds) propagates.
            val cached = serveCached(e) { dao?.query(recipientId, from, to)?.map { it.toDto() } }
            mergeDates(pendingFor(recipientId, from, to), cached)
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
            cacheWrite { cacheNote(created.toEntity()) }
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
            // Drain as soon as connectivity returns (Task 8c).
            scheduleOutboxSync()
            row.toNoteDto()
        }
    }

    /**
     * Detail for an outbox id is served from the outbox: nothing server-side
     * knows that id yet, and this keeps note-saved / detail screens working
     * offline. A drained id resolves through the cached mapping to its
     * server row (Task 8c), so screens still holding the client id keep
     * working after a sync. Every other id always comes from the network:
     * addenda are not cached, so a cached fallback would silently drop
     * corrections. A successful call still refreshes the cached note fields.
     */
    suspend fun detail(id: String): NoteDetailDto {
        outboxDao?.byLocalId(id)?.let { return it.toNoteDetailDto() }
        val detail = apis.notes().detail(serverIdFor(id))
        cacheWrite { cacheNote(detail.toEntity()) }
        return detail
    }

    /**
     * A drained outbox id resolves to its server row first (Task 8c);
     * appends pass straight through otherwise (addenda are not cached —
     * see 8a notes).
     */
    suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto =
        apis.notes().append(serverIdFor(id), body)

    /**
     * Uploads every outbox row, oldest first (Task 8c), on behalf of the
     * unique WorkManager drain. Each row moves to [OutboxStatus.SYNCING]
     * while its create call runs; on success the row is deleted and the
     * server copy is cached under the client [OutboxEntity.localId] so the
     * note keeps its place in timelines and its old id keeps resolving.
     *
     * Failure contract: a transport failure ([IOException]) or a 5xx puts
     * the row back to [OutboxStatus.PENDING] and stops the pass with
     * [OutboxDrain.RETRY] (WorkManager retries with exponential backoff);
     * 401/403 stops the pass with [OutboxDrain.STOPPED] and leaves the row
     * pending (the session, not the note, needs attention); any other HTTP
     * rejection marks the row [OutboxStatus.FAILED] and the pass goes on,
     * because the remaining notes can still upload. Failed rows are
     * retried by the next drain (app start, a new offline save, or the
     * Task 8d retry tap). A retried upload cannot be deduplicated
     * server-side (no idempotency key — docs/api/android-gaps.md), so a
     * duplicate-day 422 ends as a failed row, not as a silent success.
     *
     * Rows saved while a pass runs are picked up by the same pass, so a
     * save racing the drain never waits for another trigger.
     */
    suspend fun drainOutbox(): OutboxDrain {
        // Demo graphs have no outbox: nothing to upload, ever.
        val outbox = outboxDao ?: return OutboxDrain.DONE
        val attempted = mutableSetOf<String>()
        while (true) {
            val rows = outbox.query(null, null, null)
                .sortedBy { it.createdAt }
                .filter { it.localId !in attempted }
            if (rows.isEmpty()) return OutboxDrain.DONE
            for (row in rows) {
                attempted += row.localId
                outbox.updateStatus(row.localId, OutboxStatus.SYNCING)
                val created: NoteDto
                try {
                    created = apis.notes().create(row.toCreateRequest())
                } catch (e: IOException) {
                    outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                    return OutboxDrain.RETRY
                } catch (e: HttpException) {
                    when {
                        e.code() == 401 || e.code() == 403 -> {
                            outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                            return OutboxDrain.STOPPED
                        }
                        e.code() >= 500 -> {
                            outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                            return OutboxDrain.RETRY
                        }
                        else -> {
                            outbox.updateStatus(row.localId, OutboxStatus.FAILED)
                            continue
                        }
                    }
                }
                cacheWrite { cacheNote(created.toEntity().copy(localId = row.localId)) }
                outbox.delete(row.localId)
            }
        }
    }

    /**
     * Writes a server row into the cache without dropping an existing
     * localId mapping (Task 8c): a refresh or re-fetch must not orphan an
     * id that a screen still holds.
     */
    private suspend fun cacheNote(entity: NoteEntity) {
        val existingLocalId = dao?.byServerId(entity.serverId)?.localId
        dao?.insertAll(listOf(entity.copy(localId = entity.localId ?: existingLocalId)))
    }

    /** Server id for a client localId whose outbox row already drained (Task 8c). */
    private suspend fun serverIdFor(id: String): String =
        dao?.byLocalId(id)?.serverId ?: id

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
