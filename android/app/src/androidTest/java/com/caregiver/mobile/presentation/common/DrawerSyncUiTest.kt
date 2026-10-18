package com.caregiver.mobile.presentation.common

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 11: the drawer's sync footer must mirror the real state
 * ([SyncStatus], derived by SyncStatusViewModelTest) and retry through the
 * same re-queue action the top bar uses — for every state, in both locales.
 * Instrumentation tests: they need a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class DrawerSyncUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun syncedReadsOutAndOffersNoRetry() {
        render(SyncStatus.SYNCED)

        compose.onNodeWithText(text(R.string.sync_status_synced)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).assertDoesNotExist()
    }

    @Test
    fun syncingReadsOut() {
        render(SyncStatus.SYNCING)

        compose.onNodeWithText(text(R.string.sync_status_syncing)).assertIsDisplayed()
    }

    @Test
    fun offlineReadsOut() {
        render(SyncStatus.OFFLINE)

        compose.onNodeWithText(text(R.string.sync_status_offline)).assertIsDisplayed()
    }

    @Test
    fun failedOffersARetryThatInvokesTheCallback() {
        var retries = 0
        render(SyncStatus.FAILED, onRetry = { retries++ })

        compose.onNodeWithText(text(R.string.sync_status_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun failedWithoutTheCallbackShowsNoRetryAction() {
        render(SyncStatus.FAILED)

        compose.onNodeWithText(text(R.string.sync_status_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry)).assertDoesNotExist()
    }

    @Test
    fun failedReadsOutWithLocalizedRetryInArabic() {
        var retries = 0
        render(SyncStatus.FAILED, language = "ar", onRetry = { retries++ })

        compose.onNodeWithText(text(R.string.sync_status_failed, "ar")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_retry, "ar")).performClick()
        assertEquals(1, retries)
    }

    private fun render(status: SyncStatus, language: String = "en", onRetry: (() -> Unit)? = null) {
        val localized = ApplicationProvider.getApplicationContext<Context>().withLocale(language)
        val graph = AppGraph(localized)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides
                    if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                WithTestOwner {
                    CaregiverTheme {
                        // Hosted exactly like MainScaffold hosts it, opened,
                        // so the footer is on screen and settled.
                        ModalNavigationDrawer(
                            drawerState = rememberDrawerState(DrawerValue.Open),
                            drawerContent = {
                                AppDrawer(
                                    graph = graph,
                                    profileName = "Sara",
                                    avatarLetter = "S",
                                    syncStatus = status,
                                    onRetry = onRetry,
                                    onNavigate = {},
                                )
                            },
                        ) {}
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int, language: String = "en"): String =
        ApplicationProvider.getApplicationContext<Context>().withLocale(language).getString(id)

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
