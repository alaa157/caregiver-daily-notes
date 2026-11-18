package com.caregiver.mobile.presentation.common

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Account menu identity (#706): stored name when present, app name otherwise. */
@RunWith(AndroidJUnit4::class)
class AccountPopupMenuUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(accountName: String?, avatarLetter: String = "أ") {
        compose.setContent {
            CaregiverTheme {
                AccountPopupMenu(
                    onDismiss = {},
                    onSignInWithGoogle = {},
                    onSettings = {},
                    accountName = accountName,
                    avatarLetter = avatarLetter,
                    showSignInWithGoogle = false,
                )
            }
        }
    }

    @Test
    fun rendersStoredDisplayName() {
        render("أم أحمد")

        compose.onNodeWithTag("account_menu_name")
            .assertIsDisplayed()
            .assertTextEquals("أم أحمد")
    }

    @Test
    fun fallsBackToAppNameWhenNoAccount() {
        render(null)
        val appName = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.app_name)

        compose.onNodeWithTag("account_menu_name")
            .assertIsDisplayed()
            .assertTextEquals(appName)
    }
}
