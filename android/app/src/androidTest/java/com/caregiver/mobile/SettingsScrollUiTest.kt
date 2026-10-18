package com.caregiver.mobile

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScrollUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsScreenRendersTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    SettingsScreen(graph, androidx.navigation.compose.rememberNavController())
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.settings_title)).assertIsDisplayed()
    }
}