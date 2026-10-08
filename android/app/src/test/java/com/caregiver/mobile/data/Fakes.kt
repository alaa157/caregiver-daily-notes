package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SummaryApi
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Shared fakes for repository and ViewModel tests. */
class FakeAuthApi : AuthApi {
    var lastLogin: LoginRequest? = null
    var loginHandler: suspend (LoginRequest) -> AuthResponse = { error("no login handler") }
    var registerHandler: suspend (RegisterRequest) -> AuthResponse = { error("no register handler") }

    override suspend fun login(body: LoginRequest): AuthResponse {
        lastLogin = body
        return loginHandler(body)
    }

    override suspend fun register(body: RegisterRequest): AuthResponse = registerHandler(body)

    companion object {
        fun httpError(code: Int, errorCode: String, message: String = errorCode): HttpException =
            HttpException(
                Response.error<String>(
                    code,
                    """{"code":"$errorCode","message":"$message"}"""
                        .toResponseBody("application/json".toMediaType()),
                ),
            )
    }
}

class FakeBackendApis(val authApi: AuthApi) : BackendApis {
    var recipientApi: RecipientApi = FakeRecipientApi()
    var noteApi: NoteApi = FakeNoteApi()
    var summaryApi: SummaryApi = FakeSummaryApi()

    override suspend fun auth(): AuthApi = authApi
    override suspend fun recipients(): RecipientApi = recipientApi
    override suspend fun notes(): NoteApi = noteApi
    override suspend fun summaries(): SummaryApi = summaryApi
    override suspend fun plans(): PlanApi = error("unused")
}

class FakeRecipientApi : RecipientApi {
    var listHandler: suspend () -> List<RecipientDto> = { error("no list handler") }
    var lastCreatedName: String? = null
    var createHandler: suspend (CreateRecipientRequest) -> RecipientDto = { error("no create handler") }

    override suspend fun list(): List<RecipientDto> = listHandler()
    override suspend fun create(body: CreateRecipientRequest): RecipientDto {
        lastCreatedName = body.name
        return createHandler(body)
    }
    override suspend fun get(id: String): RecipientDto = error("unused")
    override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto = error("unused")
}

class FakeNoteApi : NoteApi {
    var historyHandler: suspend (String?, String?, String?) -> List<NoteDto> = { _, _, _ -> error("no history handler") }

    override suspend fun create(body: CreateNoteRequest): NoteDto = error("unused")
    override suspend fun detail(id: String): NoteDetailDto = error("unused")
    override suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto = error("unused")
    override suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> =
        historyHandler(recipientId, from, to)
}

class FakeSummaryApi : SummaryApi {
    var signalsHandler: suspend (String, String?, String?) -> List<SignalDto> =
        { _, _, _ -> error("no signals handler") }

    override suspend fun signals(id: String, from: String?, to: String?): List<SignalDto> =
        signalsHandler(id, from, to)
    override suspend fun summarize(body: SummaryRequest): SummaryDto = error("unused")
}
