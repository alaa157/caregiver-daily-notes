/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/SongDropdownSelect.kt (GPL-3.0): a search field with an
 * anchored, width-matched DropdownMenu — type to filter, pick from up to 75
 * rows (each scrollable horizontally when the label is long), with an
 * optional "keep typing" footer when results were truncated.
 * Personalization: the item type is generic and the song/artist strings are
 * label hooks (Metrolist was hardwired to its SongWithStats entity and
 * R.string resources); behaviour is otherwise identical.
 */

package com.caregiver.mobile.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

@Composable
fun <T> SongSelectDropdown(
    titleT: String,
    items: List<T>,
    selectedItem: MutableState<T?>,
    itemTitle: (T) -> String,
    itemSubtitle: ((T) -> String)? = null,
    maxItemsShown: Int = 75,
    moreResultsText: ((Int) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var textFieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val filteredItems = items.filter { item ->
        itemTitle(item).contains(searchText, ignoreCase = true)
    }

    val visibleItems = filteredItems.take(maxItemsShown)
    val remainingCount = filteredItems.size - visibleItems.size

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    expanded = true
                    selectedItem.value = null
                },
                label = { Text(titleT) },
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        textFieldWidthPx = coordinates.size.width
                    }
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { textFieldWidthPx.toDp() }),
            properties = PopupProperties(focusable = false)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 160.dp) // the scroll "box"
                    .verticalScroll(rememberScrollState())
            ) {
                visibleItems.forEach { item ->
                    val scrollState = rememberScrollState()
                    DropdownMenuItem(
                        onClick = {
                            searchText = itemTitle(item)
                            selectedItem.value = item
                            expanded = false
                        },
                        text = {
                            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                                Text(
                                    text = itemTitle(item),
                                    maxLines = 1,
                                )
                                val subtitle = itemSubtitle?.invoke(item)
                                if (!subtitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subtitle,
                                        maxLines = 1,
                                        color = Color.Gray,
                                        overflow = TextOverflow.Ellipsis // Highly recommended for long labels
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (remainingCount > 0 && moreResultsText != null) {
                    DropdownMenuItem(
                        onClick = { /* no-op */ },
                        enabled = false,
                        text = {
                            Text(
                                text = moreResultsText(remainingCount),
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
