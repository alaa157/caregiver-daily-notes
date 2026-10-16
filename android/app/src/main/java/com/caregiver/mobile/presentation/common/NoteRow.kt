package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic

/**
 * X-style note timeline row (Task 4).
 *
 * Full-bleed row for a LazyColumn timeline: no card, no shadow, hairline
 * BorderSoft divider at the bottom, 40dp avatar on the layout-start side.
 * Lines: name + muted dot + relative time, note body (max 4 lines, then
 * "Show more"), pills slot, actions slot. [pending] rows (Task 8b, an
 * outbox note not yet on the server) carry a small clock glyph after the
 * time, like an unsent post.
 *
 * Plain values plus composable slots — no view model, DTO, or UI state type
 * crosses this API, so any screen can feed it. Names and relative times are
 * wrapped in [Bidi.isolate] exactly like HomeScreen, so mixed Arabic/Latin
 * content cannot reorder under RTL.
 */

/** Clock glyph marking a note still waiting to sync (Task 8b). */
private val NoteRowPendingIconSize = 14.dp

/** Gap between pills inside the pills slot row. */
private val NoteRowPillGap = 6.dp

/** Body lines shown before the overflow "Show more" appears. */
private const val NoteRowBodyMaxLines = 4

@Composable
fun NoteRow(
    avatarLetter: String,
    name: String,
    time: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    pills: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var bodyOverflows by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("note_row"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
            modifier = Modifier.padding(
                horizontal = AppSpacing.md,
                vertical = AppDimens.GapCard,
            ),
        ) {
            Avatar(letter = avatarLetter, modifier = Modifier.size(AppDimens.NoteRowAvatar))
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.GapTight),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Bidi.isolate(name),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CaregiverColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = " · " + Bidi.isolate(time),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                        maxLines = 1,
                    )
                    if (pending) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = stringResource(R.string.note_row_pending),
                            tint = CaregiverColors.Muted,
                            modifier = Modifier
                                .padding(start = AppDimens.GapTight)
                                .size(NoteRowPendingIconSize)
                                .testTag("note_row_pending"),
                        )
                    }
                }
                Text(
                    text = body,
                    fontFamily = PlexArabic,
                    fontSize = 15.sp,
                    color = CaregiverColors.Ink,
                    maxLines = if (expanded) Int.MAX_VALUE else NoteRowBodyMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (!expanded && layout.hasVisualOverflow) {
                            bodyOverflows = true
                        }
                    },
                )
                if (!expanded && bodyOverflows) {
                    Text(
                        text = stringResource(R.string.note_row_show_more),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CaregiverColors.Primary,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .testTag("note_row_show_more"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NoteRowPillGap)) { pills() }
                Row(horizontalArrangement = Arrangement.spacedBy(NoteRowPillGap)) { actions() }
            }
        }
        HorizontalDivider(thickness = AppDimens.Hairline, color = CaregiverColors.BorderSoft)
    }
}

/** Loading placeholder matching [NoteRow]'s rhythm, built from [SkeletonBar]. */
@Composable
fun NoteRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
            modifier = Modifier.padding(
                horizontal = AppSpacing.md,
                vertical = AppDimens.GapCard,
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.NoteRowAvatar)
                    .background(CaregiverColors.Skeleton, CircleShape),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
                modifier = Modifier.weight(1f),
            ) {
                SkeletonBar(widthFraction = 0.4f)
                SkeletonBar(widthFraction = 1f)
                SkeletonBar(widthFraction = 0.75f)
            }
        }
        HorizontalDivider(thickness = AppDimens.Hairline, color = CaregiverColors.BorderSoft)
    }
}
