package com.caregiver.mobile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.core.theme.CaregiverTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM regression lock for the scroll crash (demo-v0.8.0): walks the real
 * offline demo flow onto Home and swipes down — the gesture that killed the
 * process — to prove the fix holds. Material3's stock top-bar scroll
 * behavior laid out the 56-dp bar at a negative height when fully hidden
 * ("Size(.., -16) is out of range") and the first swipe on Home crashed the
 * app; the custom top-bar slide (TopBarHide, no negative-size layout) keeps
 * this green. Runs on Robolectric — no device or backend needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class TopBarHideRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.auth.signOut()
            graph.settings.resetBaseUrl()
        }
    }

    @Test
    fun scrollDownOnHomeDoesNotCrash() {
        compose.setContent {
            TestOwner {
                CaregiverTheme {
                    CaregiverRoot(graph)
                }
            }
        }

        // Debug demo entry → tabbed app.
        compose.onNodeWithTag("auth_demo_entry").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        // Dashboard loaded (Home content state).
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_tabs").fetchSemanticsNodes().isNotEmpty()
        }
        // All tab: two recipient rows, so the feed overflows the viewport.
        compose.onNodeWithTag("home_tab_all").performClick()
        compose.waitForIdle()

        // Scroll down twice — this is what hides the top bar and the FAB.
        repeat(2) {
            compose.onRoot().performTouchInput {
                swipeUp(startY = height * 0.72f, endY = height * 0.25f)
            }
            compose.waitForIdle()
        }
        // Hidden: the bar and the note FAB are out of the composition.
        assertTrue(compose.onAllNodesWithTag("fab_add_note").fetchSemanticsNodes().isEmpty())

        // Scroll back up (bar + FAB return).
        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.72f)
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("fab_add_note").fetchSemanticsNodes().isNotEmpty())
    }
}

@Composable
private fun TestOwner(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
