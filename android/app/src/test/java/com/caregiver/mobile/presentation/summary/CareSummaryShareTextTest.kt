package com.caregiver.mobile.presentation.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share-text builder for the doctor share flow. Pure string logic — no
 * Android framework, no app launch, and no real messaging apps: the summary
 * text itself is ordinary test data, not a networked send.
 */
class CareSummaryShareTextTest {

    @Test
    fun englishMessageUsesActualSummaryAndDate() {
        val actualSummary = "Patient slept well. Mild evening knee pain, appetite stable."
        val text = CareSummaryShare.shareText(
            summaryText = actualSummary,
            dateText = "Oct 19, 2026",
            appName = "Caregiver Notes",
            arabic = false,
        )
        assertEquals(
            "Care Summary\nShared on: Oct 19, 2026\n\n$actualSummary\n\nShared from Caregiver Notes",
            text,
        )
    }

    @Test
    fun arabicMessageUsesActualSummaryAndDate() {
        val actualSummary = "نام المريض جيدًا. ألم خفيف في الركبة مساءً والشهية مستقرة."
        val text = CareSummaryShare.shareText(
            summaryText = actualSummary,
            dateText = "19 أكتوبر 2026",
            appName = "ملاحظات مقدم الرعاية",
            arabic = true,
        )
        assertEquals(
            "ملخص الرعاية\nتاريخ المشاركة: 19 أكتوبر 2026\n\n$actualSummary\n\nتمت المشاركة من تطبيق ملاحظات مقدم الرعاية",
            text,
        )
    }

    @Test
    fun blankSummaryHidesShareAction() {
        assertNull(CareSummaryShare.shareText("", "Oct 19, 2026", "Caregiver Notes", arabic = false))
        assertNull(CareSummaryShare.shareText("   \n  ", "19 أكتوبر 2026", "ملاحظات مقدم الرعاية", arabic = true))
    }

    @Test
    fun paragraphBreaksAndUnicodeSurviveIntoTheMessage() {
        // The generated summary is multi-paragraph and bilingual; the
        // outgoing plain-text message must carry it verbatim, spacing and
        // Unicode (Arabic, dashes) included.
        val summary = "نام المريض جيدًا.\n\nMorning walk taken — appetite improving.\n\nالرعاية مستقرة."
        val text = CareSummaryShare.shareText(
            summaryText = summary,
            dateText = "Oct 19, 2026",
            appName = "Caregiver Notes",
            arabic = false,
        )!!
        assertTrue(text.contains(summary))
    }
}
