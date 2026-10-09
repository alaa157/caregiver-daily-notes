package com.caregiver.mobile.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * History (board 9), also serving the Notes tab as today's list.
 * HTML: header السجل + person, filter row (2× secondary 44dp), timeline
 * (2px start border + 12dp teal dots), cards (date bold + summary 14sp +
 * pills + teal link 15sp 44dp with arrow).
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
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.padding(top = AppSpacing.md)) {
            Text(
                text = title ?: stringResource(R.string.nav_history),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = CaregiverColors.TextPrimary,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                DateField(R.string.summary_period_7, from) { vm.setFrom(it) }
                DateField(R.string.summary_period_14, to) { vm.setTo(it) }
            }
        }
        when (val s = state) {
            HistoryState.Loading -> LoadingRow()
            HistoryState.Error -> LoadFailed(onRetry = vm::refresh)
            is HistoryState.Content -> {
                if (s.content.entries.isEmpty()) {
                    AppEmptyState(
                        icon = "📄",
                        title = stringResource(R.string.people_empty),
                        subtitle = stringResource(R.string.people_empty),
                        actionLabel = stringResource(R.string.summary_generate),
                        onAction = vm::refresh,
                    )
                } else {
                    val arabic = Locale.getDefault().language == "ar"
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = AppSpacing.sm)
                            .drawBehind {
                                drawLine(
                                    CaregiverColors.Border,
                                    Offset(0f, 0f),
                                    Offset(0f, size.height),
                                    strokeWidth = AppSizes.borderWidthStrong.toPx(),
                                )
                            }
                            .padding(start = AppSpacing.sm),
                    ) {
                        items(s.content.entries, key = { it.note.id }) { entry ->
                            Box {
                                // Timeline dot — HTML 12px teal circle.
                                Box(
                                    modifier = Modifier
                                        .size(AppSpacing.sm)
                                        .background(CaregiverColors.Primary, CircleShape),
                                )
                                AppCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navController.navigate("note/${entry.note.id}") },
                                ) {
                                    Text(
                                        text = DateFormats.historyDay(entry.note.date, arabic),
                                        fontFamily = AppFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = CaregiverColors.TextPrimary,
                                    )
                                    Text(
                                        text = entry.note.text.take(120),
                                        fontFamily = AppFontFamily,
                                        fontSize = 14.sp,
                                        color = CaregiverColors.TextSecondary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (entry.note.fall) {
                                            RedPill(stringResource(R.string.alert_redFlag_fall))
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .clickable { navController.navigate("note/${entry.note.id}") },
                                    ) {
                                        Text(
                                            stringResource(R.string.note_saved),
                                            fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = CaregiverColors.Primary,
                                        )
                                        Text(
                                            "←",
                                            fontSize = 18.sp,
                                            color = CaregiverColors.Primary,
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
                ?: stringResource(R.string.nav_history),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.nav_people), fontFamily = AppFontFamily) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(AppSpacing.sm),
            modifier = Modifier.menuAnchor().fillMaxWidth().height(AppSpacing.xxl),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_history), fontFamily = AppFontFamily) },
                onClick = { onSelect(null); expanded = false },
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(Bidi.isolate(person.name), fontFamily = AppFontFamily) },
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
    val arabic = Locale.getDefault().language == "ar"
    Button(
        onClick = { open = true },
        shape = RoundedCornerShape(AppSpacing.sm),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CaregiverColors.Surface,
            contentColor = CaregiverColors.TextPrimary,
        ),
        border = androidx.compose.foundation.BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        modifier = Modifier.weight(1f).height(44.dp),
    ) {
        val shown = date?.let { DateFormats.historyDay(it.toString(), arabic) } ?: "—"
        Text(
            stringResource(label) + ": " + shown,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
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
                    Text(stringResource(R.string.action_retry), fontFamily = AppFontFamily)
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}
