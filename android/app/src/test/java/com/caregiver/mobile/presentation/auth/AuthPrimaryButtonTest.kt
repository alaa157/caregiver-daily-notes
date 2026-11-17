package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI coverage for the standalone redesign submit button (#875), run on the JVM
 * under Robolectric as part of `testDebugUnitTest`. Locks the idle/busy
 * contract: the label, the enabled state, the in-button progress indicator and
 * the fact that a busy button cannot be tapped.
 */
@RunWith(AndroidJUnit4::class)
class AuthPrimaryButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(busy: Boolean, onClick: () -> Unit = {}) {
        compose.setContent {
            CaregiverTheme {
                AuthPrimaryButton(text = "Sign in", onClick = onClick, busy = busy)
            }
        }
    }

    @Test
    fun idleButtonShowsLabelAndIsEnabled() {
        var clicks = 0
        render(busy = false, onClick = { clicks++ })

        compose.onNodeWithTag(AuthPrimaryButtonTestTag).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(AuthSubmitProgressTestTag).assertDoesNotExist()
        compose.onNodeWithTag(AuthPrimaryButtonTestTag).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun busyButtonShowsProgressIndicatorAndIsDisabled() {
        render(busy = true)

        // Disabled => a second tap cannot reach onClick while one is in flight.
        compose.onNodeWithTag(AuthPrimaryButtonTestTag).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(AuthSubmitProgressTestTag).assertIsDisplayed()
    }

    @Test
    fun busyButtonKeepsItsLabelForAccessibility() {
        render(busy = true)

        compose.onNodeWithTag(AuthPrimaryButtonTestTag).assertIsDisplayed()
        compose.onNodeWithTag(AuthSubmitProgressTestTag).assertIsDisplayed()
    }

    /**
     * #876: a fixed 56dp height cut the wrapped label off at 200% font scale.
     * Density 1 makes the measured px equal dp, so the growth check reads in dp.
     */
    @Test
    fun buttonGrowsPastItsMinimumAtDoubledFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CaregiverTheme {
                    Box(Modifier.width(320.dp)) {
                        AuthPrimaryButton(
                            text = "Already have an account? Sign in",
                            onClick = {},
                        )
                    }
                }
            }
        }

        val height = compose.onNodeWithTag(AuthPrimaryButtonTestTag)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "auth button must grow past its 56dp minimum instead of clipping at 200% text (height=$height px)",
            height > 56f,
        )
    }
}
