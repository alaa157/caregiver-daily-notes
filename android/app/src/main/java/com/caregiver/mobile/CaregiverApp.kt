package com.caregiver.mobile

import android.app.Application
import android.content.Context
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.RetrofitBackendApis

class CaregiverApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** Manual composition root: settings, token mirror, API provider, auth. */
class AppGraph(val context: Context) {
    val tokens = TokenHolder()
    val settings: SettingsStore by lazy { SettingsStore.create(context) }
    val apis: BackendApis by lazy { RetrofitBackendApis(settings, tokens) }
    val auth: AuthRepository by lazy { AuthRepository(apis, settings, tokens) }
}
