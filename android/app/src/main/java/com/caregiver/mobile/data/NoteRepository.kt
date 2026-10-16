package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.local.NoteDao
import com.caregiver.mobile.data.local.toDto
import com.caregiver.mobile.data.local.toEntity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed repository for notes (Task 8a): it reads from Room (the
 * [observeAll] Flow) and refreshes from the network when online, writing
 * the response into Room.
 *
 * A successful history call refreshes the cached copy for the queried
 * scope (null filters = wildcard, matching the endpoint) and returns the
 * live result. When the transport fails ([IOException], i.e. the device is
 * offline), the same scope is served from the last cached copy so
 * timelines keep showing the last data; with nothing cached the failure
 * propagates exactly as before Task 8a, so the existing error/retry UI is
 * unchanged. A null [dao] (demo builds) makes every call a plain
 * passthrough: demo data never touches disk.
 *
 * TODO(api-gap): the API exposes no updated-since/deleted marker, so sync
 * is refresh-and-replace per scope rather than incremental (see
 * docs/api/android-gaps.md).
 */
class NoteRepository(
    private val apis: BackendApis,
    private val dao: NoteDao?,
) {
    /** Observes the cached notes, newest first; empty in demo mode (no cache). */
    fun observeAll(): Flow<List<NoteDto>> =
        dao?.observeAll()?.map { rows -> rows.map { it.toDto() } } ?: flowOf(emptyList())

    suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> {
        val api = apis.notes()
        return try {
            val fresh = api.history(recipientId, from, to)
            cacheWrite { dao?.replaceScope(recipientId, from, to, fresh.map { it.toEntity() }) }
            fresh
        } catch (e: IOException) {
            serveCached(e) { dao?.query(recipientId, from, to)?.map { it.toDto() } }
        }
    }

    /**
     * Offline creation is Task 8b (outbox); a transport failure surfaces to
     * the editor exactly as today. A successful create refreshes the cached
     * row so the note is visible before the next history refresh lands.
     */
    suspend fun create(body: CreateNoteRequest): NoteDto {
        val created = apis.notes().create(body)
        cacheWrite { dao?.insertAll(listOf(created.toEntity())) }
        return created
    }

    /**
     * Detail always comes from the network: addenda are not cached, so a
     * cached fallback would silently drop corrections. A successful call
     * still refreshes the cached note fields.
     */
    suspend fun detail(id: String): NoteDetailDto {
        val detail = apis.notes().detail(id)
        cacheWrite { dao?.insertAll(listOf(detail.toEntity())) }
        return detail
    }

    /** Addenda are not cached in 8a; appends pass straight through. */
    suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto =
        apis.notes().append(id, body)
}
