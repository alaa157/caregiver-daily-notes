package com.caregiver.mobile.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * POST /api/notes, GET /api/notes/{id}, POST /api/notes/{id}/addenda,
 * GET /api/notes?recipientId&from&to (ISO dates; nulls are omitted).
 */
interface NoteApi {
    @POST("api/notes")
    suspend fun create(@Body body: CreateNoteRequest): NoteDto

    @GET("api/notes/{id}")
    suspend fun detail(@Path("id") id: String): NoteDetailDto

    @POST("api/notes/{id}/addenda")
    suspend fun append(@Path("id") id: String, @Body body: CreateAddendumRequest): AddendumDto

    @GET("api/notes")
    suspend fun history(
        @Query("recipientId") recipientId: String?,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): List<NoteDto>
}

@Serializable
data class NoteDto(
    val id: String,
    val recipientId: String,
    val date: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

@Serializable
data class NoteDetailDto(
    val id: String,
    val recipientId: String,
    val date: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
    val addenda: List<AddendumDto>,
)

@Serializable
data class CreateNoteRequest(
    val recipientId: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

@Serializable
data class CreateAddendumRequest(val text: String)

@Serializable
data class AddendumDto(val id: String, val noteId: String, val createdAt: String, val text: String)
