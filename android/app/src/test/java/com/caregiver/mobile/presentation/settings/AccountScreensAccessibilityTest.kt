package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Accessibility assertions for the account screens added by #607-#611. */
@RunWith(AndroidJUnit4::class)
class AccountScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val active = Account("a1", "aisha@example.com", "Aisha", 1L, 1L)
    private val other = Account("a2", "omar@example.com", "Omar", 1L, 1L)

    private lateinit var repository: AuthRepository

    private fun setUpRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-account-a11y")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    private fun render(language: String = "en", content: @Composable () -> Unit) {
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

    private fun assertMinTouchTarget(tag: String, minDp: Float = 48f) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val density = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density
        val minPx = minDp * density
        assertTrue(
            "$tag height ${node.boundsInRoot.height} is below ${minDp}dp",
            node.boundsInRoot.height >= minPx - 0.5f,
        )
    }

    @Test
    fun pickerAnnouncesCurrentAccountAndRemoveTargetEnglish() {
        render {
            AccountPicker(
                accounts = listOf(active, other),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
                onRemoveAccount = { Result.success(Unit) },
            )
        }

        compose.onNode(hasContentDescription("Current account", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove account").assertIsDisplayed()
        listOf("account_picker_row_a1", "account_picker_row_a2", "account_remove_a2", "account_add_row")
            .forEach { assertMinTouchTarget(it) }
    }

    @Test
    fun arabicPickerAnnouncesCurrentAccount() {
        render("ar") {
            AccountPicker(
                accounts = listOf(active),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
            )
        }

        compose.onNode(hasContentDescription("الحساب الحالي", substring = true)).assertIsDisplayed()
    }

    @Test
    fun settingsAccountRowsMeetTouchTargetMinimum() {
        render {
            AccountSettingsSection(
                account = active,
                onSwitch = {},
                onAdd = {},
                onRemove = {},
            )
        }

        compose.onNodeWithText("Switch account").assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()
        compose.onNodeWithText("Remove account").assertIsDisplayed()
        listOf("Switch account", "Add account", "Remove account").forEach { assertMinTouchTarget(it) }
    }

    @Test
    fun removeDialogButtonsMeetTouchTargetMinimum() {
        render {
            RemoveAccountDialog(
                account = other,
                isActive = false,
                onDismiss = {},
                onConfirm = { Result.success(Unit) },
            )
        }

        compose.onNodeWithText("Remove account?").assertIsDisplayed()
        assertMinTouchTarget("remove_account_confirm")
        assertMinTouchTarget("remove_account_cancel")
    }

    @Test
    fun addAccountFormHasAccessibleBackAndSubmit() {
        setUpRepository()
        render {
            AddAccountFlow(
                repository = repository,
                registerAccount = { _, _, _ -> Result.success(other) },
                onCancel = {},
                onCreated = {},
            )
        }

        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_email").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_primary_button").performScrollTo().assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
