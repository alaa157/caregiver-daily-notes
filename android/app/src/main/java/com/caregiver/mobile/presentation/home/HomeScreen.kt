package com.caregiver.mobile.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.NoteRow
import com.caregiver.mobile.presentation.common.NoteRowSkeleton
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.OptionLabels
import java.util.Locale

/**
 * Home timeline (Task 5): greeting, compact stats, then one [NoteRow] per
 * care recipient, filtered by the Today / Needs attention / All sub-tabs.
 *
 * The fall-safety banner is pinned OUTSIDE the feed (Rule 6): it sits under
 * the shared top bar and above the sub-tabs, so scrolling can never hide it.
 * Filtering is pure client-side over the dashboard's single bounded load —
 * one row per recipient, so no pagination and no per-tab request.
 */
@Composable
fun HomeScreen(graph: AppGraph, navController: NavController) {
    val vm: HomeViewModel = assistedViewModel("home") {
        HomeViewModel(graph.auth, graph.settings)
    }
    val state by vm.state.collectAsState()
    when (val s = state) {
        HomeState.Loading, HomeState.Idle -> HomeLoadingRows()
        is HomeState.Error -> HomeErrorState(
            flags = s.lastContent?.flags.orEmpty(),
            onRetry = vm::refresh,
        )
        is HomeState.Content -> HomeTimeline(
            content = s.content,
            refreshing = s.refreshing,
            onRefresh = vm::refresh,
            navController = navController,
        )
    }
}

/** Skeleton rows shown while the dashboard loads (Task 5). */
private const val LOADING_ROWS = 4

private val HomeTab.testTag: String
    get() = when (this) {
        HomeTab.Today -> "home_tab_today"
        HomeTab.NeedsAttention -> "home_tab_attention"
        HomeTab.All -> "home_tab_all"
    }

private val HomeTab.labelRes: Int
    get() = when (this) {
        HomeTab.Today -> R.string.home_tab_today
        HomeTab.NeedsAttention -> R.string.home_tab_attention
        HomeTab.All -> R.string.home_tab_all
    }

@Composable
private fun HomeLoadingRows() {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(AppDimens.GapForm))
        repeat(LOADING_ROWS) { NoteRowSkeleton() }
    }
}

