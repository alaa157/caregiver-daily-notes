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
import com.caregiver.mobile.data.api.PlanDto
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
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
    var planApi: PlanApi = FakePlanApi()

    override suspend fun auth(): AuthApi = authApi
    override suspend fun recipients(): RecipientApi = recipientApi
    override suspend fun notes(): NoteApi = noteApi
    override suspend fun summaries(): SummaryApi = summaryApi
    override suspend fun plans(): PlanApi = planApi
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
    var lastHistory: Triple<String?, String?, String?>? = null
    val historyCalls = mutableListOf<Triple<String?, String?, String?>>()
    var createHandler: suspend (CreateNoteRequest) -> NoteDto = { error("no create handler") }
    var lastCreate: CreateNoteRequest? = null
    var detailHandler: suspend (String) -> NoteDetailDto = { error("no detail handler") }
    var detailCalls = 0
    var appendHandler: suspend (String, CreateAddendumRequest) -> AddendumDto = { _, _ -> error("no append handler") }
    var lastAppend: Pair<String, CreateAddendumRequest>? = null

    override suspend fun create(body: CreateNoteRequest): NoteDto {
        lastCreate = body
        return createHandler(body)
    }
    override suspend fun detail(id: String): NoteDetailDto {
        detailCalls++
        return detailHandler(id)
    }
    override suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto {
        lastAppend = id to body
        return appendHandler(id, body)
    }
    override suspend fun history(recipientId: String?, from: String?, to: String?): List<NoteDto> {
        lastHistory = Triple(recipientId, from, to)
        historyCalls.add(Triple(recipientId, from, to))
        return historyHandler(recipientId, from, to)
    }
}

class FakeSummaryApi : SummaryApi {
    var signalsHandler: suspend (String, String?, String?) -> List<SignalDto> =
        { _, _, _ -> error("no signals handler") }
    var signalsCalls = 0
    var summarizeHandler: suspend (SummaryRequest) -> SummaryDto = { error("no summarize handler") }
    var lastSummarize: SummaryRequest? = null

    override suspend fun signals(id: String, from: String?, to: String?): List<SignalDto> {
        signalsCalls++
        return signalsHandler(id, from, to)
    }
    override suspend fun summarize(body: SummaryRequest): SummaryDto {
        lastSummarize = body
        return summarizeHandler(body)
    }
}

class FakePlanApi : PlanApi {
    var listHandler: suspend () -> List<PlanDto> = { error("no plan list handler") }
    var versionsHandler: suspend (String) -> List<PlanVersionDto> = { error("no versions handler") }
    var lastVersionsId: String? = null
    var appendHandler: suspend (String, AppendVersionRequest) -> PlanDto =
        { _, _ -> error("no append handler") }
    var lastAppend: Pair<String, AppendVersionRequest>? = null
    var acceptHandler: suspend (String) -> PlanDto = { error("no accept handler") }
    var dismissHandler: suspend (String) -> PlanDto = { error("no dismiss handler") }
    var archiveHandler: suspend (String) -> PlanDto = { error("no archive handler") }
    var lastTransition: Pair<String, String>? = null

    override suspend fun list(): List<PlanDto> = listHandler()
    override suspend fun suggest(body: SuggestPlanRequest): PlanDto = error("unused")
    override suspend fun versions(id: String): List<PlanVersionDto> {
        lastVersionsId = id
        return versionsHandler(id)
    }
    override suspend fun appendVersion(id: String, body: AppendVersionRequest): PlanDto {
        lastAppend = id to body
        return appendHandler(id, body)
    }
    override suspend fun accept(id: String): PlanDto {
        lastTransition = id to "accept"
        return acceptHandler(id)
    }
    override suspend fun editAccept(id: String): PlanDto = error("unused")
    override suspend fun dismiss(id: String): PlanDto {
        lastTransition = id to "dismiss"
        return dismissHandler(id)
    }
    override suspend fun archive(id: String): PlanDto {
        lastTransition = id to "archive"
        return archiveHandler(id)
    }
}
