package com.caregiver.mobile

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Avatar account pop-up flows on the real renderer: open from the avatar,
 * hide the Google row on standalone local-first builds, navigate to Settings
 * (which closes the pop-up), return with the top bar's back arrow, and back
 * closing an open pop-up before any other back action.
 *
 * The rule hosts a real ComponentActivity, so the BackHandler and the
 * Settings screen's activity cast work exactly like in the app. Assertions
 * use test tags plus the activity's own strings, so they hold on any device
 * locale. Instrumentation-only: needs a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class DrawerUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setScaffold() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    MainScaffold(graph)
                }
            }
        }
    }

    @Test
    fun settingsItemNavigatesAndBackArrowReturns() {
        setScaffold()

        compose.onNodeWithTag("drawer_avatar").performClick()
        // Standalone builds have no accounts: the menu explains itself with
        // the account subtitle instead of an inert Google sign-in row (which
        // could only show an "experimental" toast).
        compose.onNodeWithText(compose.activity.getString(R.string.account_menu_subtitle))
            .assertIsDisplayed()
        val google = compose.onNodeWithText(compose.activity.getString(R.string.sign_in_with_google))
        if (BuildConfig.LOCAL_MODE) google.assertDoesNotExist() else google.assertIsDisplayed()

        compose.onNodeWithTag("drawer_item_settings").performClick()
        compose.waitForIdle()

        // The tab shell's shared top bar (avatar included) is replaced by the
        // Settings screen, the pop-up is gone, and the Data & Backup section
        // (which shipped empty) renders its rows.
        compose.onNodeWithTag("drawer_avatar").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_title))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_data_backup))
            .assertExists()
        compose.onNodeWithTag("drawer_item_settings").assertDoesNotExist()
        // Add-note is tab-shell chrome: it must not follow the user into
        // settings (or any other non-tab destination).
        compose.onNodeWithTag("fab_add_note").assertDoesNotExist()

        // The top bar arrow is the visible way back — the system back button
        // is no longer the only affordance — and it pops to the tab shell.
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drawer_avatar").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_data_backup))
            .assertDoesNotExist()
    }

    @Test
    fun backPressClosesOpenAccountMenuAndStaysOnCurrentTab() {
        setScaffold()

        compose.onNodeWithTag("drawer_avatar").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.account_menu_subtitle))
            .assertIsDisplayed()

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        // The pop-up closed first; the current tab did not change.
        compose.onNodeWithText(compose.activity.getString(R.string.account_menu_subtitle))
            .assertDoesNotExist()
        compose.onNodeWithTag("tab_home").assertIsSelected()
    }
}
