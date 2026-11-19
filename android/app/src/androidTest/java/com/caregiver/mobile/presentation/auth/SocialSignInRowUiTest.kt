package com.caregiver.mobile.presentation.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #873: the standalone coming-soon social row. The row is purely
 * presentational, so these tests pin the visible copy and that every brand
 * button is disabled (not a focusable action) with a localized content
 * description. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class SocialSignInRowUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginShowsComingSoonDividerAndThreeDisabledProviders() {
        compose.setContent {
            CaregiverTheme {
                SocialSignInRow(isRegister = false)
            }
        }

        compose.onNodeWithText("or continue with · coming soon").assertExists()
        listOf("Google, coming soon", "GitHub, coming soon", "Facebook, coming soon").forEach {
            compose.onNodeWithContentDescription(it).assertIsNotEnabled()
        }
    }

    @Test
    fun registerUsesSignUpDivider() {
        compose.setContent {
            CaregiverTheme {
                SocialSignInRow(isRegister = true)
            }
        }

        compose.onNodeWithText("or sign up with · coming soon").assertExists()
    }
}
