package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.BuildConfig
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The field report that started this fix: Settings showed a Data & Backup
 * section with no actions — every `CaregiverSettingsItem(...)` call had
 * resolved to a data-class constructor instead of the composable, so the
 * rows were built and thrown away. These tests lock the shipped contract:
 *
 * - every section and all four Data & Backup actions render;
 * - when the build has no authoritative local store, the backup actions
 *   stay visible with the explanation and CSV keeps working.
 *
 * A follow-up device session on the same demo build added: the Settings top
 * bar had no visible back affordance (a dead-end except the system back
 * button), the AI tile repeated its title as its description, the AI screen
 * group headers repeated their first row's title, and the version string
 * appeared twice. Those locks live here too.
 *
 * They run on the JVM under Robolectric so they execute in CI's
 * `testDebugUnitTest` without a device.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun renderScreen(content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    content(rememberNavController())
                }
            }
        }
    }

    private fun renderSettings(graph: AppGraph) = renderScreen { SettingsScreen(graph, it) }

    @Test
    fun everySectionAndAllFourDataBackupActionsRender() {
        renderSettings(AppGraph(context))

        listOf(
            R.string.settings_language,
            R.string.settings_ai,
            R.string.settings_theme_colors,
            R.string.settings_data_backup,
            R.string.settings_about,
        ).forEach { id ->
            compose.onNodeWithText(context.getString(id)).assertExists()
        }
        listOf(
            R.string.csv_import_title,
            R.string.csv_export_title,
            R.string.backup_create_title,
            R.string.backup_restore_title,
        ).forEach { id ->
            compose.onNodeWithText(context.getString(id)).assertExists()
        }
        // Both language choices stay reachable at all times.
        compose.onNodeWithText("\u0627\u0644\u0639\u0631\u0628\u064a\u0629").assertExists()
        compose.onNodeWithText("English").assertExists()
        // The version lives in exactly one place: the About row description.
        // The duplicate footer line that used to repeat it is gone.
        compose.onAllNodesWithText(
            context.getString(R.string.settings_app_version, BuildConfig.VERSION_NAME),
        ).assertCountEquals(1)
    }

    @Test
    fun topBarBackArrowReturnsFromSettings() {
        // The field report's dead-end: Settings had no visible way back and
        // re-tapping the Home tab only emitted ScrollToTop, so the system
        // back button was the only exit. The top bar now carries a back
        // arrow wired to popBackStack; this drives it through a real
        // NavHost and proves the caller is revealed again.
        lateinit var nav: NavHostController
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home-stub") {
                        composable("home-stub") { Text("home-stub") }
                        composable("settings-stub") { SettingsScreen(AppGraph(context), nav) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { nav.navigate("settings-stub") }
        compose.waitForIdle()

        // Settings is up with its rows and the back arrow.
        compose.onNodeWithText(context.getString(R.string.settings_data_backup)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("home-stub").assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_data_backup)).assertDoesNotExist()
    }

    @Test
    fun backupActionsStayVisibleWithAnExplanationAndCsvStaysEnabledWithoutALocalStore() {
        // forceProduction turns LOCAL_MODE off: the Room store is only a
        // server cache there, so full backup/restore is unavailable.
        renderSettings(AppGraph(context, forceProduction = true))

        compose.onNodeWithText(context.getString(R.string.backup_create_title))
            .assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_restore_title))
            .assertIsNotEnabled()
        // Both disabled rows carry the explanation, so the section never
        // reads as empty.
        compose.onAllNodesWithText(context.getString(R.string.backup_unavailable))
            .assertCountEquals(2)
        // CSV does not depend on the backup store and must keep working.
        compose.onNodeWithText(context.getString(R.string.csv_import_title))
            .assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.csv_export_title))
            .assertIsEnabled()
    }

    @Test
    fun aiSettingsScreenRendersItsRows() {
        // The same name-collision bug emptied the AI settings screen: row
        // calls built a data-class instance and rendered nothing. The
        // provider, model and base-URL rows prove it is populated again.
        renderScreen { nav -> AiSettingsScreen(AppGraph(context), nav) }

        listOf(
            R.string.settings_ai_provider,
            R.string.settings_ai_model,
            R.string.settings_ai_base_url,
        ).forEach { id ->
            compose.onNodeWithText(context.getString(id)).assertExists()
        }
        // The group headers used to repeat the first row's title ("API key",
        // "System prompt") above the card; each title now appears once.
        compose.onAllNodesWithText(context.getString(R.string.settings_ai_api_key)).assertCountEquals(1)
        compose.onAllNodesWithText(context.getString(R.string.settings_ai_system_prompt)).assertCountEquals(1)
        // The screen carries the same back arrow as every other detail route.
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).assertExists()
    }
}
