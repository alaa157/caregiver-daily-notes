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
 * #904: one medication answer for the day as a single three-way segmented
 * row. The "Medication" label comes from the editor's existing `GroupLabel`,
 * so this row stays independent of the other option groups. There is
 * deliberately no expandable "which medication was missed" state — the note
 * model has no field for it and this issue must not change the model.
 */
internal val MedicationSegmentValues = listOf("taken", "missed", "unsure")

internal fun medicationSegmentLabelRes(value: String): Int = when (value) {
    "taken" -> R.string.editor_medication_all_taken
    "missed" -> R.string.editor_medication_something_missed
    "unsure" -> R.string.editor_medication_not_sure
    else -> R.string.editor_medication
}

/** Single-select row. [onSelect] is the editor's toggling select. */
@Composable
fun MedicationSegments(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectableGroup()
            .testTag("medication_segments"),
    ) {
        MedicationSegmentValues.forEach { value ->
            val label = stringResource(medicationSegmentLabelRes(value))
            val isSelected = selected == value
            Surface(
                shape = RoundedCornerShape(AppDimens.ControlRadius),
                color = if (isSelected) CaregiverColors.Primary else CaregiverColors.PillGrayContainer,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) CaregiverColors.Primary else CaregiverColors.PillGrayContent,
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
                    .testTag("medication_segment_$value"),
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
