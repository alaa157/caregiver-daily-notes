package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Claim-existing-data screen coverage for issue #624. */
@RunWith(AndroidJUnit4::class)
class ClaimExistingDataScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val owner = Account("owner", "owner@example.com", "Owner", 1L, 1L)
    private lateinit var repository: AuthRepository

    private fun setUpRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-claim-data")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    private fun render(
        language: String = "en",
        registerAccount: RegisterAccount,
        onClaimed: (Account) -> Unit,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalContext provides localized) {
                        ClaimExistingDataScreen(
                            repository = repository,
                            registerAccount = registerAccount,
                            onClaimed = onClaimed,
                        )
                    }
                }
            }
        }
    }

    private fun fillValidForm() {
        compose.onNodeWithTag("auth_field_name").performTextInput("Owner")
        compose.onNodeWithTag("auth_field_email").performTextInput("owner@example.com")
        compose.onNodeWithTag("auth_field_password").performTextInput("password1")
    }

    private fun submitButton(label: String) =
        compose.onAllNodesWithText(label).filterToOne(hasClickAction())

    @Test
    fun showsBlockingClaimCopyWithoutBackEnglish() {
        setUpRepository()
        render(registerAccount = { _, _, _ -> Result.success(owner) }, onClaimed = {})

        compose.onNodeWithText("Claim your data").assertIsDisplayed()
        compose.onNodeWithText("only this account", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Create account & claim data").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_email").assertIsDisplayed()
        compose.onNodeWithTag("auth_field_password").assertIsDisplayed()
        compose.onNodeWithTag("auth_primary_button").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun failedCreationStaysOnScreenWithErrorEnglish() {
        setUpRepository()
        var calls = 0
        var claimed: Account? = null
        render(
            registerAccount = {
                calls++
                Result.failure(AccountException(AccountError.EmailTaken))
            },
            onClaimed = { claimed = it },
        )

        fillValidForm()
        submitButton("Create account & claim data").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Claim your data").assertIsDisplayed()
        compose.onNodeWithText("This email is already registered").assertIsDisplayed()
        assertEquals(1, calls)
        assertNull(claimed)
    }

    @Test
    fun successHandsOffCreatedOwnerAccountEnglish() {
        setUpRepository()
        var calls = 0
        var claimed: Account? = null
        render(
            registerAccount = { name, email, _ ->
                calls++
                assertEquals("Owner", name)
                assertEquals("owner@example.com", email)
                Result.success(owner)
            },
            onClaimed = { claimed = it },
        )

        fillValidForm()
        submitButton("Create account & claim data").performClick()
        compose.waitForIdle()

        assertEquals(1, calls)
        assertEquals(owner, claimed)
    }

    @Test
    fun arabicClaimCopyLocalized() {
        setUpRepository()
        render(
            language = "ar",
            registerAccount = { _, _, _ -> Result.success(owner) },
            onClaimed = {},
        )

        compose.onNodeWithText("استرجاع بياناتك").assertIsDisplayed()
        compose.onNodeWithText("هذا الحساب", substring = true).assertIsDisplayed()
        compose.onNodeWithText("إنشاء حساب واسترجاع البيانات").assertIsDisplayed()
        compose.onNodeWithContentDescription("رجوع").assertDoesNotExist()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
