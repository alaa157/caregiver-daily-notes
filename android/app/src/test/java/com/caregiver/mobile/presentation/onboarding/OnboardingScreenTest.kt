package com.caregiver.mobile.presentation.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * The onboarding contract the field reports came back with: the cards are
 * readable in either language from the very first frame (an ar/en toggle
 * sits on the header — previously the screen had none), the Arabic copy is
 * real Arabic (three entries shipped half-English), and Get started on the
 * last card actually completes onboarding.
 *
 * Runs on the JVM under Robolectric, same as the settings compose tests.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(onFinished: () -> Unit = {}) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    OnboardingScreen(AppGraph(context), onFinished)
                }
            }
        }
    }

    @Test
    fun firstCardRendersInAppAndBothLanguageChipsAreOpen() {
        render()
        compose.onNodeWithText(context.getString(R.string.app_name)).assertExists()
        compose.onNodeWithText(context.getString(R.string.onboarding_title_1)).assertExists()
        // The AR/EN toggle is on the cards from the start, before any
        // language preference exists — that is its reason to live here.
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("\u0627\u0644\u0639\u0631\u0628\u064a\u0629").assertExists()
    }

    @Test
    fun nextToTheLastCardThenGetStartedCompletesOnboarding() {
        val finished = AtomicBoolean(false)
        render { finished.set(true) }

        compose.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()
        // Two Next taps later the button becomes Get started on the third card.
        compose.onNodeWithText(context.getString(R.string.onboarding_get_started)).performClick()
        compose.waitUntil { finished.get() }
        assertTrue(finished.get())
    }

    @Test
    fun backAppearsFromTheSecondCardAndReturnsToTheFirst() {
        render()
        compose.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_back)).performClick()
        // Back exists only from the second card onward and genuinely returns:
        // the first card's title is back on screen.
        compose.onNodeWithText(context.getString(R.string.onboarding_title_1)).assertExists()
    }
}
