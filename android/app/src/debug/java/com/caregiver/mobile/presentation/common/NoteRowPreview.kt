package com.caregiver.mobile.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.core.theme.PlexArabic

/**
 * Task 4 previews. They live in the debug source set because
 * ui-tooling-preview is a debugImplementation dependency: previews in main
 * would break the release/minified builds that CI compiles.
 */

@Preview(name = "NoteRow en", showBackground = true, backgroundColor = 0xFFF7F6F3)
@Composable
private fun NoteRowPreviewEnglish() {
    CaregiverTheme {
        NoteRow(
            avatarLetter = "S",
            name = "Sara",
            time = "2h",
            body = "Slept through the night and ate most of breakfast. " +
                "Walked to the garden and back without help.",
            onClick = {},
            pills = {
                GreenPill("Mood: good")
                RedPill("Fall")
            },
            actions = {
                NoteRowAction("Add addendum")
                NoteRowAction("Summary", primary = false)
            },
        )
    }
}

@Preview(
    name = "NoteRow ar (RTL)",
    showBackground = true,
    backgroundColor = 0xFFF7F6F3,
    locale = "ar",
)
@Composable
private fun NoteRowPreviewArabic() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CaregiverTheme {
            NoteRow(
                avatarLetter = "ل",
                name = "ليلى حسن",
                time = "2h",
                body = "نامت طوال الليل وتناولت الفطور كاملًا تقريبًا، ثم مشت في الحديقة " +
                    "وعادت بمفردها بخطوات ثابتة دون أي مساعدة، وتناولت الغداء مع العائلة.",
                onClick = {},
                pills = {
                    GreenPill("الحالة جيدة")
                    YellowPill("ألم 4")
                },
                actions = {
                    NoteRowAction("إضافة ملحق")
                    NoteRowAction("الملخص", primary = false)
                },
            )
        }
    }
}

/** Sample action text so the preview shows the Task 4 action row shape. */
@Composable
private fun NoteRowAction(label: String, primary: Boolean = true) {
    Text(
        text = label,
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (primary) CaregiverColors.Primary else CaregiverColors.Muted,
    )
}
