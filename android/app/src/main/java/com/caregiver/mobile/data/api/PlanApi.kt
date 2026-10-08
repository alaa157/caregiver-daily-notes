package com.caregiver.mobile.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** GET /api/plans, POST /api/plans/suggest, versions, and the four transitions. */
interface PlanApi {
    @GET("api/plans")
    suspend fun list(): List<PlanDto>

    @POST("api/plans/suggest")
    suspend fun suggest(@Body body: SuggestPlanRequest): PlanDto

    @GET("api/plans/{id}/versions")
    suspend fun versions(@Path("id") id: String): List<PlanVersionDto>

    @POST("api/plans/{id}/versions")
    suspend fun appendVersion(@Path("id") id: String, @Body body: AppendVersionRequest): PlanDto

    @POST("api/plans/{id}/accept")
    suspend fun accept(@Path("id") id: String): PlanDto

    @POST("api/plans/{id}/edit-accept")
    suspend fun editAccept(@Path("id") id: String): PlanDto

    @POST("api/plans/{id}/dismiss")
    suspend fun dismiss(@Path("id") id: String): PlanDto

    @POST("api/plans/{id}/archive")
    suspend fun archive(@Path("id") id: String): PlanDto
}

@Serializable
data class PlanDto(val id: String, val recipientId: String, val versions: List<PlanVersionDto>)

@Serializable
data class PlanVersionDto(
    val version: Int,
    val status: String,
    val items: List<String>,
    val createdAt: String,
    val reason: String,
)

@Serializable
data class SuggestPlanRequest(val recipientId: String, val items: List<String>)

@Serializable
data class AppendVersionRequest(
    val status: String? = null,
    val items: List<String>? = null,
)
