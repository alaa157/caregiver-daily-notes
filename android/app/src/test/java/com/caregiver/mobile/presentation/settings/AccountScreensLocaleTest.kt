package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.presentation.auth.AccountPicker
import com.caregiver.mobile.presentation.auth.AuthForm
import com.caregiver.mobile.presentation.auth.AuthMode
import com.caregiver.mobile.presentation.auth.AuthUiState
import com.caregiver.mobile.presentation.auth.RemoveAccountDialog
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** EN/AR locale + RTL coverage for the account screens (#633). */
@RunWith(AndroidJUnit4::class)
class AccountScreensLocaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val active = Account("a1", "aisha@example.com", "Aisha", 1L, 1L)
    private val other = Account("a2", "omar@example.com", "Omar", 1L, 1L)

    private lateinit var repository: AuthRepository

    private fun setUpRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-account-locales")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    private fun render(language: String, content: @Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        // Emulate what the Android view root does in production: derive the
        // composition's layout direction from the localized configuration.
        val direction =
            if (localized.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(
                        LocalContext provides localized,
                        LocalLayoutDirection provides direction,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            Text(
                                text = LocalLayoutDirection.current.name,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .testTag("layout_direction"),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertDirection(expected: String) {
        compose.onNodeWithTag("layout_direction").assertExists()
        compose.onNodeWithTag("layout_direction").assertTextEquals(expected)
    }

    @Composable
    private fun authForm(mode: AuthMode, showName: Boolean = false) {
        AuthForm(
            state = AuthUiState(),
            mode = mode,
            onEmail = {},
            onPassword = {},
            onSubmit = {},
            onSwitchMode = {},
            showDisplayName = showName,
            onDisplayName = {},
            showSwitchAccount = mode == AuthMode.Login,
            onSwitchAccount = {},
        )
    }

    @Test
    fun englishSignInRegisterAndSwitchAreLtr() {
        render("en") { authForm(AuthMode.Login) }
        assertDirection("Ltr")
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Switch account").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun englishRegisterWithNameIsLtr() {
        render("en") { authForm(AuthMode.Register, showName = true) }
        assertDirection("Ltr")
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Already have an account? Sign in")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun arabicSignInRegisterAndSwitchAreRtl() {
        render("ar") { authForm(AuthMode.Login) }
        assertDirection("Rtl")
        compose.onNodeWithText("دخول").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("البريد الإلكتروني").assertIsDisplayed()
        compose.onNodeWithText("كلمة المرور").assertIsDisplayed()
        compose.onNodeWithText("تبديل الحساب").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun arabicRegisterWithNameIsRtl() {
        render("ar") { authForm(AuthMode.Register, showName = true) }
        assertDirection("Rtl")
        compose.onNodeWithText("إنشاء الحساب").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("الاسم").assertIsDisplayed()
        compose.onNodeWithText("لديك حساب بالفعل؟ سجّل الدخول")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun englishPickerShowsActiveMarkerAndRemoveEnglish() {
        render("en") {
            AccountPicker(
                accounts = listOf(active, other),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
                onRemoveAccount = { Result.success(Unit) },
            )
        }
        assertDirection("Ltr")
        compose.onNodeWithText("Switch account").assertIsDisplayed()
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test
    fun arabicPickerIsRtlWithActiveMarker() {
        render("ar") {
            AccountPicker(
                accounts = listOf(active),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
            )
        }
        assertDirection("Rtl")
        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText("نشط").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
    }

    @Test
    fun englishSettingsShowsDisabledRemoveEnglish() {
        render("en") {
            AccountSettingsSection(
                account = active,
                onSwitch = {},
                onAdd = {},
                onRemove = {},
            )
        }
        assertDirection("Ltr")
        compose.onNodeWithText("Account").assertIsDisplayed()
        compose.onNodeWithText("Switch account").assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()
        compose.onNodeWithTag("Remove account").assertIsNotEnabled()
        compose.onNodeWithText(
            "You cannot remove the account you are using. Switch to another account first.",
        ).assertIsDisplayed()
    }

    @Test
    fun arabicSettingsIsRtlWithDisabledRemove() {
        render("ar") {
            AccountSettingsSection(
                account = active,
                onSwitch = {},
                onAdd = {},
                onRemove = {},
            )
        }
        assertDirection("Rtl")
        compose.onNodeWithText("الحساب").assertIsDisplayed()
        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
        compose.onNodeWithText("إزالة الحساب").assertIsDisplayed()
        compose.onNodeWithText(
            "لا يمكنك إزالة الحساب الذي تستخدمه. بدّل إلى حساب آخر أولاً.",
        ).assertIsDisplayed()
    }

    @Test
    fun englishRemoveDialogIsLtr() {
        render("en") {
            RemoveAccountDialog(
                account = other,
                isActive = false,
                onDismiss = {},
                onConfirm = { Result.success(Unit) },
            )
        }
        assertDirection("Ltr")
        compose.onNodeWithText("Remove account?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicRemoveDialogIsRtl() {
        render("ar") {
            RemoveAccountDialog(
                account = other,
                isActive = false,
                onDismiss = {},
                onConfirm = { Result.success(Unit) },
            )
        }
        assertDirection("Rtl")
        compose.onNodeWithText("إزالة الحساب؟").assertIsDisplayed()
        compose.onNodeWithText("إلغاء").assertIsDisplayed()
        compose.onNodeWithText("إزالة").assertIsDisplayed()
    }

    @Test
    fun englishAddAccountFlowIsLtr() {
        setUpRepository()
        render("en") {
            AddAccountFlow(
                repository = repository,
                registerAccount = { _, _, _ -> Result.success(other) },
                onCancel = {},
                onCreated = {},
            )
        }
        assertDirection("Ltr")
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun arabicAddAccountFlowIsRtl() {
        setUpRepository()
        render("ar") {
            AddAccountFlow(
                repository = repository,
                registerAccount = { _, _, _ -> Result.success(other) },
                onCancel = {},
                onCreated = {},
            )
        }
        assertDirection("Rtl")
        compose.onNodeWithText("إنشاء الحساب").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("الاسم").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
