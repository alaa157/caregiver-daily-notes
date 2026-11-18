package com.caregiver.mobile.core.network

/**
 * In-memory copy of the auth token for the OkHttp interceptor. DataStore
 * reads are suspending, so the repository mirrors the persisted token here on
 * every sign-in/out (and re-seeds it on first authed call after a restart).
 * Never logged, never persisted by this holder.
 */
class TokenHolder {
    @Volatile
    var token: String? = null
}
