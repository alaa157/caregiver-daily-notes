package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.SignInResult
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.PasswordPolicy
import com.caregiver.mobile.data.accounts.PasswordProblem
import com.caregiver.mobile.data.api.AuthResponse
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        onBack: (() -> Unit)? = null,
        onDemo: (() -> Unit)? = null,
        registerAccount: RegisterAccount? = null,
        showSwitchAccount: Boolean = false,
        onSwitchAccount: (() -> Unit)? = null,
        enterDemoSession: (suspend () -> SignInResult)? = null,
    ): AuthViewModel {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        val vm = AuthViewModel(
            mode,
            repository,
            registerAccount = registerAccount,
            enterDemoSession = enterDemoSession,
        )
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                CaregiverTheme {
                    val state by vm.state.collectAsState()
                    AuthForm(
                        state = state,
                        mode = mode,
                        onEmail = vm::onEmail,
                        onPassword = vm::onPassword,
                        onSubmit = vm::submit,
                        onSwitchMode = onSwitch,
                        onBack = onBack,
                        onDisplayName = vm::onDisplayName,
                        showDisplayName = vm.requiresDisplayName,
                        onDemo = onDemo ?: if (enterDemoSession != null) vm::demoSignIn else null,
                        showSwitchAccount = showSwitchAccount,
                        onSwitchAccount = onSwitchAccount,
                    )
                }
            }
        }
        return vm
    }

    private fun submitButton(label: String) =
        compose.onAllNodesWithText(label).filterToOne(hasClickAction())

    /** #872: the demo entry is hidden until the logo is long-pressed. */
    private fun revealDemo() {
        compose.onNodeWithTag("auth_logo").performTouchInput { longClick() }
    }

    @Test
    fun loginLabelsEnglish() {
        setForm(AuthMode.Login, "en")

        listOf("Welcome back", "Email", "Password", "No account? Create one").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun registerLabelsArabic() {
        setForm(AuthMode.Register, "ar")

        listOf("أنشئ حسابك", "البريد الإلكتروني", "كلمة المرور").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        // #874: the hint replaces the removed confirm field.
        compose.onNodeWithText(hintText("ar")).assertIsDisplayed()
    }

    /** The Register hint is derived from the same policy value as the UI. */
    private fun hintText(language: String): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
            .withLocale(language)
        return context.getString(R.string.auth_password_hint, PasswordPolicy.MIN_LENGTH)
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
    fun registerUsesSharedFieldsButtonAndPolicyHint() {
        setForm(AuthMode.Register, "en")

        // #874: every field is the shared AuthTextField and the CTA is the
        // shared AuthPrimaryButton with a stable test tag.
        compose.onNodeWithTag("auth_field_name").assertDoesNotExist()
        compose.onNodeWithTag("auth_field_email").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_password").assertIsDisplayed()
        compose.onNodeWithTag("auth_primary_button").assertIsDisplayed()
        compose.onNodeWithText(hintText("en")).assertIsDisplayed()
        // The confirm field was removed with its state in #869.
        compose.onNodeWithTag("auth_field_confirm").assertDoesNotExist()
    }

    @Test
    fun registerBackButtonKeepsExistingBehavior() {
        var backs = 0
        setForm(AuthMode.Register, "en", onBack = { backs++ })

        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()

        assertEquals(1, backs)
    }

    @Test
    fun loginDoesNotShowRegisterHint() {
        setForm(AuthMode.Login, "en")

        compose.onNodeWithText(hintText("en")).assertDoesNotExist()
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
    fun submitDisablesButtonWhileBusy() {
        val gate = CompletableDeferred<AuthResponse>()
        fakeAuth.loginHandler = { gate.await() }
        setForm(AuthMode.Login, "en")

        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("pw")
        submitButton("Sign in").performClick()

        submitButton("Sign in").assertIsNotEnabled()
        gate.complete(AuthResponse("tok"))
    }

    @Test
    fun switchModeCallbackFires() {
        var switched = false
        setForm(AuthMode.Login, "en", onSwitch = { switched = true })

        compose.onNodeWithText("No account? Create one").performClick()

        assertTrue(switched)
    }

    @Test
    fun switchAccountHiddenWithoutAccounts() {
        setForm(AuthMode.Login, "en", showSwitchAccount = false)

        compose.onNodeWithTag("auth_switch_account").assertDoesNotExist()
    }

    @Test
    fun switchAccountEntryFiresCallbackEnglish() {
        var opened = false
        setForm(
            AuthMode.Login,
            "en",
            showSwitchAccount = true,
            onSwitchAccount = { opened = true },
        )

        compose.onNodeWithTag("auth_switch_account").assertIsDisplayed().performClick()

        assertTrue(opened)
    }

    @Test
    fun switchAccountEntryVisibleArabic() {
        setForm(AuthMode.Login, "ar", showSwitchAccount = true, onSwitchAccount = {})

        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
    }

    @Test
    fun serverRegisterHidesNameField() {
        setForm(AuthMode.Register, "en")

        compose.onNodeWithTag("auth_field_name").assertDoesNotExist()
    }

    @Test
    fun localRegisterNameRequiredAndTooLongArabic() {
        var calls = 0
        setForm(
            AuthMode.Register,
            "ar",
            registerAccount = { _, _, _ ->
                calls++
                Result.success(Account("a1", "a@b.c", "Aisha", 1L, 1L))
            },
        )
        compose.onNodeWithTag("auth_field_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("Password1")

        submitButton("إنشاء الحساب").performClick()
        compose.onNodeWithText("الاسم مطلوب").assertIsDisplayed()

        compose.onNodeWithTag("auth_field_name").performTextClearance()
        compose.onNodeWithTag("auth_field_name").performTextInput("n".repeat(81))
        submitButton("إنشاء الحساب").performClick()
        compose.onNodeWithText("استخدم اسمًا لا يتجاوز 80 حرفًا.").assertIsDisplayed()
        assertEquals(0, calls)
    }

    @Test
    fun localRegisterValidNameSubmitsTrimmedEnglish() {
        val calls = mutableListOf<Triple<String, String, String>>()
        setForm(
            AuthMode.Register,
            "en",
            registerAccount = { name, email, password ->
                calls += Triple(name, email, password)
                Result.success(Account("a1", email, name, 1L, 1L))
            },
        )
        compose.onNodeWithTag("auth_field_name").performTextInput("  Aisha  ")
        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("Password1")

        submitButton("Create account").performClick()
        compose.waitForIdle()

        assertEquals(listOf(Triple("Aisha", "a@b.c", "Password1")), calls)
    }

    @Test
    fun localRegisterTooShortPasswordShowsPolicyMessageAndCreatesNoAccount() {
        var signUpCalls = 0
        val vm = setForm(
            AuthMode.Register,
            "en",
            registerAccount = { _, _, password ->
                signUpCalls++
                val problem = PasswordPolicy.validate(password)
                if (problem == null) {
                    Result.success(Account("a1", "a@b.c", "Aisha", 1L, 1L))
                } else {
                    Result.failure(AccountException(AccountError.WeakPassword(problem)))
                }
            },
        )
        compose.onNodeWithTag("auth_field_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_name").performTextInput("Aisha")
        compose.onNodeWithTag("auth_field_email").performTextInput("a@b.c")
        compose.onNodeWithTag("auth_field_password").performTextInput("abcd")

        submitButton("Create account").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Use at least 8 characters.").assertIsDisplayed()
        compose.onAllNodesWithText("Something went wrong. Please try again.").assertCountEquals(0)
        assertEquals(1, signUpCalls)
        assertNull(vm.state.value.createdAccount)
        assertFalse(vm.state.value.signedIn)
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }

    @Test
    fun debugDemoEntryVisibleOnLogin() {
        setForm(AuthMode.Login, "en", onDemo = {})

        // #872: no demo link in the plain footer; long-press reveals it.
        compose.onNodeWithTag("auth_demo_entry").assertDoesNotExist()
        revealDemo()
        compose.onNodeWithTag("auth_demo_entry").assertIsDisplayed()
        compose.onNodeWithText("Explore the demo (offline)").assertIsDisplayed()
    }

    @Test
    fun demoEntryAbsentWithoutCallback() {
        setForm(AuthMode.Login, "en")

        compose.onNodeWithTag("auth_demo_entry").assertDoesNotExist()
    }

    @Test
    fun demoEntryEntersDemoInOneTapWithoutCredentialFormOrError() {
        var demoCalls = 0
        val vm = setForm(AuthMode.Login, "en", enterDemoSession = {
            demoCalls++
            SignInResult.SignedIn
        })

        revealDemo()
        compose.onNodeWithTag("auth_demo_entry").performClick()
        compose.waitForIdle()

        assertEquals(1, demoCalls)
        assertTrue(vm.state.value.signedIn)
        compose.onNodeWithTag("auth_field_email").assertTextEquals("")
        compose.onNodeWithTag("auth_field_password").assertTextEquals("")
        compose.onNodeWithText("Invalid email or password").assertDoesNotExist()
        compose.onNodeWithText("Something went wrong. Please try again.").assertDoesNotExist()
    }

    @Test
    fun demoEntryFailureNeverShowsACredentialError() {
        setForm(AuthMode.Login, "en", enterDemoSession = {
            SignInResult.Rejected(null, "Offline demo unavailable.")
        })

        revealDemo()
        compose.onNodeWithTag("auth_demo_entry").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Invalid email or password").assertDoesNotExist()
        compose.onNodeWithText("Something went wrong. Please try again.").assertIsDisplayed()
    }
}
