package com.caregiver.mobile.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** GET /api/recipients/{id}/signals and POST /api/summaries. */
interface SummaryApi {
    @GET("api/recipients/{id}/signals")
    suspend fun signals(
        @Path("id") id: String,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): List<SignalDto>

    @POST("api/summaries")
    suspend fun summarize(@Body body: SummaryRequest): SummaryDto
}

@Serializable
data class SignalDto(
    val noteId: String,
    val recipientId: String,
    val date: String,
    val fallReported: Boolean,
    val pain: Int?,
    val medication: String,
    val medicationUnverified: Boolean,
    val poorAppetite: Boolean,
    val text: String,
)

@Serializable
data class SummaryRequest(val recipientId: String, val periodDays: Int)

@Serializable
data class SummaryDto(
    val id: String,
    val recipientId: String,
    val periodDays: Int,
    val text: String,
    val redFlags: List<String>,
    val evidence: List<EvidenceDto>,
    val uncertainties: List<UncertaintyDto>,
) {
    @Serializable
    data class EvidenceDto(val noteId: String, val quote: String)

    @Serializable
    data class UncertaintyDto(val topic: String, val detail: String)
}
