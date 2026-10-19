/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/ChipsRow.kt (GPL-3.0): a generic filter/sort chip row, plus
 * a dropdown-led variant whose chip set slide-swaps via AnimatedContent.
 * Both are primitives called directly — no wrapper class behind the call
 * sites. Personalization: metrolist pads the row with system-bar insets
 * (edge-to-edge layout) — this app's Scaffold content already carries its
 * insets, so the row is inset-free and callers add ScreenHPadding; the icon
 * uses the App Icon set (no drawable); the dropdown item type became a second
 * generic (metrolist paired `OptionStats` with an `Int` shadow). ChipsRow
 * takes optional label-style and selected-tint hooks (default = metrolist
 * behavior) so bilingual boards can keep the PlexArabic labels and the
 * PrimarySoft selection tint.
 */

package com.caregiver.mobile.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** One chip colors spec, metrolist default unless board tint hooks are set. */
private fun chipColors(
    containerColor: Color,
    selectedContainerColor: Color,
    selectedLabelColor: Color,
) = if (selectedContainerColor == Color.Unspecified) {
    FilterChipDefaults.filterChipColors(containerColor = containerColor)
} else {
    FilterChipDefaults.filterChipColors(
        containerColor = containerColor,
        selectedContainerColor = selectedContainerColor,
        selectedLabelColor = selectedLabelColor,
    )
}

/**
 * Filter/sort chips in a borderless, horizontally scrolling row
 * (metrolist `ChipsRow`). Fills width; callers pad for the screen gutters.
 */
@Composable
fun <V> ChipsRow(
    chips: List<Pair<V, String>>,
    currentValue: V,
    onValueUpdate: (V) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    textStyle: TextStyle = LocalTextStyle.current,
    selectedContainerColor: Color = Color.Unspecified,
    selectedLabelColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            FilterChip(
                label = { Text(text = label, style = textStyle) },
                selected = currentValue == value,
                colors = chipColors(containerColor, selectedContainerColor, selectedLabelColor),
                onClick = { onValueUpdate(value) },
                shape = RoundedCornerShape(16.dp),
                border = null
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * A chip row led by a dropdown "group" chip (metrolist `ChoiceChipsRow`):
 * selecting a group slide-swaps the chip set via [AnimatedContent]. The
 * chevron rotates 180° per toggle/selection on the same 400ms tween.
 */
@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun <G, V> ChoiceChipsRow(
    groups: List<Pair<G, String>>,
    selectedGroup: G,
    onGroupChange: (G) -> Unit,
    chips: List<Pair<V, String>>,
    currentValue: V,
    onValueUpdate: (V) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    var expandIconDegree by remember { mutableFloatStateOf(0f) }
    val rotationAnimation by animateFloatAsState(
        targetValue = expandIconDegree,
        animationSpec = tween(durationMillis = 400),
        label = "",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column {
            AssistChip(
                onClick = {
                    expanded = !expanded
                    expandIconDegree -= 180
                },
                label = {
                    Text(
                        text = groups
                            .firstOrNull { it.first == selectedGroup }
                            ?.second
                            .orEmpty(),
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = rotationAnimation },
                    )
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = containerColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandIn() + fadeIn(),
                exit = shrinkOut() + fadeOut(),
            ) {
                DropdownMenu(
                    modifier = Modifier.padding(start = 12.dp),
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        expandIconDegree -= 180
                    },
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(text = group.second) },
                            onClick = {
                                onGroupChange(group.first)
                                expandIconDegree -= 180
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedGroup,
            transitionSpec = { slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut() },
            label = "",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                chips.forEach { (value, label) ->
                    Spacer(Modifier.width(8.dp))

                    FilterChip(
                        label = { Text(label) },
                        selected = currentValue == value,
                        colors = chipColors(containerColor, Color.Unspecified, Color.Unspecified),
                        onClick = { onValueUpdate(value) },
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }
        }
    }
}
