package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.data.local.RecipientDao
import com.caregiver.mobile.data.local.toDto
import com.caregiver.mobile.data.local.toEntity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed repository for recipients (Task 8a): it reads from Room
 * (the [observeAll] Flow) and refreshes from the network when online,
 * writing the response into Room.
 *
 * A successful call refreshes the cached copy and returns the live result.
 * When the transport fails ([IOException], i.e. the device is offline),
 * the same query is served from the last cached copy so the app keeps
 * showing the last data; with nothing cached the failure propagates
 * exactly as before Task 8a, so the existing error/retry UI is unchanged.
 * A null [dao] (demo builds) makes every call a plain passthrough: demo
 * data never touches disk.
 *
 * TODO(api-gap): the API exposes no updated-since/deleted marker, so sync
 * is refresh-and-replace rather than incremental (see
 * docs/api/android-gaps.md).
 */
class RecipientRepository(
    private val apis: BackendApis,
    private val dao: RecipientDao?,
) {
    /** Observes the cached recipients in server order; empty in demo mode (no cache). */
    fun observeAll(): Flow<List<RecipientDto>> =
        dao?.observeAll()?.map { rows -> rows.map { it.toDto() } } ?: flowOf(emptyList())

    suspend fun list(): List<RecipientDto> {
        val api = apis.recipients()
        return try {
            val fresh = api.list()
            cacheWrite { dao?.replaceAll(fresh.map { it.toEntity() }) }
            fresh
        } catch (e: IOException) {
            serveCached(e) { dao?.all()?.map { it.toDto() } }
        }
    }

    suspend fun get(id: String): RecipientDto {
        val api = apis.recipients()
        return try {
            val fresh = api.get(id)
            cacheWrite { dao?.insertAll(listOf(fresh.toEntity())) }
            fresh
        } catch (e: IOException) {
            dao?.byServerId(id)?.toDto() ?: throw e
        }
    }

    /** Offline creation is Task 8b (outbox); transport failures surface as today. */
    suspend fun create(body: CreateRecipientRequest): RecipientDto {
        val created = apis.recipients().create(body)
        cacheWrite { dao?.insertAll(listOf(created.toEntity())) }
        return created
    }

    suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto {
        val updated = apis.recipients().update(id, body)
        cacheWrite { dao?.insertAll(listOf(updated.toEntity())) }
        return updated
    }
}
