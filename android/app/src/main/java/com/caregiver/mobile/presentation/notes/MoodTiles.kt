@file:OptIn(ExperimentalLayoutApi::class)

package com.caregiver.mobile.presentation.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.common.AppDimens

/**
 * #902: the three headline moods are tiles (one tap, 76dp minimum) while the
 * clinically meaningful values stay writable behind a disclosure. Both sets
 * map to wire codes the note model already stores — no new values are added
 * and none are removed.
 */
internal val PrimaryMoodValues = listOf("happy", "calm", "sad")
internal val MoreMoodValues = listOf("anxious", "agitated", "confused")

/** A stored less-common mood must open the disclosure so it is never hidden. */
internal fun shouldExpandMoreMoods(selected: String?): Boolean = selected in MoreMoodValues

/** Tile label resource for a primary mood. Plain so it is unit-testable. */
internal fun moodTileLabelRes(value: String): Int = when (value) {
    "happy" -> R.string.editor_mood_tile_good
    "calm" -> R.string.editor_mood_tile_okay
    "sad" -> R.string.editor_mood_tile_low
    else -> R.string.editor_mood
}

@Composable
private fun moodTileIcon(value: String): ImageVector = when (value) {
    "happy" -> Icons.Filled.SentimentSatisfied
    "calm" -> Icons.Filled.SentimentNeutral
    "sad" -> Icons.Filled.SentimentDissatisfied
    else -> Icons.Filled.SentimentNeutral
}

/**
 * Single-select mood control. [selected] is the wire value from the editor
 * state; [onSelect] is the editor's toggling `select` (tapping a selected
 * tile clears the choice).
 */
@Composable
fun MoodTiles(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Initialise from a restored note, and also react when the note loads
    // asynchronously after the first composition (#902).
    var expanded by rememberSaveable { mutableStateOf(shouldExpandMoreMoods(selected)) }
    LaunchedEffect(selected) {
        if (shouldExpandMoreMoods(selected)) {
            expanded = true
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mood_tiles"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            PrimaryMoodValues.forEach { value ->
                val label = stringResource(moodTileLabelRes(value))
                val isSelected = selected == value
                Surface(
                    shape = RoundedCornerShape(AppDimens.SectionCardRadius),
                    color = if (isSelected) CaregiverColors.Primary else CaregiverColors.Surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) CaregiverColors.Primary else CaregiverColors.Border,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 76.dp)
                        // One TalkBack node: label + role + selected state.
                        // The explicit clickable keeps Role.RadioButton —
                        // Surface's own onClick would reset it to Button.
                        .clearAndSetSemantics {
                            contentDescription = label
                            semanticsSelected = isSelected
                        }
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .testTag("mood_tile_$value"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        Icon(
                            imageVector = moodTileIcon(value),
                            contentDescription = null,
                            tint = if (isSelected) Color.White else CaregiverColors.Primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) Color.White else CaregiverColors.Ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        val moreLabel = stringResource(R.string.editor_more_moods)
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = moreLabel }
                .testTag("mood_more_toggle"),
        ) {
            Text(
                text = moreLabel,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CaregiverColors.Primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = CaregiverColors.Primary,
            )
        }

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                MoreMoodValues.forEach { value ->
                    val label = optionLabel(OptionGroup.Mood, value)
                    val isSelected = selected == value
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(value) },
                        label = {
                            Text(
                                text = label,
                                fontFamily = PlexArabic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        },
                        shape = RoundedCornerShape(AppDimens.CardRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CaregiverColors.Surface,
                            labelColor = CaregiverColors.Ink,
                            selectedContainerColor = CaregiverColors.PrimarySoft,
                            selectedLabelColor = CaregiverColors.Primary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clearAndSetSemantics {
                                contentDescription = label
                                role = Role.RadioButton
                                semanticsSelected = isSelected
                            }
                            .testTag("mood_tile_$value"),
                    )
                }
            }
        }
    }
}
