package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #878: the standalone/local-login NavHost does not register the
 * `server-url` destination, so Login must not offer that link when it is
 * rendered in that host. The QA build showed the Arabic link and tapping it
 * threw an uncaught IllegalArgumentException (NavDeepLinkRequest not found),
 * killing the app. These tests pin the wiring that hides it.
 *
 * Instrumentation-only; these never run under `testDebugUnitTest`.
 */
@RunWith(AndroidJUnit4::class)
class LoginServerSettingsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-878-login-server")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    @After
    fun tearDown() = runBlocking { repository.signOut() }

    private fun renderLogin(language: String, showServerSettings: Boolean) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(language))
        val localized = base.createConfigurationContext(config)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                CaregiverTheme {
                    LoginScreen(
                        repository = repository,
                        navController = rememberNavController(),
                        showServerSettings = showServerSettings,
                    )
                }
            }
        }
    }

    @Test
    fun standaloneLoginHidesServerSettingsLinkArabic() {
        // The exact QA reproduction: Arabic default locale, local-login host.
        renderLogin("ar", showServerSettings = false)

        compose.onNodeWithText("إعدادات الخادم").assertDoesNotExist()
    }

    @Test
    fun backendLoginStillShowsServerSettingsLink() {
        // Guard the other branch: when the destination IS registered, the
        // link must remain available.
        renderLogin("ar", showServerSettings = true)

        compose.onNodeWithText("إعدادات الخادم").assertExists()
    }
}
