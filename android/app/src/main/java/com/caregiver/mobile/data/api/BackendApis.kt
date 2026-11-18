package com.caregiver.mobile.data.api

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Lazily built service set. Rebuilds when the stored base URL changes, so a
 * server-URL edit (Task 7) applies to the very next call.
 */
interface BackendApis {
    suspend fun auth(): AuthApi
    suspend fun recipients(): RecipientApi
    suspend fun notes(): NoteApi
    suspend fun summaries(): SummaryApi
    suspend fun plans(): PlanApi
}

class RetrofitBackendApis(
    private val settings: SettingsStore,
    private val tokens: TokenHolder,
) : BackendApis {
    private var cachedBaseUrl: String? = null
    private var cached: RetrofitHolder? = null

    private data class RetrofitHolder(
        val auth: AuthApi,
        val recipients: RecipientApi,
        val notes: NoteApi,
        val summaries: SummaryApi,
        val plans: PlanApi,
    )

    private suspend fun holder(): RetrofitHolder {
        val baseUrl = settings.baseUrl.first()
        if (cached == null || cachedBaseUrl != baseUrl) {
            val retrofit = ApiClient.retrofit(baseUrl, tokens)
            cached = RetrofitHolder(
                auth = retrofit.create(AuthApi::class.java),
                recipients = retrofit.create(RecipientApi::class.java),
                notes = retrofit.create(NoteApi::class.java),
                summaries = retrofit.create(SummaryApi::class.java),
                plans = retrofit.create(PlanApi::class.java),
            )
            cachedBaseUrl = baseUrl
        }
        return cached!!
    }

    override suspend fun auth(): AuthApi = holder().auth
    override suspend fun recipients(): RecipientApi = holder().recipients
    override suspend fun notes(): NoteApi = holder().notes
    override suspend fun summaries(): SummaryApi = holder().summaries
    override suspend fun plans(): PlanApi = holder().plans
}
