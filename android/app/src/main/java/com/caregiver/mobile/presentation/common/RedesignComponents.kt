package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic

const val SectionCardTestTag = "redesign_section_card"
const val StatusChipTestTag = "redesign_status_chip"
const val PersonAvatarTestTag = "redesign_person_avatar"

/**
 * The four building blocks of the Home / Recipient Detail / Note Editor
 * redesign (#892, #894). The three composables below are new; the fourth is
 * the auth redesign's 56-dp [com.caregiver.mobile.presentation.auth.AuthPrimaryButton]
 * (#875/#890), which is deliberately **reused as-is** — do not introduce a
 * second primary button for these screens.
 *
 * Sizing here is local to the redesign so no existing screen changes
 * appearance. Issue #893 owns the shared palette tokens; until those land,
 * the redesign's chip/card colours are kept private here so the two issues
 * can merge in either order without touching the same files.
 */

/** Card metrics from the prototype: white, 1 dp border, 20 dp radius, 16 dp padding. */
object SectionCardDimens {
    val CardRadius = 20.dp
    val CardBorder = 1.dp
    val CardPadding = 16.dp
}

private val SectionCardContainer = Color(0xFFFFFFFF)
private val SectionCardBorderColor = Color(0xFFE3E7EA)

/** "Done" chip: #0A524E on #DDF1EE (7.68:1 contrast). */
private val DoneChipContainer = Color(0xFFDDF1EE)
private val DoneChipContent = Color(0xFF0A524E)

/** "Not yet" chip: #7A4A00 on #FFF0D6 (6.66:1 contrast). */
private val NotYetChipContainer = Color(0xFFFFF0D6)
private val NotYetChipContent = Color(0xFF7A4A00)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SectionCardDimens.CardRadius),
        color = SectionCardContainer,
        border = BorderStroke(SectionCardDimens.CardBorder, SectionCardBorderColor),
        modifier = modifier.testTag(SectionCardTestTag),
    ) {
        Column(
            modifier = Modifier.padding(SectionCardDimens.CardPadding),
            content = content,
        )
    }
}

/** Which state a [StatusChip] represents. The label is always rendered, so
 * state is never communicated by colour alone. */
enum class StatusChipVariant { Done, NotYet }

@Composable
fun StatusChip(
    label: String,
    variant: StatusChipVariant,
    modifier: Modifier = Modifier,
) {
    val container = if (variant == StatusChipVariant.Done) DoneChipContainer else NotYetChipContainer
    val content = if (variant == StatusChipVariant.Done) DoneChipContent else NotYetChipContent
    Surface(
        shape = RoundedCornerShape(AppDimens.PillRadius),
        color = container,
        modifier = modifier.testTag(StatusChipTestTag),
    ) {
        Text(
            text = label,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The two avatar sizes used by the redesign: 48 dp in lists, 72 dp in the
 * Recipient Detail header (#897/#899). */
enum class PersonAvatarSize(val dp: Dp) {
    Small(48.dp),
    Large(72.dp),
}

@Composable
fun PersonAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    size: PersonAvatarSize = PersonAvatarSize.Small,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .background(CaregiverColors.AvatarContainer, CircleShape)
            .testTag(PersonAvatarTestTag),
    ) {
        Text(
            text = initial.take(1),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = if (size == PersonAvatarSize.Large) 28.sp else 20.sp,
            color = CaregiverColors.AvatarContent,
        )
    }
}
