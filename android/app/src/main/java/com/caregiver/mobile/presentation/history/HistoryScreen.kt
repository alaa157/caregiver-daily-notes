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
import androidx.compose.material.icons.filled.EventNote
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.time.LocalDate
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
        Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = title ?: stringResource(R.string.tab_history),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = CaregiverColors.Ink,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(R.string.history_from, from) { vm.setFrom(it) }
                DateField(R.string.history_to, to) { vm.setTo(it) }
            }
        }
        when (val s = state) {
            HistoryState.Loading -> LoadingRow()
            HistoryState.Error -> LoadFailed(onRetry = vm::refresh)
            is HistoryState.Content -> {
                if (s.content.entries.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Filled.EventNote,
                        title = stringResource(R.string.history_empty),
                        subtitle = stringResource(R.string.notes_empty),
                        actionLabel = stringResource(R.string.summary_period),
                        onAction = vm::refresh,
                    )
                } else {
                    val arabic = Locale.getDefault().language == "ar"
                    // The rail sits on the layout-start side: physical x must
                    // mirror in RTL because drawBehind offsets are physical,
                    // while padding(start) already flips automatically.
                    val railRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = AppDimens.GapCard)
                            .drawBehind {
                                val x = if (railRtl) size.width else 0f
                                drawLine(
                                    CaregiverColors.BorderSoft,
                                    Offset(x, 0f),
                                    Offset(x, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            .padding(start = AppDimens.GapCard),
                    ) {
                        items(s.content.entries, key = { it.note.id }) { entry ->
                            Box {
                                // Timeline dot — 12dp primary circle.
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(CaregiverColors.Primary, CircleShape),
                                )
                                AppCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navController.navigate("note/${entry.note.id}") },
                                ) {
                                    Text(
                                        text = DateFormats.historyDay(entry.note.date, arabic),
                                        fontFamily = PlexArabic,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = CaregiverColors.Ink,
                                    )
                                    Text(
                                        text = entry.note.text.take(120),
                                        fontFamily = PlexArabic,
                                        fontSize = 14.sp,
                                        color = CaregiverColors.Muted,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (entry.note.fall) {
                                            RedPill(stringResource(R.string.chip_fall))
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clickable { navController.navigate("note/${entry.note.id}") },
                                    ) {
                                        Text(
                                            stringResource(R.string.saved_view),
                                            fontFamily = PlexArabic,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = CaregiverColors.Primary,
                                        )
                                        Text(
                                            if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→",
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
                ?: stringResource(R.string.history_filter_all),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.history_filter_person), fontFamily = PlexArabic) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(AppDimens.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth().height(48.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_all), fontFamily = PlexArabic) },
                onClick = { onSelect(null); expanded = false },
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(Bidi.isolate(person.name), fontFamily = PlexArabic) },
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
        shape = RoundedCornerShape(AppDimens.ControlRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CaregiverColors.Surface,
            contentColor = CaregiverColors.Ink,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, CaregiverColors.Border),
        modifier = Modifier.weight(1f).height(48.dp),
    ) {
        val shown = date?.let { DateFormats.historyDay(it.toString(), arabic) } ?: "—"
        Text(
            stringResource(label) + ": " + shown,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
    if (open) {
        // UTC both ways: the picker deals in UTC-midnight millis, and the
        // API takes plain ISO dates — the device zone must not shift the day.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date?.let { HistoryDates.toPickerMillis(it) },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(
                        picker.selectedDateMillis?.let {
                            HistoryDates.fromPickerMillis(it)
                        },
                    )
                    open = false
                }) {
                    Text(stringResource(R.string.common_ok), fontFamily = PlexArabic)
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}
