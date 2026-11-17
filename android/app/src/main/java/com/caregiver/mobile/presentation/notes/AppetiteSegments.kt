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
import com.caregiver.mobile.presentation.common.AppDimens

/**
 * #903: appetite is one row with three 44 dp segments, not four per-meal
 * chips. The model stores a single value for the day, so the row writes one
 * of `good` / `fair` / `refused`. The legacy `poor` value is a display alias
 * for "A little" only — it is never rewritten in a stored note.
 */
internal enum class AppetiteChoice { Well, Little, Refused }

internal val AppetiteChoices = listOf(AppetiteChoice.Well, AppetiteChoice.Little, AppetiteChoice.Refused)

/** `poor` maps to A little for display only; [appetiteWireValue] still writes `fair`. */
internal fun appetiteChoiceFor(value: String?): AppetiteChoice? = when (value) {
    "good" -> AppetiteChoice.Well
    "fair", "poor" -> AppetiteChoice.Little
    "refused" -> AppetiteChoice.Refused
    else -> null
}

internal fun appetiteWireValue(choice: AppetiteChoice): String = when (choice) {
    AppetiteChoice.Well -> "good"
    AppetiteChoice.Little -> "fair"
    AppetiteChoice.Refused -> "refused"
}

internal fun appetiteChoiceLabelRes(choice: AppetiteChoice): Int = when (choice) {
    AppetiteChoice.Well -> R.string.editor_appetite_well
    AppetiteChoice.Little -> R.string.editor_appetite_little
    AppetiteChoice.Refused -> R.string.editor_appetite_refused
}

/**
 * [selected] is the editor's stored wire value. [onSelect] is the ViewModel's
 * toggling `select`, so tapping the already-selected value clears it; tapping
 * "A little" on a stored `poor` writes `fair` without touching the old note.
 */
@Composable
fun AppetiteSegments(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = appetiteChoiceFor(selected)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag("appetite_segments"),
    ) {
        AppetiteChoices.forEach { choice ->
            val label = stringResource(appetiteChoiceLabelRes(choice))
            val isSelected = chosen == choice
            val wire = appetiteWireValue(choice)
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
                    // One TalkBack node: label, radio role and selected state.
                    .clearAndSetSemantics {
                        contentDescription = label
                        semanticsSelected = isSelected
                    }
                    .clickable(role = Role.RadioButton) { onSelect(wire) }
                    .testTag("appetite_segment_$wire"),
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
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else CaregiverColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
