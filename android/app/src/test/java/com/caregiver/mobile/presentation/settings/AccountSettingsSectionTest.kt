package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSettingsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val account = Account(
        id = "a1",
        email = "aisha@example.com",
        displayName = "Aisha",
        createdAt = 1L,
        lastUsedAt = 1L,
    )

    private fun render(language: String, content: @androidx.compose.runtime.Composable () -> Unit) {
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
    fun showsActiveAccountActionsAndDisabledRemoveEnglish() {
        var switched = false
        var added = false
        var removed = false
        render("en") {
            AccountSettingsSection(
                account = account,
                onSwitch = { switched = true },
                onAdd = { added = true },
                onRemove = { removed = true },
            )
        }

        compose.onNodeWithTag("settings_account_section").assertIsDisplayed()
        compose.onNodeWithText("Account").assertIsDisplayed()
        compose.onNodeWithText("Aisha").assertIsDisplayed()
        compose.onNodeWithText("aisha@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText(
            "You cannot remove the account you are using. Switch to another account first.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("Remove account").assertIsNotEnabled()

        compose.onNodeWithTag("Switch account").performClick()
        compose.onNodeWithTag("Add account").performClick()

        assertTrue(switched)
        assertTrue(added)
        assertTrue(!removed)
    }

    @Test
    fun removeRowEnabledForNonActiveAccount() {
        var removed = false
        render("en") {
            AccountSettingsSection(
                account = account,
                onSwitch = {},
                onAdd = {},
                onRemove = { removed = true },
                canRemove = true,
            )
        }

        // Invoke the row's click action directly: the last row can sit below
        // the headless viewport's fold, where an injected touch would miss.
        compose.onNodeWithTag("Remove account").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(removed)
    }

    @Test
    fun arabicAccountSectionLocalized() {
        render("ar") {
            AccountSettingsSection(
                account = account.copy(displayName = "عائشة"),
                onSwitch = {},
                onAdd = {},
                onRemove = {},
            )
        }

        compose.onNodeWithText("الحساب").assertIsDisplayed()
        compose.onNodeWithText("عائشة").assertIsDisplayed()
        compose.onNodeWithText("aisha@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
        compose.onNodeWithText("إزالة الحساب").assertIsDisplayed()
        compose.onNodeWithText(
            "لا يمكنك إزالة الحساب الذي تستخدمه. بدّل إلى حساب آخر أولاً.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("إزالة الحساب").assertIsNotEnabled()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
