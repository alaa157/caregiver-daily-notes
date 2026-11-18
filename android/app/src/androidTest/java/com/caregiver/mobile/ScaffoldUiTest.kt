package com.caregiver.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Task 1 scaffold coverage on a real renderer: the four tab names (icon
 * content descriptions since Task 2) in both locales, tab selection, and
 * the localized add-note action. These are
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

        // Task 2 made the bar icon-only: the tab names live on the icons as
        // content descriptions (TalkBack), not as label text.
        listOf("Home", "People", "Notes", "History").forEach { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Add note").assertIsDisplayed()
    }

    @Test
    fun arabicTabLabelsRendered() {
        setScaffold("ar")

        listOf("الرئيسية", "الأشخاص", "الملاحظات", "السجل").forEach { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
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
    fun fabOpensRecipientPicker() {
        setScaffold("en")

        // Task 3: the FAB no longer detours through the People tab — the
        // picker opens in place with the two active demo people.
        compose.onNodeWithContentDescription("Add note").performClick()
        compose.onNodeWithText("Who is this note for?").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("picker_recipient_r1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab_home").assertIsSelected()
    }

    @Test
    fun addNoteActionIsEnabled() {
        setScaffold("en")

        compose.onNodeWithContentDescription("Add note")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addNoteActionStaysOnEveryMainTab() {
        setScaffold("en")

        // The FAB is tab-shell chrome: available from each of the four tabs,
        // and (covered by DrawerUiTest) never on detail/form screens. The
        // People tab adds a person, every other tab adds a note.
        listOf("tab_home" to "fab_add_note", "tab_people" to "fab_add_person", "tab_notes" to "fab_add_note", "tab_history" to "fab_add_note")
            .forEach { (tab, fab) ->
                compose.onNodeWithTag(tab).performClick()
                compose.onNodeWithTag(fab).assertIsDisplayed()
            }
    }

    @Test
    fun addNoteActionStaysClearOfTheBottomNavigation() {
        setScaffold("en")

        // The FAB is chrome, not an overlay: its laid-out bounds must end
        // above the bar, since a button covering a tab would swallow taps.
        val fab = compose.onNodeWithTag("fab_add_note").fetchSemanticsNode().boundsInRoot
        val homeTab = compose.onNodeWithTag("tab_home").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Add-note FAB (bottom=${fab.bottom}) overlaps the bottom bar (top=${homeTab.top})",
            fab.bottom <= homeTab.top,
        )
    }

    @Test
    fun peopleTabListsDemoRecipients() {
        setScaffold("en")

        compose.onNodeWithTag("tab_people").performClick()
        compose.onNodeWithTag("tab_people").assertIsSelected()
        compose.onNodeWithText(Bidi.isolate("ليلى حسن")).assertIsDisplayed()
    }

    @Test
    fun tabTapFromRecipientDetailReturnsToTheTabRoot() {
        setScaffold("en")

        compose.onNodeWithTag("tab_people").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(Bidi.isolate("ليلى حسن"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(Bidi.isolate("ليلى حسن")).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Add note").fetchSemanticsNodes().isNotEmpty()
        }

        // People stays visually selected while RecipientDetail is open, but
        // tapping it is still a request for the People root — not a no-op
        // ScrollToTop event.
        compose.onNodeWithTag("tab_people").performClick()

        compose.onNodeWithText("Add a person in care").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
