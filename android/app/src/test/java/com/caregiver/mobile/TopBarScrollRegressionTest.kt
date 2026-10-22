package com.caregiver.mobile

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression lock for the Home scroll crash.
 *
 * Material3's stock `enterAlwaysScrollBehavior` collapses the bar by a
 * hardcoded 64dp while the shared bar is 56dp tall. The first full collapse
 * therefore lays the bar out at a negative height and throws
 * `IllegalStateException: Size(.., -16) is out of range` mid-scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class TopBarScrollRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
    }

    @Test
    fun scrollingHomeDoesNotCrash() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    MainScaffold(graph)
                }
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_tabs").fetchSemanticsNodes().isNotEmpty()
        }
        // All tab: enough rows for the feed to overflow the viewport.
        compose.onNodeWithTag("home_tab_all").performClick()
        compose.waitForIdle()

        // This is the gesture that used to kill the process when the stock
        // top-bar behavior fully collapsed the 56dp bar.
        repeat(2) {
            compose.onRoot().performTouchInput {
                swipeUp(startY = height * 0.72f, endY = height * 0.25f)
            }
            compose.waitForIdle()
        }

        assertTrue(compose.onAllNodesWithTag("home_tabs").fetchSemanticsNodes().isNotEmpty())
    }
}
