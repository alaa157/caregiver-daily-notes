package com.caregiver.mobile.presentation.auth

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.accounts.Account
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountPickerUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun account(id: String, email: String, name: String) =
        Account(id = id, email = email, displayName = name, createdAt = 1L, lastUsedAt = 1L)

    private fun render(language: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                CaregiverTheme(content = content)
            }
        }
    }

    @Test
    fun listsAccountsInOrderWithActiveMarkerEnglish() {
        val active = account("a1", "aisha@example.com", "Aisha")
        val other = account("a2", "omar@example.com", "Omar")
        render("en") {
            AccountPicker(
                accounts = listOf(active, other),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Switch account").assertIsDisplayed()
        compose.onNodeWithText("Aisha").assertIsDisplayed()
        compose.onNodeWithText("aisha@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Omar").assertIsDisplayed()
        compose.onNodeWithText("omar@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("account_active_marker").assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()

        val activeTop = compose.onNodeWithTag("account_picker_row_a1")
            .fetchSemanticsNode().boundsInRoot.top
        val otherTop = compose.onNodeWithTag("account_picker_row_a2")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(activeTop < otherTop)
    }

    @Test
    fun tapNonActiveSelectsAndDismissesEnglish() {
        val active = account("a1", "aisha@example.com", "Aisha")
        val other = account("a2", "omar@example.com", "Omar")
        var selected: Account? = null
        var dismissed = false
        render("en") {
            AccountPicker(
                accounts = listOf(active, other),
                activeAccountId = "a1",
                onSelect = { selected = it },
                onAddAccount = {},
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithTag("account_picker_row_a2").performClick()

        assertEquals(other, selected)
        assertTrue(dismissed)
    }

    @Test
    fun tapActiveOnlyDismissesEnglish() {
        val active = account("a1", "aisha@example.com", "Aisha")
        var selected: Account? = null
        var dismissed = false
        render("en") {
            AccountPicker(
                accounts = listOf(active),
                activeAccountId = "a1",
                onSelect = { selected = it },
                onAddAccount = {},
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithTag("account_picker_row_a1").performClick()

        assertNull(selected)
        assertTrue(dismissed)
    }

    @Test
    fun addAccountRowFiresEnglish() {
        var added = false
        render("en") {
            AccountPicker(
                accounts = emptyList(),
                activeAccountId = null,
                onSelect = {},
                onAddAccount = { added = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("account_add_row").performClick()

        assertTrue(added)
    }

    @Test
    fun arabicPickerLocalized() {
        val active = account("a1", "aisha@example.com", "عائشة")
        render("ar") {
            AccountPicker(
                accounts = listOf(active),
                activeAccountId = "a1",
                onSelect = {},
                onAddAccount = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("تبديل الحساب").assertIsDisplayed()
        compose.onNodeWithText("عائشة").assertIsDisplayed()
        compose.onNodeWithText("aisha@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText("نشط").assertIsDisplayed()
        compose.onNodeWithText("إضافة حساب").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
