package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.AuthResponse
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 3 composable coverage: the real login/register forms in both locales
 * — labels, inline field errors, localized form errors (never raw server
 * English), submit wiring, and mode switching. Instrumentation-only; these
 * never run under `testDebugUnitTest`.
 */
@RunWith(AndroidJUnit4::class)
class AuthFormUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var settings: SettingsStore
    private lateinit var fakeAuth: FakeAuthApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsStore.create(context, "test-auth-ui")
        fakeAuth = FakeAuthApi()
        repository = AuthRepository(FakeBackendApis(fakeAuth), settings, TokenHolder())
    }

    @After
    fun tearDown() = runBlocking { repository.signOut() }

    private fun setForm(
        mode: AuthMode,
        language: String,
        onSwitch: () -> Unit = {},
    ): AuthViewModel {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        val vm = AuthViewModel(mode, repository)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                CaregiverTheme {
                    val state by vm.state.collectAsState()
                    AuthForm(
                        state = state,
                        mode = mode,
                        onEmail = vm::onEmail,
                        onPassword = vm::onPassword,
                        onConfirm = vm::onConfirm,
                        onSubmit = vm::submit,
                        onSwitchMode = onSwitch,
                    )
                }
            }
        }
        return vm
    }

    private fun submitButton(label: String) =
        compose.onAllNodesWithText(label).filterToOne(hasClickAction())

    @Test
    fun loginLabelsEnglish() {
        setForm(AuthMode.Login, "en")

        listOf("Sign in", "Email", "Password", "No account? Create one").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun registerLabelsArabic() {
        setForm(AuthMode.Register, "ar")

        listOf("إنشاء حساب", "البريد الإلكتروني", "كلمة المرور", "تأكيد كلمة المرور").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun blankEmailErrorInline() {
        setForm(AuthMode.Login, "en")

        submitButton("Sign in").performClick()

        compose.onNodeWithText("Email is required").assertIsDisplayed()
    }

    @Test
    fun malformedEmailErrorInlineArabic() {
        setForm(AuthMode.Login, "ar")

        compose.onNodeWithTag("auth_field_email").performTextInput("x@")
        submitButton("دخول").performClick()

        compose.onNodeWithText("أدخل بريداً إلكترونياً صحيحاً").assertIsDisplayed()
    }

    @Test
    fun blankAndOversizedPasswordErrorsInline() {
        setForm(AuthMode.Login, "en")
        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")

        submitButton("Sign in").performClick()
        compose.onNodeWithText("Password is required").assertIsDisplayed()

        compose.onNodeWithTag("auth_field_password").performTextInput("a".repeat(73))
        submitButton("Sign in").performClick()
        compose.onNodeWithText("Password must be at most 72 bytes").assertIsDisplayed()
    }

    @Test
    fun registerMismatchErrorInline() {
        setForm(AuthMode.Register, "en")

        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("pw")
        compose.onNodeWithTag("auth_field_confirm").performTextInput("other")
        submitButton("Create account").performClick()

        compose.onNodeWithText("Passwords do not match").assertIsDisplayed()
    }

    @Test
    fun invalidCredentialsLocalized() {
        fakeAuth.loginHandler = { throw FakeAuthApi.httpError(401, "INVALID_CREDENTIALS") }
        setForm(AuthMode.Login, "en")

        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("wrong")
        submitButton("Sign in").performClick()

        compose.onNodeWithText("Invalid email or password").assertIsDisplayed()
    }

    @Test
    fun duplicateEmailLocalizedArabic() {
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "DUPLICATE_EMAIL", "Email is already registered.")
        }
        setForm(AuthMode.Register, "ar")

        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("pw")
        compose.onNodeWithTag("auth_field_confirm").performTextInput("pw")
        compose.onAllNodesWithText("إنشاء الحساب").filterToOne(hasClickAction()).performClick()

        compose.onNodeWithText("هذا البريد مسجل بالفعل").assertIsDisplayed()
    }

    @Test
    fun unknownServerMessageNeverLeaksIntoArabicUi() {
        fakeAuth.loginHandler = {
            throw FakeAuthApi.httpError(422, "SOME_FUTURE_CODE", "Some future English message.")
        }
        setForm(AuthMode.Login, "ar")

        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("pw")
        submitButton("دخول").performClick()

        compose.onNodeWithText("حدث خطأ ما. حاول مرة أخرى.").assertIsDisplayed()
        compose.onAllNodesWithText("Some future English message.").assertCountEquals(0)
    }

    @Test
    fun validSubmitReachesAuthenticatedState() {
        fakeAuth.loginHandler = { AuthResponse("tok") }
        setForm(AuthMode.Login, "en")

        compose.onNodeWithTag("auth_field_email").performTextInput("  ALI@Example.COM ")
        compose.onNodeWithTag("auth_field_password").performTextInput("pw")
        submitButton("Sign in").performClick()

        compose.waitForIdle()
        assertEquals("ali@example.com", fakeAuth.lastLogin?.email)
        assertEquals("tok", runBlocking { settings.token.first() })
    }

    @Test
    fun switchModeCallbackFires() {
        var switched = false
        setForm(AuthMode.Login, "en", onSwitch = { switched = true })

        compose.onNodeWithText("No account? Create one").performClick()

        assertTrue(switched)
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
