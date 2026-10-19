/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/SortHeader.kt (GPL-3.0): a reified-enum sort header — the
 * current sort label opens a radio-list dropdown, beside a descending-toggle
 * arrow. Personalized: metrolist excluded the toggle for the special
 * PlaylistSongSortType.CUSTOM constant; since this app has no such constant,
 * the existing `showDescending: Boolean? = true` parameter alone controls
 * it. Icons come from the material-icons-extended set (a build dependency
 * here), so no painter resources are involved.
 */

package com.caregiver.mobile.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
inline fun <reified T : Enum<T>> SortHeader(
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    crossinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
    showDescending: Boolean? = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(sortTypeText(sortType)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier =
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                ) {
                    menuExpanded = !menuExpanded
                }.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 172.dp),
        ) {
            enumValues<T>().forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sortTypeText(type)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector =
                            if (sortType == type) {
                                Icons.Filled.RadioButtonChecked
                            } else {
                                Icons.Filled.RadioButtonUnchecked
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onSortTypeChange(type)
                        menuExpanded = false
                    },
                )
            }
        }

        if (showDescending == true) {
            ResizableIconButton(
                icon = if (sortDescending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                Modifier
                    .size(32.dp)
                    .padding(8.dp),
                onClick = { onSortDescendingChange(!sortDescending) },
            )
        }
    }
}
