package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.caregiver.mobile.presentation.auth.RemoveAccountDialog
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Arabic/RTL coverage for the account screens added by #607-#611. */
@RunWith(AndroidJUnit4::class)
class AccountScreensArabicTest {

    @get:Rule
    val compose = createComposeRule()

    private val longName = "عائشة أحمد محمد عبد الرحمن الطويل جداً"
    private val longEmail = "very.long.mixed.email.address.for.rtl.testing@example.com"
    private val account = Account(
        id = "a1",
        email = longEmail,
        displayName = longName,
        createdAt = 1L,
        lastUsedAt = 1L,
    )

    private lateinit var repository: AuthRepository

    private fun setUpRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-account-screens-ar")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    private fun render(language: String, content: @Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalContext provides localized) {
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun arabicAccountPickerLongMixedTextDoesNotClip() {
        render("ar") {
            AccountPicker(
                accounts = listOf(account),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText(longName).assertIsDisplayed()
        compose.onNodeWithText(longEmail, substring = true).assertIsDisplayed()
        compose.onNodeWithText("نشط").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
    }

    @Test
    fun arabicSettingsAccountLongMixedTextDoesNotClip() {
        render("ar") {
            AccountSettingsSection(
                account = account,
                onSwitch = {},
                onAdd = {},
                onRemove = {},
            )
        }

        compose.onNodeWithText("الحساب").assertIsDisplayed()
        compose.onNodeWithText(longName).assertIsDisplayed()
        compose.onNodeWithText(longEmail, substring = true).assertIsDisplayed()
        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
        compose.onNodeWithText("إزالة الحساب").assertIsDisplayed()
    }

    @Test
    fun arabicAddAccountFormLocalized() {
        setUpRepository()
        render("ar") {
            AddAccountFlow(
                repository = repository,
                registerAccount = { _, _, _ -> Result.success(account.copy(id = "new")) },
                onCancel = {},
                onCreated = {},
            )
        }

        compose.onNodeWithContentDescription("رجوع").assertIsDisplayed()
        compose.onNodeWithText("الاسم").assertIsDisplayed()
        compose.onNodeWithText("البريد الإلكتروني").assertIsDisplayed()
        compose.onNodeWithText("كلمة المرور").assertIsDisplayed()
        compose.onNodeWithTag("auth_primary_button").assertIsDisplayed()
        compose.onNodeWithText("إنشاء الحساب").assertIsDisplayed()
    }

    @Test
    fun arabicRemoveDialogLongMixedTextDoesNotClip() {
        render("ar") {
            RemoveAccountDialog(
                account = account,
                isActive = false,
                onDismiss = {},
                onConfirm = { Result.success(Unit) },
            )
        }

        compose.onNodeWithText("إزالة الحساب؟").assertIsDisplayed()
        compose.onNodeWithText(longName, substring = true).assertIsDisplayed()
        compose.onNodeWithText(longEmail, substring = true).assertIsDisplayed()
        compose.onNodeWithText("إلغاء").assertIsDisplayed()
        compose.onNodeWithText("إزالة").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
