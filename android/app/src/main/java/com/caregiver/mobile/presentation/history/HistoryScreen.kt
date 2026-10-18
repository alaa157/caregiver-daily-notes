package com.caregiver.mobile.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.navigation.ScrollToTop
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.NoteRow
import com.caregiver.mobile.presentation.common.NoteRowSkeleton
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.assistedViewModel
import java.time.LocalDate
import java.util.Locale

/**
 * History (board 9) and the Notes tab (Task 6): one [NoteRow] per note with
 * a hairline divider, wrapped in pull-to-refresh, paginated client-side.
 *
 * The History tab keeps its person/date filters as a collapsible chip row
 * under the title; the Notes tab passes `showFilters = false` plus a preset
 * single-day range and gets no filter chips. Rows navigate to note detail
 * exactly like the old cards did.
 *
 * Re-tapping this tab in the bottom bar scrolls the list back to the first
 * row via [ScrollToTop].
 */
@Composable
fun HistoryScreen(
    graph: AppGraph,
    navController: NavController,
    initialRecipientId: String? = null,
    initialFrom: LocalDate? = null,
    initialTo: LocalDate? = null,
    showFilters: Boolean = true,
    title: String? = null,
    scrollRoute: String = if (showFilters) MainTab.History.route else MainTab.Notes.route,
) {
    val key = "history-$initialRecipientId-$initialFrom-$initialTo-$showFilters"
    val vm: HistoryViewModel = assistedViewModel(key) {
        HistoryViewModel(graph.apis, graph.auth, null, initialRecipientId, initialFrom, initialTo)
    }
    val state by vm.state.collectAsState()
    val recipientId by vm.recipientId.collectAsState()
    val from by vm.from.collectAsState()
    val to by vm.to.collectAsState()

    val listState = rememberLazyListState()
    // Task 2 contract: re-tapping the active tab publishes its route; scroll
    // this list back to the first row when it is ours. Both tabs share this
    // screen, so the route comes in as a parameter (its default follows
    // showFilters: the Notes tab is the only caller passing false).
    LaunchedEffect(scrollRoute) {
        ScrollToTop.events.collect { route ->
            if (route == scrollRoute && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Title and filter chips keep the 16dp gutters; the note timeline
        // below is full-bleed because NoteRow carries its own padding.
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.ScreenHPadding,
                    top = AppDimens.ScreenHPadding,
                    end = AppDimens.ScreenHPadding,
                    bottom = AppDimens.GapForm,
                ),
        ) {
            Text(
                text = title ?: stringResource(R.string.tab_history),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = CaregiverColors.Ink,
            )
            if (showFilters) {
                HistoryFilterChips(
                    people = (state as? HistoryState.Content)?.content?.recipients.orEmpty(),
                    selected = recipientId,
                    from = from,
                    to = to,
                    onSelectRecipient = vm::setRecipient,
                    onPickFrom = vm::setFrom,
                    onPickTo = vm::setTo,
                )
            }
        }
        when (val s = state) {
            HistoryState.Loading -> HistoryLoadingRows()
            HistoryState.Error -> HistoryError(onRetry = vm::refresh)
            is HistoryState.Content -> HistoryFeed(
                content = s.content,
                refreshing = s.refreshing,
                onRefresh = vm::refresh,
                listState = listState,
                navController = navController,
            )
        }
    }
}

/** Skeleton rows shown on the first load. */
private const val HISTORY_LOADING_ROWS = 4

/** Rows revealed per client-side page (the API has no paging). */
private const val HISTORY_PAGE_SIZE = 20

@Composable
private fun HistoryLoadingRows() {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(AppDimens.GapForm))
        repeat(HISTORY_LOADING_ROWS) { NoteRowSkeleton() }
    }
}

@Composable
private fun HistoryError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenHPadding),
    ) {
        Spacer(Modifier.height(AppDimens.SectionGap))
        AppErrorState(
            message = stringResource(R.string.common_loading_failed),
            onRetry = onRetry,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFeed(
    content: HistoryContent,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState,
    navController: NavController,
) {
    if (content.entries.isEmpty()) {
        Box(
            modifier = Modifier.padding(
                horizontal = AppDimens.ScreenHPadding,
                vertical = AppDimens.SectionGap,
            ),
        ) {
            AppEmptyState(
                icon = Icons.Filled.EventNote,
                title = stringResource(R.string.history_empty),
                subtitle = stringResource(R.string.notes_empty),
                actionLabel = stringResource(R.string.summary_period),
                onAction = onRefresh,
            )
        }
        return
    }
    val arabic = Locale.getDefault().language == "ar"
    // Notes carry the recipient id only; resolve display names from the
    // recipients of the same load (a note of a deleted recipient renders
    // without a name).
    val byId = remember(content.recipients) { content.recipients.associateBy { it.id } }
    // TODO(api-gap): GET /api/notes has no limit/pagination parameter, so the
    // whole (unbounded) history arrives in one response — see
    // docs/api/android-gaps.md, "History has no server-side bound". Page the
    // timeline client-side until the API can bound the response.
    var visibleCount by remember(content.entries) { mutableIntStateOf(HISTORY_PAGE_SIZE) }
    val visibleEntries = remember(content.entries, visibleCount) {
        content.entries.take(visibleCount)
    }
    // Reveal the next page once the last five revealed rows are on screen.
    LaunchedEffect(content.entries, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (visibleCount < content.entries.size && lastVisible >= visibleCount - 5) {
                    visibleCount = minOf(visibleCount + HISTORY_PAGE_SIZE, content.entries.size)
                }
            }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("history_list"),
        ) {
            items(visibleEntries, key = { it.note.id }) { entry ->
                HistoryNoteRow(
                    entry = entry,
                    name = byId[entry.note.recipientId]?.name.orEmpty(),
                    arabic = arabic,
                    onOpen = { navController.navigate(AppRoutes.noteDetail(entry.note.id)) },
                )
            }
        }
    }
}

