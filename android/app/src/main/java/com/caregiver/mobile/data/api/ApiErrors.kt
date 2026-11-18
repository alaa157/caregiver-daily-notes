package com.caregiver.mobile.data.api

import retrofit2.HttpException

/**
 * Reads the {code, message} envelope out of a failed call. Null when the
 * body is absent or unparsable — callers fall back to generic copy.
 */
object ApiErrors {
    fun parse(e: HttpException): ErrorResponse? {
        return try {
            e.response()?.errorBody()?.string()?.let {
                ApiClient.json.decodeFromString<ErrorResponse>(ErrorResponse.serializer(), it)
            }
        } catch (_: Exception) {
            null
        }
    }
}
