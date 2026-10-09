package com.caregiver.mobile.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.data.demo.RecipientEntity
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.TimelineItem
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale

/**
 * History (spec screen 7): AppBar, filter row (period range + recipient),
 * TimelineItems grouped by day, BottomNav (scaffold). Filters update the
 * timeline while preserving RTL/LTR layout.
 * States: loading, empty, error, success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    graph: AppGraph,
    navController: NavController,
    initialRecipientId: String? = null,
    title: String? = null,
) {
    val key = "history-demo-$initialRecipientId"
    val vm: HistoryDemoViewModel = assistedViewModel(key) {
        HistoryDemoViewModel(graph.demoRecipients, graph.demoNotes, initialRecipientId)
    }
    val state by vm.state.collectAsState()
    val arabic = Locale.getDefault().language == "ar"
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(title = title ?: stringResource(R.string.nav_history))
        when (val s = state) {
            HistoryDemoState.Loading -> LoadingRow()
            HistoryDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            is HistoryDemoState.Content -> {
                PersonFilter(
                    people = s.recipients,
                    selected = s.selectedId,
                    onSelect = vm::setRecipient,
                    onAll = vm::setAll,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = s.periodDays == days,
                            onClick = { vm.setPeriod(days) },
                            label = { Text(periodLabel(days)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CaregiverColors.Surface,
                                labelColor = CaregiverColors.TextPrimary,
                                selectedContainerColor = CaregiverColors.Primary,
                                selectedLabelColor = CaregiverColors.Surface,
                            ),
                        )
                    }
                }
                if (s.days.isEmpty()) {
                    AppEmptyState(
                        icon = "○",
                        title = stringResource(R.string.nav_history),
                        subtitle = stringResource(R.string.people_empty),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = vm::refresh,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        s.days.forEach { day ->
                            item(key = "h-${day.date}") {
                                Text(
                                    text = DateFormats.historyDay(day.date, arabic),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CaregiverColors.TextPrimary,
                                )
                            }
                            items(day.notes, key = { it.note.id }) { entry ->
                                TimelineItem(
                                    dateCaption = DateFormats.historyDay(entry.note.date, arabic),
                                    summary = Bidi.isolate(entry.recipientName) +
                                        " · " + entry.note.text.take(120),
                                    onOpen = {
                                        navController.navigate(AppRoutes.noteDetail(entry.note.id))
                                    },
                                )
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
    people: List<RecipientEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = people.firstOrNull { it.id == selected }?.name
                // Unfiltered view shows the whole history.
                ?: stringResource(R.string.nav_history),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.nav_people)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_history)) },
                onClick = { onAll(); expanded = false },
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(Bidi.isolate(person.name)) },
                    onClick = { onSelect(person.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun periodLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.summary_period_7
        14 -> R.string.summary_period_14
        else -> R.string.summary_period_30
    },
)
