package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 11: large font scales must not clip button labels. The shared
 * buttons keep a minimum height and grow with their content; a fixed height
 * would cut the wrapped second line off instead. Instrumentation tests:
 * they need a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class ButtonScaleUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryButtonGrowsPastItsMinimumWhenTheLabelWraps() {
        setButton {
            PrimaryButton(label = WRAPPING_LABEL, onClick = {}, modifier = Modifier.testTag("cta"))
        }
        assertGrew()
    }

    @Test
    fun secondaryButtonGrowsPastItsMinimumWhenTheLabelWraps() {
        setButton {
            SecondaryButton(label = WRAPPING_LABEL, onClick = {}, modifier = Modifier.testTag("cta"))
        }
        assertGrew()
    }

    private fun setButton(button: @Composable () -> Unit) {
        compose.setContent {
            // Density 1: px == dp, so the measured height reads in dp.
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CaregiverTheme {
                    Box(Modifier.width(300.dp)) { button() }
                }
            }
        }
    }

    private fun assertGrew() {
        val height = compose.onNodeWithTag("cta").fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "button must grow past its ${AppDimens.ButtonH} minimum instead of clipping at 200% text (height=$height px)",
            height > AppDimens.ButtonH.value,
        )
    }

    private companion object {
        const val WRAPPING_LABEL = "A label long enough to wrap at two hundred percent scale"
    }
}
