package com.caregiver.mobile.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * History (board 9), also serving the Notes tab as today's list. Filters are
 * the recipient dropdown plus from/to date pickers; entries show per-day
 * rows with correction counts and open the note detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    graph: AppGraph,
    navController: NavController,
    initialRecipientId: String? = null,
    initialFrom: LocalDate? = null,
    initialTo: LocalDate? = null,
    showFilters: Boolean = true,
    title: String? = null,
) {
    val key = "history-$initialRecipientId-$initialFrom-$initialTo-$showFilters"
    val vm: HistoryViewModel = assistedViewModel(key) {
        HistoryViewModel(graph.apis, graph.auth, null, initialRecipientId, initialFrom, initialTo)
    }
    val state by vm.state.collectAsState()
    val recipientId by vm.recipientId.collectAsState()
    val from by vm.from.collectAsState()
    val to by vm.to.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        title?.let {
            Text(text = it, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }
        if (showFilters) {
            when (val s = state) {
                is HistoryState.Content -> PersonFilter(
                    people = s.content.recipients,
                    selected = recipientId,
                    onSelect = vm::setRecipient,
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(R.string.history_from, from) { vm.setFrom(it) }
                DateField(R.string.history_to, to) { vm.setTo(it) }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (val s = state) {
            HistoryState.Loading -> LoadingRow()
            HistoryState.Error -> LoadFailed(onRetry = vm::refresh)
            is HistoryState.Content -> {
                if (s.content.entries.isEmpty()) {
                    Text(stringResource(R.string.history_empty))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.content.entries, key = { it.note.id }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { navController.navigate("note/${entry.note.id}") },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(text = entry.note.date)
                                    Text(
                                        text = entry.note.text.take(120),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (entry.addendumCount > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.history_corrections,
                                                entry.addendumCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonFilter(
    people: List<com.caregiver.mobile.data.api.RecipientDto>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = people.firstOrNull { it.id == selected }?.name
                ?: stringResource(R.string.history_filter_all),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.history_filter_person)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_all)) },
                onClick = { onSelect(null); expanded = false },
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    onClick = { onSelect(person.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.DateField(label: Int, date: LocalDate?, onPick: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }, modifier = Modifier.weight(1f)) {
        Text(stringResource(label) + ": " + (date?.toString() ?: "—"))
    }
    if (open) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(
                        picker.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        },
                    )
                    open = false
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}
