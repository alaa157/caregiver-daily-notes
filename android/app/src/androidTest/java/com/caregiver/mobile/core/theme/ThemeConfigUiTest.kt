package com.caregiver.mobile.core.theme

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 11: every supported appearance configuration — the four accent
 * palettes in light and dark — renders the settings surface with its
 * controls present and untangled. The stored-mode mapping that picks the
 * dark flag is covered by ThemeModeTest.
 */
@RunWith(AndroidJUnit4::class)
class ThemeConfigUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsRendersAcrossEveryPaletteInLightAndDark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        var dark by mutableStateOf(false)
        var palette by mutableStateOf(ColorPalettes.TEAL)

        compose.setContent {
            CaregiverTheme(darkTheme = dark, palette = palette) {
                WithTestOwner { SettingsScreen(graph, rememberNavController()) }
            }
        }

        listOf(
            ColorPalettes.METROLIST,
            ColorPalettes.TEAL,
            ColorPalettes.INDIGO,
            ColorPalettes.ROSE,
        ).forEach { paletteName ->
            listOf(false, true).forEach { isDark ->
                compose.runOnIdle {
                    palette = paletteName
                    dark = isDark
                }
                compose.onNodeWithText(context.getString(R.string.settings_title))
                    .assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.settings_accent))
                    .assertIsDisplayed()
                // The last accent chip proves the whole appearance row laid
                // out under this palette instead of clipping.
                compose.onNodeWithText(context.getString(R.string.settings_palette_rose))
                    .assertIsDisplayed()
            }
        }
    }
}
