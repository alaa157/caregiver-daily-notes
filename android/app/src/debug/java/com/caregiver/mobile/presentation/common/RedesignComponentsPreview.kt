package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.auth.AuthPrimaryButton

/**
 * Previews for the four shared redesign components (#894). They live in the
 * debug source set because ui-tooling-preview is a debugImplementation
 * dependency: previews in main would break the release/minified builds.
 */

@Preview(name = "SectionCard", showBackground = true, backgroundColor = 0xFFF6F8F7)
@Composable
private fun SectionCardPreview() {
    CaregiverTheme {
        SectionCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Today's notes",
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = CaregiverColors.Ink,
            )
            Text(
                text = "2 of 3 written",
                fontFamily = PlexArabic,
                fontSize = 15.sp,
                color = CaregiverColors.Muted,
            )
        }
    }
}

@Preview(name = "SectionCard ar (RTL)", showBackground = true, backgroundColor = 0xFFF6F8F7, locale = "ar")
@Composable
private fun SectionCardPreviewArabic() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CaregiverTheme {
            SectionCard(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ملاحظات اليوم",
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = CaregiverColors.Ink,
                )
                Text(
                    text = "كتبت ٢ من ٣",
                    fontFamily = PlexArabic,
                    fontSize = 15.sp,
                    color = CaregiverColors.Muted,
                )
            }
        }
    }
}

@Preview(name = "StatusChip done / not yet", showBackground = true, backgroundColor = 0xFFF6F8F7)
@Composable
private fun StatusChipPreview() {
    CaregiverTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            StatusChip(label = "Done", variant = StatusChipVariant.Done)
            StatusChip(label = "Not yet", variant = StatusChipVariant.NotYet)
        }
    }
}

@Preview(name = "StatusChip ar (RTL)", showBackground = true, backgroundColor = 0xFFF6F8F7, locale = "ar")
@Composable
private fun StatusChipPreviewArabic() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CaregiverTheme {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                StatusChip(label = "تم", variant = StatusChipVariant.Done)
                StatusChip(label = "لم بعد", variant = StatusChipVariant.NotYet)
            }
        }
    }
}

@Preview(name = "PersonAvatar sizes", showBackground = true, backgroundColor = 0xFFF6F8F7)
@Composable
private fun PersonAvatarPreview() {
    CaregiverTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            PersonAvatar(initial = "L", size = PersonAvatarSize.Small)
            PersonAvatar(initial = "L", size = PersonAvatarSize.Large)
        }
    }
}

@Preview(name = "PersonAvatar ar (RTL)", showBackground = true, backgroundColor = 0xFFF6F8F7, locale = "ar")
@Composable
private fun PersonAvatarPreviewArabic() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CaregiverTheme {
            PersonAvatar(initial = "ل", size = PersonAvatarSize.Large)
        }
    }
}

@Preview(name = "Primary button (reused)", showBackground = true, backgroundColor = 0xFFF6F8F7)
@Composable
private fun RedesignPrimaryButtonPreview() {
    CaregiverTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AuthPrimaryButton(text = "Write a note", onClick = {})
            Spacer(Modifier.height(12.dp))
            AuthPrimaryButton(text = "Saving", onClick = {}, busy = true)
        }
    }
}
