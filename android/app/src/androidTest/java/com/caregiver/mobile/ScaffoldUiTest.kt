package com.caregiver.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Task 1 scaffold coverage on a real renderer: the four tab labels in both
 * locales, tab selection, and the localized add-note action. These are
 * instrumentation tests — they need a device or emulator and never run
 * under `testDebugUnitTest`.
 *
 * Offline-safe by construction: they assert tab labels, selection state,
 * and the FAB only — never backend-driven screen content.
 */
@RunWith(AndroidJUnit4::class)
class ScaffoldUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScaffold(language: String) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        val graph = AppGraph(localized)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                WithTestOwner {
                    CaregiverTheme {
                        MainScaffold(graph)
                    }
                }
            }
        }
    }

    @Test
    fun englishTabLabelsRendered() {
        setScaffold("en")

        listOf("Home", "People", "Notes", "History").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Add note").assertIsDisplayed()
    }

    @Test
    fun arabicTabLabelsRendered() {
        setScaffold("ar")

        listOf("الرئيسية", "الأشخاص", "الملاحظات", "السجل").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("إضافة ملاحظة").assertIsDisplayed()
    }

    @Test
    fun tabsAreSelectable() {
        setScaffold("en")

        compose.onNodeWithTag("tab_home").assertIsSelected()
        compose.onNodeWithTag("tab_people").performClick()
        compose.onNodeWithTag("tab_people").assertIsSelected()
        compose.onNodeWithTag("tab_home").assertIsNotSelected()
    }

    @Test
    fun fabNavigatesToNotesTab() {
        setScaffold("en")

        compose.onNodeWithContentDescription("Add note").performClick()
        compose.onNodeWithTag("tab_notes").assertIsSelected()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