/** One note as a timeline row: recipient name, note date, note body. */
@Composable
private fun HistoryNoteRow(
    entry: HistoryEntry,
    name: String,
    arabic: Boolean,
    onOpen: () -> Unit,
) {
    NoteRow(
        avatarLetter = name.take(1),
        name = name,
        time = DateFormats.historyDay(entry.note.date, arabic),
        body = entry.note.text,
        pending = entry.note.pending,
        onClick = onOpen,
        pills = {
            if (entry.note.fall) {
                RedPill(stringResource(R.string.chip_fall))
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
                modifier = Modifier
                    .heightIn(min = AppDimens.ButtonH)
                    .clickable(onClick = onOpen),
            ) {
                Text(
                    text = stringResource(R.string.saved_view),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CaregiverColors.Primary,
                )
                Text(
                    // Directional arrow: mirrors in RTL, like the old card.
                    text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→",
                    fontSize = 18.sp,
                    color = CaregiverColors.Primary,
                )
            }
        },
    )
}

/**
 * Collapsible filter row: a toggle chip plus person/date chips. Each chip
 * shows its current value; tapping opens the dropdown or date picker. The
 * row scrolls horizontally when the chips outgrow a narrow screen.
 */
@Composable
private fun HistoryFilterChips(
    people: List<RecipientDto>,
    selected: String?,
    from: LocalDate?,
    to: LocalDate?,
    onSelectRecipient: (String?) -> Unit,
    onPickFrom: (LocalDate?) -> Unit,
    onPickTo: (LocalDate?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        AssistChip(
            onClick = { expanded = !expanded },
            label = {
                Text(
                    stringResource(R.string.history_filters),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSm),
                )
            },
            modifier = Modifier.testTag("history_filters_toggle"),
        )
        if (expanded) {
            PersonFilterChip(
                people = people,
                selected = selected,
                onSelect = onSelectRecipient,
            )
            DateFilterChip(
                label = R.string.history_from,
                date = from,
                onPick = onPickFrom,
                tag = "history_filter_from",
            )
            DateFilterChip(
                label = R.string.history_to,
                date = to,
                onPick = onPickTo,
                tag = "history_filter_to",
            )
        }
    }
}

@Composable
private fun PersonFilterChip(
    people: List<RecipientDto>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.history_filter_all)
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = {
                val name = people.firstOrNull { it.id == selected }
                    ?.name
                    ?.let { Bidi.isolate(it) }
                    ?: stringResource(R.string.history_filter_all)
                Text(
                    stringResource(R.string.history_filter_person) + ": " + name,
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                )
            },
            modifier = Modifier
                .testTag("history_filter_person")
                .semantics {
                    stateDescription = people.firstOrNull { it.id == selected }?.name
                        ?: allLabel
                },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_all), fontFamily = PlexArabic) },
                onClick = { onSelect(null); expanded = false },
                modifier = Modifier.testTag("history_person_option_all"),
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(Bidi.isolate(person.name), fontFamily = PlexArabic) },
                    onClick = { onSelect(person.id); expanded = false },
                    modifier = Modifier.testTag("history_person_option_" + person.id),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChip(
    label: Int,
    date: LocalDate?,
    onPick: (LocalDate?) -> Unit,
    tag: String,
) {
    var open by remember { mutableStateOf(false) }
    val arabic = Locale.getDefault().language == "ar"
    FilterChip(
        selected = date != null,
        onClick = { open = true },
        label = {
            val shown = date?.let { DateFormats.historyDay(it.toString(), arabic) } ?: "—"
            Text(
                stringResource(label) + ": " + shown,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
            )
        },
        modifier = Modifier
            .testTag(tag)
            .semantics {
                stateDescription = date?.let { DateFormats.historyDay(it.toString(), arabic) } ?: "—"
            },
    )
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
