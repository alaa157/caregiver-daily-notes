package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Remove-account confirmation coverage for issue #611. */
@RunWith(AndroidJUnit4::class)
class RemoveAccountDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val account = Account(
        id = "a2",
        email = "omar@example.com",
        displayName = "Omar",
        createdAt = 1L,
        lastUsedAt = 1L,
    )

    private fun render(
        language: String = "en",
        isActive: Boolean = false,
        onDismiss: () -> Unit,
        onConfirm: suspend (Account) -> Result<Unit>,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalContext provides localized) {
                        RemoveAccountDialog(
                            account = account,
                            isActive = isActive,
                            onDismiss = onDismiss,
                            onConfirm = onConfirm,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun cancelDoesNotCallConfirmEnglish() {
        var calls = 0
        var dismissed = false
        render(onDismiss = { dismissed = true }, onConfirm = { calls++; Result.success(Unit) })

        compose.onNodeWithTag("remove_account_cancel").performClick()
        compose.waitForIdle()

        assertEquals(0, calls)
        assertTrue(dismissed)
    }

    @Test
    fun confirmCallsRemoveExactlyOnceEnglish() {
        var calls = 0
        var dismissed = false
        var confirmedAccount: Account? = null
        render(
            onDismiss = { dismissed = true },
            onConfirm = { target ->
                calls++
                confirmedAccount = target
                Result.success(Unit)
            },
        )

        compose.onNodeWithTag("remove_account_confirm").performClick()
        compose.waitForIdle()

        assertEquals(1, calls)
        assertEquals(account, confirmedAccount)
        assertTrue(dismissed)
    }

    @Test
    fun activeAccountCannotBeRemovedEnglish() {
        var calls = 0
        render(isActive = true, onDismiss = {}, onConfirm = { calls++; Result.success(Unit) })

        compose.onNodeWithText(
            "You cannot remove the account you are using. Switch to another account first.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("remove_account_confirm").assertIsNotEnabled()

        assertEquals(0, calls)
    }

    @Test
    fun failureKeepsDialogOpenWithLocalizedErrorEnglish() {
        var calls = 0
        var dismissed = false
        render(
            onDismiss = { dismissed = true },
            onConfirm = {
                calls++
                Result.failure(AccountException(AccountError.AccountNotFound))
            },
        )

        compose.onNodeWithTag("remove_account_confirm").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("remove_account_dialog").assertIsDisplayed()
        compose.onNodeWithText("No account found for this email.").assertIsDisplayed()
        assertEquals(1, calls)
        assertFalse(dismissed)
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicDialogLocalized() {
        render("ar", onDismiss = {}, onConfirm = { Result.success(Unit) })

        compose.onNodeWithText("إزالة الحساب؟").assertIsDisplayed()
        compose.onNodeWithText("Omar", substring = true).assertIsDisplayed()
        compose.onNodeWithText("إلغاء").assertIsDisplayed()
        compose.onNodeWithText("إزالة").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
