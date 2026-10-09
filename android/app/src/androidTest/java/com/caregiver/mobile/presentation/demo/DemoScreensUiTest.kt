package com.caregiver.mobile.presentation.demo

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.auth.DemoSignInScreen
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.plans.PlanProposalScreen
import com.caregiver.mobile.presentation.plans.PlanVersionsScreen
import com.caregiver.mobile.presentation.recipients.RecipientDetailScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen
import com.caregiver.mobile.presentation.saved.SavedScreen
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 8: every spec screen renders in English (LTR) and Arabic (RTL)
 * against seeded Room data. Counts mirror the seeded set (Layla H. /
 * Omar K. / Salem N., 10 notes, 1 fall). Instrumentation-only: needs a
 * device or emulator; compile-verified in CI via androidTest sources.
 */
@RunWith(AndroidJUnit4::class)
class DemoScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph
    private var capturedDirection: LayoutDirection? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.demoNotes.resetToSeed()
        }
    }

    private fun render(language: String, content: @Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                capturedDirection = LocalLayoutDirection.current
                WithTestOwner {
                    CaregiverTheme(content = content)
                }
            }
        }
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }

    // 1. Sign in (demo gate) -------------------------------------------

    @Test
    fun signInEnglishLtr() {
        render("en") { DemoSignInScreen(graph) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_signin").assertIsDisplayed()
        compose.onNodeWithText("[APP NAME - supplied by owner]").assertIsDisplayed()
        compose.onNodeWithText("Start demo").assertIsDisplayed()
        compose.onNodeWithText("DEMO").assertIsDisplayed()
    }

    @Test
    fun signInArabicRtl() {
        render("ar") { DemoSignInScreen(graph) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_signin").assertIsDisplayed()
        compose.onNodeWithText("[اسم التطبيق - يحدده المالك]").assertIsDisplayed()
        compose.onNodeWithText("Start demo").assertIsDisplayed()
    }

    // 2. Home ----------------------------------------------------------

    @Test
    fun homeEnglishLtr() {
        render("en") { HomeScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onAllNodesWithText("Add today’s note").assertCountEquals(2)
        compose.onNodeWithText("View summary").assertIsDisplayed()
        compose.onNodeWithText("Ate well today", substring = true).assertIsDisplayed()
    }

    @Test
    fun homeArabicRtl() {
        render("ar") { HomeScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        compose.onNodeWithText("مساء الخير").assertIsDisplayed()
        compose.onAllNodesWithText("أضف ملاحظة اليوم").assertCountEquals(2)
        compose.onNodeWithText("عرض الملخص").assertIsDisplayed()
    }

    // 3. Care recipients ------------------------------------------------

    @Test
    fun recipientsEnglishLtr() {
        render("en") { RecipientsScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_recipients").assertIsDisplayed()
        compose.onNodeWithText("Care recipients").assertIsDisplayed()
        compose.onNodeWithText("Add care recipient").assertIsDisplayed()
        compose.onNodeWithText("Layla H.").assertIsDisplayed()
    }

    @Test
    fun recipientsArabicRtl() {
        render("ar") { RecipientsScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_recipients").assertIsDisplayed()
        compose.onNodeWithText("المرضى").assertIsDisplayed()
        compose.onNodeWithText("إضافة شخص لتلقي الرعاية").assertIsDisplayed()
    }

    // 4. Recipient detail ------------------------------------------------

    @Test
    fun recipientDetailEnglishLtr() {
        render("en") { RecipientDetailScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_recipient_detail").assertIsDisplayed()
        compose.onAllNodesWithText("Layla H.").assertCountEquals(2)
        compose.onNodeWithText("Add today’s note").assertIsDisplayed()
        compose.onAllNodesWithText("A fall was recorded. Consider contacting a doctor.").assertCountEquals(2)
    }

    @Test
    fun recipientDetailArabicRtl() {
        render("ar") { RecipientDetailScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_recipient_detail").assertIsDisplayed()
        compose.onAllNodesWithText("Layla H.").assertCountEquals(2)
        compose.onNodeWithText("أضف ملاحظة اليوم").assertIsDisplayed()
    }

    // 5. Daily note editor ------------------------------------------------

    @Test
    fun noteEditorEnglishLtr() {
        render("en") { NoteEditorScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_note_editor").assertIsDisplayed()
        compose.onNodeWithText("Daily note").assertIsDisplayed()
        compose.onNodeWithText("Mood").assertIsDisplayed()
        compose.onNodeWithText("Falls").assertIsDisplayed()
        compose.onNodeWithText("Save note").assertIsDisplayed()
    }

    @Test
    fun noteEditorArabicRtl() {
        render("ar") { NoteEditorScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_note_editor").assertIsDisplayed()
        compose.onNodeWithText("الملاحظة اليومية").assertIsDisplayed()
        compose.onNodeWithText("الحالة المزاجية").assertIsDisplayed()
        compose.onNodeWithText("حفظ الملاحظة").assertIsDisplayed()
    }

    // 6. Note detail -------------------------------------------------------

    @Test
    fun noteDetailEnglishLtr() {
        render("en") { NoteDetailScreen("n1", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_note_detail").assertIsDisplayed()
        compose.onNodeWithText("Daily note").assertIsDisplayed()
        compose.onNodeWithText("Mood", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ate well today", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Add correction").assertCountEquals(2)
    }

    @Test
    fun noteDetailArabicRtl() {
        render("ar") { NoteDetailScreen("n1", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_note_detail").assertIsDisplayed()
        compose.onNodeWithText("الملاحظة اليومية").assertIsDisplayed()
        compose.onAllNodesWithText("إضافة تصحيح").assertCountEquals(2)
    }

    // 7. History ------------------------------------------------------------

    @Test
    fun historyEnglishLtr() {
        render("en") { HistoryScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_history").assertIsDisplayed()
        compose.onAllNodesWithText("History").assertCountEquals(2)
        compose.onNodeWithText("Last 30 days").assertIsDisplayed()
        compose.onNodeWithText("Ate well today", substring = true).assertIsDisplayed()
    }

    @Test
    fun historyArabicRtl() {
        render("ar") { HistoryScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_history").assertIsDisplayed()
        compose.onAllNodesWithText("السجل").assertCountEquals(2)
        compose.onNodeWithText("آخر 30 يومًا").assertIsDisplayed()
    }

    // 8. Summary ---------------------------------------------------------------

    @Test
    fun summaryEnglishLtr() {
        render("en") { SummaryPeriodScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_summary").assertIsDisplayed()
        compose.onNodeWithText("View summary").assertIsDisplayed()
        compose.onNodeWithText("Last 14 days").assertIsDisplayed()
        compose.onNodeWithText("Generate summary").assertIsDisplayed()
    }

    @Test
    fun summaryArabicRtl() {
        render("ar") { SummaryPeriodScreen("r-layla", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_summary").assertIsDisplayed()
        compose.onNodeWithText("عرض الملخص").assertIsDisplayed()
        compose.onNodeWithText("آخر 14 يومًا").assertIsDisplayed()
        compose.onNodeWithText("إنشاء ملخص").assertIsDisplayed()
    }

    // 9. Plan proposal ------------------------------------------------------------

    @Test
    fun planProposalEnglishLtr() {
        render("en") { PlanProposalScreen("demo", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_plan_proposal").assertIsDisplayed()
        compose.onAllNodesWithText("Accept plan").assertCountEquals(2)
        compose.onNodeWithText("Small frequent meals").assertIsDisplayed()
        compose.onAllNodesWithText("Care-support suggestions generated from your notes. Not medical advice.").assertCountEquals(2)
        compose.onNodeWithText("Demo").assertIsDisplayed()
    }

    @Test
    fun planProposalArabicRtl() {
        render("ar") { PlanProposalScreen("demo", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_plan_proposal").assertIsDisplayed()
        compose.onAllNodesWithText("قبول الخطة").assertCountEquals(2)
        compose.onNodeWithText("وجبات صغيرة متكررة").assertIsDisplayed()
    }

    // 10. Plan history ---------------------------------------------------------------

    @Test
    fun planHistoryEnglishLtr() {
        render("en") { PlanVersionsScreen("demo", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_plan_history").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("v2").assertIsDisplayed()
        compose.onNodeWithText("v1").assertIsDisplayed()
        compose.onAllNodesWithText("Suggested").assertCountEquals(2)
    }

    @Test
    fun planHistoryArabicRtl() {
        render("ar") { PlanVersionsScreen("demo", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_plan_history").assertIsDisplayed()
        compose.onNodeWithText("السجل").assertIsDisplayed()
        compose.onNodeWithText("v2").assertIsDisplayed()
    }

    // 11. Saved -------------------------------------------------------------------------

    @Test
    fun savedEnglishLtr() {
        render("en") { SavedScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_saved").assertIsDisplayed()
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithText("Ate well today", substring = true).assertIsDisplayed()
    }

    @Test
    fun savedArabicRtl() {
        render("ar") { SavedScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_saved").assertIsDisplayed()
        compose.onNodeWithText("المحفوظات").assertIsDisplayed()
    }

    // 12. Settings ---------------------------------------------------------------------------

    @Test
    fun settingsEnglishLtr() {
        render("en") { SettingsScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onAllNodesWithText("Settings").assertCountEquals(2)
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Reset demo data").assertIsDisplayed()
        compose.onNodeWithText("Demo").assertIsDisplayed()
    }

    @Test
    fun settingsArabicRtl() {
        render("ar") { SettingsScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onAllNodesWithText("الإعدادات").assertCountEquals(2)
    }
}
