package com.caregiver.mobile

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Task 7 avatar drawer flows on the real renderer: open from the avatar,
 * navigate to Settings (which closes the drawer), and back closing an open
 * drawer before any other back action.
 *
 * The rule hosts a real ComponentActivity, so the drawer BackHandler and the
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
    fun settingsItemNavigatesAndClosesDrawer() {
        setScaffold()

        compose.onNodeWithTag("drawer_avatar").performClick()
        compose.onNodeWithTag("drawer_item_settings").assertIsDisplayed()
        compose.onNodeWithTag("drawer_item_settings").performClick()
        compose.waitForIdle()

        // The tab shell's shared top bar (avatar included) is replaced by the
        // Settings screen, whose content is now on screen.
        compose.onNodeWithTag("drawer_avatar").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_server))
            .assertIsDisplayed()
        // Drawer closed: the sheet sits off-screen.
        compose.onNodeWithTag("drawer_item_settings").assertIsNotDisplayed()
    }

    @Test
    fun backPressClosesOpenDrawerAndStaysOnCurrentTab() {
        setScaffold()

        compose.onNodeWithTag("drawer_avatar").performClick()
        compose.onNodeWithTag("drawer_item_people").assertIsDisplayed()

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        // The drawer closed first; the current tab did not change.
        compose.onNodeWithTag("drawer_item_people").assertIsNotDisplayed()
        compose.onNodeWithTag("tab_home").assertIsSelected()
    }
}
