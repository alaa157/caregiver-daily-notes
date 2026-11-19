package com.caregiver.mobile.presentation.common

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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 4 note timeline row: an Arabic name renders in RTL with its pills,
 * an English name renders in LTR, and the row exposes its test tag. Plain
 * values only — no view models, no backend. Instrumentation-only; needs a
 * device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class NoteRowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private var capturedDirection: LayoutDirection? = null

    @Test
    fun arabicNameRendersWithPills() {
        render("ar") {
            NoteRow(
                avatarLetter = "ل",
                name = "ليلى حسن",
                time = "2h",
                body = "نامت جيدًا وتناولت الفطور.",
                onClick = {},
                pills = { RedPill("ألم 6") },
            )
        }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithTag("note_row").assertIsDisplayed()
        compose.onNodeWithText(Bidi.isolate("ليلى حسن")).assertIsDisplayed()
        compose.onNodeWithText("نامت جيدًا وتناولت الفطور.").assertIsDisplayed()
        compose.onNodeWithText("ألم 6").assertIsDisplayed()
    }

    @Test
    fun englishNameRendersWithoutSlots() {
        render("en") {
            NoteRow(
                avatarLetter = "S",
                name = "Sara",
                time = "2h",
                body = "Slept well and ate breakfast.",
                onClick = {},
            )
        }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithTag("note_row").assertIsDisplayed()
        compose.onNodeWithText(Bidi.isolate("Sara")).assertIsDisplayed()
        compose.onNodeWithText("Slept well and ate breakfast.").assertIsDisplayed()
    }

    /**
     * #914: a caller without a date (the old Home card passed an empty time)
     * must never produce a standalone " · " node — in LTR or RTL.
     */
    @Test
    fun blankTimeNeverEmitsABareSeparatorInEitherLanguage() {
        render("en") {
            NoteRow(
                avatarLetter = "S",
                name = "Sarah",
                time = "",
                body = "Slept well",
                onClick = {},
            )
        }
        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithText(Bidi.isolate("Sarah")).assertIsDisplayed()
        compose.onNodeWithTag("note_row_time").assertDoesNotExist()
        compose.onAllNodesWithText(" · ").assertCountEquals(0)

        render("ar") {
            NoteRow(
                avatarLetter = "س",
                name = "سارة",
                time = "",
                body = "نامت جيدًا",
                onClick = {},
            )
        }
        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText(Bidi.isolate("سارة")).assertIsDisplayed()
        compose.onNodeWithTag("note_row_time").assertDoesNotExist()
        compose.onAllNodesWithText(" · ").assertCountEquals(0)
    }

    /** #914: with a date, the shared name-dot-date formatting is intact. */
    @Test
    fun dateSuffixStillUsesTheNameDotDateFormat() {
        render("en") {
            NoteRow(
                avatarLetter = "S",
                name = "Sarah",
                time = "Nov 17, 2026",
                body = "Slept well",
                onClick = {},
            )
        }
        compose.onNodeWithTag("note_row_time").assertIsDisplayed()
        compose.onNodeWithText(" · " + Bidi.isolate("Nov 17, 2026")).assertIsDisplayed()
    }

    private fun render(language: String, content: @Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                capturedDirection = LocalLayoutDirection.current
                CaregiverTheme(content = content)
            }
        }
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
