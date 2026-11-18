package com.caregiver.mobile.presentation.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.theme.onAccent
import com.caregiver.mobile.presentation.common.AppDimens

/**
 * #903: one appetite value for the day, shown as a single three-way
 * segmented row instead of a chip cloud. The label ("Appetite") is supplied
 * by the editor's existing `GroupLabel` so this row stays independent of the
 * other option groups.
 */
internal val AppetiteSegmentValues = listOf("good", "fair", "refused")

/**
 * Display-only fold of the legacy `poor` code into the "A little" segment.
 * Stored notes are never rewritten: [selected] stays whatever the note held,
 * and the editor's toggling `select` writes `fair` when that segment is
 * tapped (because `poor != fair`).
 */
internal fun displayedAppetite(selected: String?): String? = when (selected) {
    "poor" -> "fair"
    "good", "fair", "refused" -> selected
    else -> null
}

internal fun appetiteSegmentLabelRes(value: String): Int = when (value) {
    "good" -> R.string.editor_appetite_well
    "fair" -> R.string.editor_appetite_little
    "refused" -> R.string.editor_appetite_refused
    else -> R.string.editor_appetite
}

/** Single-select segmented row. [onSelect] is the editor's toggling select. */
@Composable
fun AppetiteSegments(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = displayedAppetite(selected)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectableGroup()
            .testTag("appetite_segments"),
    ) {
        AppetiteSegmentValues.forEach { value ->
            val label = stringResource(appetiteSegmentLabelRes(value))
            val isSelected = shown == value
            Surface(
                shape = RoundedCornerShape(AppDimens.ControlRadius),
                color = if (isSelected) CaregiverColors.Primary else CaregiverColors.Surface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) CaregiverColors.Primary else CaregiverColors.Border,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    // One TalkBack node per segment; selectableGroup on the
                    // parent Row makes them read as a single group.
                    .clearAndSetSemantics {
                        contentDescription = label
                        semanticsSelected = isSelected
                    }
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .testTag("appetite_segment_$value"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    Text(
                        text = label,
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isSelected) onAccent(CaregiverColors.Primary) else CaregiverColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
