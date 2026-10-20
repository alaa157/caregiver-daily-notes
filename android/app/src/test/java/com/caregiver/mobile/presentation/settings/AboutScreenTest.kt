package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.BuildConfig
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.update.AvailableUpdate
import com.caregiver.mobile.data.update.UpdateChecker
import com.caregiver.mobile.data.update.UpdateResult
import com.caregiver.mobile.test.WithTestOwner
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks the Metrolist-style About page contract: app identity, the update
 * check, the lead developer card, the real GitHub link, and the disabled
 * placeholder slots. Runs on the JVM under Robolectric so it is part of
 * `testDebugUnitTest`.
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun renderAbout(
        onBack: () -> Unit = {},
        checker: UpdateChecker = UpdateChecker { UpdateResult.UpToDate },
    ): RecordingUriHandler {
        val uriHandler = RecordingUriHandler()
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        AboutScreen(updateChecker = checker, onBack = onBack)
                    }
                }
            }
        }
        return uriHandler
    }

    @Test
    fun aboutShowsAppIdentityLeadDeveloperAndVersion() {
        renderAbout()

        compose.onNodeWithText(context.getString(R.string.app_name)).assertExists()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        compose.onNodeWithText(context.getString(R.string.about_lead_developer_name))
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.about_lead_developer_role))
            .assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.about_github))
            .assertExists()
    }

    @Test
    fun githubSocialButtonOpensTheLeadDevelopersProfile() {
        val uriHandler = renderAbout()

        compose.onNodeWithContentDescription(context.getString(R.string.about_github))
            .performScrollTo()
            .performClick()

        assertEquals("https://github.com/alaa157", uriHandler.opened.last())
    }

    @Test
    fun placeholderSocialsExistButAreDisabled() {
        renderAbout()

        compose.onNodeWithContentDescription(context.getString(R.string.about_website))
            .performScrollTo()
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.about_instagram))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun communityRowsOpenTheGitHubRepositoryLinks() {
        val uriHandler = renderAbout()

        compose.onNodeWithText(context.getString(R.string.about_repository))
            .performScrollTo()
            .performClick()
        assertEquals("https://github.com/alaa157/caregiver-daily-notes", uriHandler.opened.last())

        compose.onNodeWithText(context.getString(R.string.about_github))
            .performScrollTo()
            .performClick()
        assertEquals("https://github.com/alaa157", uriHandler.opened.last())

        compose.onNodeWithText(context.getString(R.string.about_report_issue))
            .performScrollTo()
            .performClick()
        assertEquals("https://github.com/alaa157/caregiver-daily-notes/issues", uriHandler.opened.last())
    }

    @Test
    fun updateCheckReportsWhenTheInstalledVersionIsLatest() {
        renderAbout()
        compose.waitForIdle()

        compose.onNodeWithText(
            context.getString(R.string.about_update_up_to_date, BuildConfig.VERSION_NAME),
        )
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun availableUpdateOpensTheApkDownloadAndReleasePage() {
        val update = AvailableUpdate(
            version = "0.3.5",
            releaseUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.5-demo",
            downloadUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.5-demo/app-minified.apk",
        )
        val uriHandler = renderAbout(
            checker = UpdateChecker { UpdateResult.Available(update) },
        )
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.about_update_available_title))
            .performScrollTo()
            .assertExists()

        compose.onNodeWithText(context.getString(R.string.about_update_download))
            .performScrollTo()
            .performClick()
        assertEquals(update.downloadUrl, uriHandler.opened.last())

        compose.onNodeWithText(context.getString(R.string.about_update_view_release))
            .performScrollTo()
            .performClick()
        assertEquals(update.releaseUrl, uriHandler.opened.last())
    }

    @Test
    fun failedUpdateUsesPlainLanguageAndTheRowRetries() {
        var calls = 0
        val checker = UpdateChecker {
            calls += 1
            if (calls == 1) throw IOException("offline") else UpdateResult.UpToDate
        }
        renderAbout(checker = checker)
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.about_update_failed))
            .performScrollTo()
            .assertExists()

        compose.onNodeWithText(context.getString(R.string.about_check_updates))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText(
            context.getString(R.string.about_update_up_to_date, BuildConfig.VERSION_NAME),
        )
            .performScrollTo()
            .assertExists()
        assertEquals(2, calls)
    }

    @Test
    fun backArrowInvokesTheCaller() {
        var backed = false
        renderAbout(onBack = { backed = true })

        compose.onNodeWithContentDescription(context.getString(R.string.action_back))
            .performClick()

        assertTrue(backed)
    }
}
