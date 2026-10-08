package com.caregiver.mobile.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** GET/POST /api/recipients, GET/PUT /api/recipients/{id}. */
interface RecipientApi {
    @GET("api/recipients")
    suspend fun list(): List<RecipientDto>

    @POST("api/recipients")
    suspend fun create(@Body body: CreateRecipientRequest): RecipientDto

    @GET("api/recipients/{id}")
    suspend fun get(@Path("id") id: String): RecipientDto

    @PUT("api/recipients/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateRecipientRequest): RecipientDto
}

@Serializable
data class RecipientDto(val id: String, val name: String, val active: Boolean)

@Serializable
data class CreateRecipientRequest(val name: String)

@Serializable
data class UpdateRecipientRequest(val active: Boolean)
