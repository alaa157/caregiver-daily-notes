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

/**
 * The 409 for a plan whose version chain changed between this write's read
 * and its compare-and-set write. The caller must reload the plan and retry;
 * nothing was overwritten.
 */
fun planConflictError(planId: String): HttpException = HttpException(
    Response.error<String>(
        409,
        """{"code":"PLAN_CONFLICT","message":"Plan $planId changed while saving; reload and retry."}"""
            .toResponseBody("application/json".toMediaType()),
    ),
)
