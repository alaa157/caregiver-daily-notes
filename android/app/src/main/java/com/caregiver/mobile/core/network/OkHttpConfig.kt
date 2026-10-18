package com.caregiver.mobile.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpConfig {
    fun defaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
