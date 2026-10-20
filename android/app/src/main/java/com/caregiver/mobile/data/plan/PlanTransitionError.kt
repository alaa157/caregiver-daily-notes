package com.caregiver.mobile.data.plan

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * The 422 the server answers an illegal plan transition with. The screens
 * read the status code, never the body.
 */
fun planTransitionError(error: IllegalPlanTransitionException): HttpException = HttpException(
    Response.error<String>(
        422,
        """{"code":"ILLEGAL_TRANSITION","message":"${error.message.orEmpty().replace("\"", "'")}"}"""
            .toResponseBody("application/json".toMediaType()),
    ),
)
