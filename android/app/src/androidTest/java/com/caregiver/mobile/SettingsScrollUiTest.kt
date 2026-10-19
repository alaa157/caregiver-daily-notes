package com.caregiver.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.settings.AiSettingsScreen
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.presentation.settings.ThemeColorsScreen
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 11: the settings surfaces must stay scrollable and operable on
 * short viewports, at double font scales, on narrow screens, and in Arabic
 * RTL — bottom actions reachable by scrolling, chips wrapped instead of
 * clipped, labels localised. Instrumentation tests: they need a device or
 * emulator and never run under `testDebugUnitTest`.
 */
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
                    SettingsScreen(graph, rememberNavController())
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.settings_title)).assertIsDisplayed()
    }

    @Test
    fun settingsScrollsToTheVersionRowOnAShortViewport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val version = context.getString(R.string.settings_app_version, BuildConfig.VERSION_NAME)

        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    // Short window: the later groups start below the fold and
                    // must arrive by scrolling rather than be clipped away.
                    Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                        SettingsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        compose.onNodeWithText(version).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScrollsAtDoubledFontScale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val version = context.getString(R.string.settings_app_version, BuildConfig.VERSION_NAME)

        compose.setContent {
            // Doubled text on a short viewport: the worst-case wrap; the
            // version row still has to be reachable.
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                WithTestOwner {
                    CaregiverTheme {
                        Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                            SettingsScreen(graph, rememberNavController())
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(version).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScrollsInArabicRtl() {
        val localized = ApplicationProvider.getApplicationContext<Context>().withLocale("ar")
        val graph = AppGraph(localized)
        val version = localized.getString(R.string.settings_app_version, BuildConfig.VERSION_NAME)

        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                WithTestOwner {
                    CaregiverTheme {
                        Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                            SettingsScreen(graph, rememberNavController())
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(version).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun themeModeAndSectionLabelsFollowTheActiveLocale() {
        // The mode/accent controls moved from the Settings list to the
        // dedicated Theme and Colors screen; under an Arabic context every
        // label there must read Arabic — the mode circles used to be
        // hardcoded English and the section headers shipped untranslated.
        val localized = ApplicationProvider.getApplicationContext<Context>().withLocale("ar")
        val graph = AppGraph(localized)

        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                WithTestOwner {
                    CaregiverTheme {
                        ThemeColorsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        listOf(
            R.string.settings_mode_light,
            R.string.settings_mode_dark,
            R.string.settings_mode_system,
        ).forEach { id ->
            compose.onNodeWithText(localized.getString(id)).assertIsDisplayed()
        }
        compose.onNodeWithText(localized.getString(R.string.theme_mode)).assertExists()
        compose.onNodeWithText(localized.getString(R.string.color_palette)).assertExists()
        compose.onNodeWithText(localized.getString(R.string.pure_black)).assertExists()
    }

    @Test
    fun paletteSwatchesStayReachableOnANarrowScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val rose = context.getString(R.string.palette_rose)

        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    // Narrower than the swatch strip needs on one line: the
                    // strip scrolls, so every accent stays reachable instead
                    // of being clipped off the edge.
                    Box(Modifier.size(width = 260.dp, height = 480.dp)) {
                        ThemeColorsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(rose).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aiSettingsScrollsToTheSaveActionOnAShortViewport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val save = context.getString(R.string.settings_ai_save)

        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                        AiSettingsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        compose.onNodeWithText(save).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aiSettingsDialogsBringTheirActionsAtDoubledFontScale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                WithTestOwner {
                    CaregiverTheme {
                        Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                            AiSettingsScreen(graph, rememberNavController())
                        }
                    }
                }
            }
        }
        // The key editor is the dialog with a focus field, i.e. the one an
        // IME can push around: its confirm action must still be on screen.
        compose.onNodeWithText(context.getString(R.string.settings_ai_set_api_key))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.common_ok)).assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
