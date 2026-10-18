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
    fun settingsModeAndAccentLabelsFollowTheActiveLocale() {
        // Cover chips used to leak English ("Light", "Teal") into the
        // Arabic screen; the same rows must read Arabic under an Arabic
        // context. The language names stay literal by design.
        val localized = ApplicationProvider.getApplicationContext<Context>().withLocale("ar")
        val graph = AppGraph(localized)

        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                WithTestOwner {
                    CaregiverTheme {
                        SettingsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        listOf(
            R.string.settings_mode_light,
            R.string.settings_mode_dark,
            R.string.settings_mode_system,
            R.string.settings_palette_rose,
        ).forEach { id ->
            compose.onNodeWithText(localized.getString(id)).assertIsDisplayed()
        }
    }

    @Test
    fun accentChipsWrapInsteadOfClippingOnANarrowScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val rose = context.getString(R.string.settings_palette_rose)

        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    // Narrower than the four chips need on one line: a chip
                    // laid out past the edge is clipped, i.e. the accent
                    // becomes unreachable.
                    Box(Modifier.size(width = 260.dp, height = 480.dp)) {
                        SettingsScreen(graph, rememberNavController())
                    }
                }
            }
        }
        compose.onNodeWithText(rose).performScrollTo().assertIsDisplayed()
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
