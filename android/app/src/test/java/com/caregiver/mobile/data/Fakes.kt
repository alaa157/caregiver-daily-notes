package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SummaryApi
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
    override suspend fun auth(): AuthApi = authApi
    override suspend fun recipients(): RecipientApi = error("unused")
    override suspend fun notes(): NoteApi = error("unused")
    override suspend fun summaries(): SummaryApi = error("unused")
    override suspend fun plans(): PlanApi = error("unused")
}
