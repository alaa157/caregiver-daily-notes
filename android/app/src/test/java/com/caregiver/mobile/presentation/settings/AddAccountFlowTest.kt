package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Add-account flow coverage for issue #610: cancel/failure keep the original active. */
@RunWith(AndroidJUnit4::class)
class AddAccountFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val created = Account(
        id = "new-account",
        email = "new@example.com",
        displayName = "New",
        createdAt = 2L,
        lastUsedAt = 2L,
    )

    private lateinit var repository: AuthRepository

    private fun setUpRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore.create(context, "test-add-account")
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    private fun render(
        language: String = "en",
        registerAccount: com.caregiver.mobile.presentation.auth.RegisterAccount,
        onCancel: () -> Unit,
        onCreated: (Account) -> Unit,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalContext provides localized) {
                        AddAccountFlow(
                            repository = repository,
                            registerAccount = registerAccount,
                            onCancel = onCancel,
                            onCreated = onCreated,
                        )
                    }
                }
            }
        }
    }

    private fun fillValidForm() {
        compose.onNodeWithTag("auth_field_name").performTextInput("New")
        compose.onNodeWithTag("auth_field_email").performTextInput("new@example.com")
        compose.onNodeWithTag("auth_field_password").performTextInput("password1")
        compose.onNodeWithTag("auth_field_confirm").performTextInput("password1")
    }

    private fun submitButton(label: String) =
        compose.onAllNodesWithText(label).filterToOne(hasClickAction())

    @Test
    fun cancelLeavesOriginalAccountActive() {
        setUpRepository()
        var registerCalls = 0
        var cancelled = false
        var createdAccount: Account? = null
        render(
            registerAccount = { _, _, _ ->
                registerCalls++
                Result.success(created)
            },
            onCancel = { cancelled = true },
            onCreated = { createdAccount = it },
        )

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        assertTrue(cancelled)
        assertEquals(0, registerCalls)
        assertNull(createdAccount)
    }

    @Test
    fun successfulCreateReportsNewAccount() {
        setUpRepository()
        var registerCalls = 0
        var createdAccount: Account? = null
        var cancelled = false
        render(
            registerAccount = { name, email, password ->
                registerCalls++
                assertEquals("New", name)
                assertEquals("new@example.com", email)
                assertEquals("password1", password)
                Result.success(created)
            },
            onCancel = { cancelled = true },
            onCreated = { createdAccount = it },
        )

        fillValidForm()
        submitButton("Create account").performClick()
        compose.waitForIdle()

        assertEquals(1, registerCalls)
        assertEquals(created, createdAccount)
        assertTrue(!cancelled)
    }

    @Test
    fun failedCreateKeepsOriginalAccountActive() {
        setUpRepository()
        var registerCalls = 0
        var createdAccount: Account? = null
        render(
            registerAccount = { _, _, _ ->
                registerCalls++
                Result.failure(AccountException(AccountError.EmailTaken))
            },
            onCancel = {},
            onCreated = { createdAccount = it },
        )

        fillValidForm()
        submitButton("Create account").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("This email is already registered").assertIsDisplayed()
        assertEquals(1, registerCalls)
        assertNull(createdAccount)
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