@Composable
private fun HomeErrorState(flags: List<SafetyFlag>, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenHPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        Spacer(Modifier.height(AppDimens.GapCard))
        // Retained flags stay pinned here too: a failed refresh must never
        // hide a known fall (same rule as the content path below).
        if (flags.isNotEmpty()) {
            SafetyBanner(flags)
        }
        AppErrorState(
            message = stringResource(R.string.common_loading_failed),
            onRetry = onRetry,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTimeline(
    content: HomeContent,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    navController: NavController,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Today) }
    val listState = rememberLazyListState()
    // Task 2 contract: re-tapping the active Home tab publishes "home";
    // scroll the feed back to the first row when the event is ours.
    LaunchedEffect(MainTab.Home.route) {
        ScrollToTop.events.collect { route ->
            if (route == MainTab.Home.route && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (content.flags.isNotEmpty()) {
            Box(
                modifier = Modifier.padding(
                    start = AppDimens.ScreenHPadding,
                    top = AppDimens.GapForm,
                    end = AppDimens.ScreenHPadding,
                    bottom = AppDimens.GapForm,
                ),
            ) {
                SafetyBanner(content.flags)
            }
        }
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.testTag("home_tabs"),
        ) {
            HomeTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    },
                    modifier = Modifier.testTag(tab.testTag),
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val cards = homeCardsForTab(content.cards, selectedTab)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "greeting") {
                    // Greeting is the first feed item (Task 1 moved it out
                    // of the old inline header row).
                    Text(
                        text = greetingText(content.greeting),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = CaregiverColors.Ink,
                        modifier = Modifier.padding(
                            start = AppDimens.ScreenHPadding,
                            top = AppDimens.GapForm,
                            end = AppDimens.ScreenHPadding,
                        ),
                    )
                }
                item(key = "stats") {
                    HomeStatsRow(
                        done = content.done,
                        total = content.total,
                        attention = content.cards.count(::needsAttention),
                    )
                }
                when {
                    content.cards.isEmpty() -> item(key = "empty-people") {
                        EmptyItem {
                            AppEmptyState(
                                icon = Icons.Filled.People,
                                title = stringResource(R.string.people_empty),
                                subtitle = stringResource(R.string.home_section_today),
                                actionLabel = stringResource(R.string.people_add),
                                onAction = { navController.navigate("people") },
                            )
                        }
                    }
                    cards.isEmpty() -> item(key = "empty-tab") {
                        EmptyItem {
                            TabEmptyState(selectedTab) { selectedTab = HomeTab.All }
                        }
                    }
                    else -> items(cards, key = { it.recipientId }) { card ->
                        RecipientRow(card) {
                            navController.navigate(AppRoutes.recipientDetail(card.recipientId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyItem(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(
            start = AppDimens.ScreenHPadding,
            top = AppDimens.SectionGap,
            end = AppDimens.ScreenHPadding,
            bottom = AppDimens.SectionGap,
        ),
    ) {
        content()
    }
}

/** Per-tab empty state with a client-side jump to the All tab. */
@Composable
private fun TabEmptyState(tab: HomeTab, onViewAll: () -> Unit) {
    if (tab == HomeTab.Today) {
        AppEmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.home_empty_today_title),
            subtitle = stringResource(R.string.home_empty_today_body),
            actionLabel = stringResource(R.string.home_empty_view_all),
            onAction = onViewAll,
        )
    } else {
        AppEmptyState(
            icon = Icons.Filled.Check,
            title = stringResource(R.string.home_empty_attention_title),
            subtitle = stringResource(R.string.home_empty_attention_body),
            actionLabel = stringResource(R.string.home_empty_view_all),
            onAction = onViewAll,
        )
    }
}

@Composable
private fun HomeStatsRow(done: Int, total: Int, attention: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.ScreenHPadding,
                top = AppDimens.GapCard,
                end = AppDimens.ScreenHPadding,
                bottom = AppDimens.GapCard,
            ),
    ) {
        AppCard(modifier = Modifier.weight(1f), padding = AppDimens.GapCard) {
            Text(
                stringResource(R.string.home_notes_today),
                fontFamily = PlexArabic,
                fontSize = 13.sp,
                color = CaregiverColors.Muted,
            )
            Text(
                stringResource(R.string.home_today_progress, done, total),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CaregiverColors.Ink,
            )
        }
        AppCard(modifier = Modifier.weight(1f), padding = AppDimens.GapCard) {
            Text(
                stringResource(R.string.home_needs_attention),
                fontFamily = PlexArabic,
                fontSize = 13.sp,
                color = CaregiverColors.Muted,
            )
            Text(
                "$attention",
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CaregiverColors.Ink,
            )
        }
    }
}

/**
 * One care recipient as a timeline row (Task 5): the chips and last-note
 * summary come from the dashboard's single bounded load.
 */
@Composable
private fun RecipientRow(card: HomeCard, onOpen: () -> Unit) {
    val arabic = Locale.getDefault().language == "ar"
    NoteRow(
        avatarLetter = card.name.take(1),
        name = card.name,
        // TODO(api-gap): the API carries only a day-precision note date, no
        // timestamp, so the row's time slot stays empty — the client must
        // not invent one. A note timestamp field would close this gap.
        time = "",
        body = card.lastNote?.let { note ->
            stringResource(R.string.person_last_note) + ": " +
                OptionLabels.label(OptionGroup.Appetite, note.appetite, arabic) + " · " +
                OptionLabels.label(OptionGroup.Sleep, note.sleep, arabic)
        } ?: "",
        onClick = onOpen,
        pills = { card.chips.forEach { ChipView(it) } },
        actions = {
            if (card.chips.contains(RecipientChip.MissedToday) ||
                card.chips.contains(RecipientChip.NoNote)
            ) {
                AddNoteAction(onClick = onOpen)
            }
        },
    )
}

/** Compact X-style action row entry for recipients missing today's note. */
@Composable
private fun AddNoteAction(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag("home_add_note"),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = CaregiverColors.Primary,
            modifier = Modifier.size(AppDimens.IconSm),
        )
        Spacer(Modifier.width(AppDimens.GapTight))
        Text(
            stringResource(R.string.detail_add_note),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.Primary,
        )
    }
}

/**
 * Fall-only banner, labeled as such: it covers reported falls from the
 * dashboard window, not every safety flag. Non-dismissible by construction —
 * no close action exists anywhere in this tree.
 */
@Composable
private fun SafetyBanner(flags: List<SafetyFlag>) {
    val first = flags.firstOrNull()
    SafetyAlertCard(
        title = stringResource(R.string.home_safety_title),
        body = if (first != null) {
            stringResource(R.string.home_safety_fall, Bidi.isolate(first.recipientName))
        } else {
            ""
        },
    )
}

@Composable
private fun ChipView(chip: RecipientChip) {
    val label = when (chip) {
        RecipientChip.DoneToday -> stringResource(R.string.chip_done)
        RecipientChip.MissedToday -> stringResource(R.string.chip_missed)
        RecipientChip.HighPain -> stringResource(R.string.chip_high_pain)
        RecipientChip.FallFlag -> stringResource(R.string.chip_fall)
        RecipientChip.NoNote -> stringResource(R.string.chip_no_note)
    }
    when (chip) {
        RecipientChip.DoneToday -> GreenPill(label)
        RecipientChip.HighPain, RecipientChip.FallFlag -> RedPill(label)
        RecipientChip.NoNote, RecipientChip.MissedToday -> GrayPill(label)
    }
}

@Composable
private fun greetingText(greeting: Greeting): String {
    val name = greeting.name?.let { Bidi.isolate(it) }
    return if (greeting.morning) {
        if (name == null) {
            stringResource(R.string.home_greeting_morning_plain)
        } else {
            stringResource(R.string.home_greeting_morning, name)
        }
    } else {
        if (name == null) {
            stringResource(R.string.home_greeting_evening_plain)
        } else {
            stringResource(R.string.home_greeting_evening, name)
        }
    }
}

@Composable
fun LoadingRow() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLoadingSkeleton()
    }
}

@Composable
fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppErrorState(
            message = stringResource(R.string.common_loading_failed),
            onRetry = onRetry,
        )
    }
}
