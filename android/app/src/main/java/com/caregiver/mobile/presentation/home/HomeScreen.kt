package com.caregiver.mobile.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.navigation.ScrollToTop
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.util.appLocale
import com.caregiver.mobile.data.recipient.RecipientNameRules
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.NoteRowSkeleton
import com.caregiver.mobile.presentation.common.PersonAvatar
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SectionCard
import com.caregiver.mobile.presentation.common.StatusChip
import com.caregiver.mobile.presentation.common.StatusChipVariant
import com.caregiver.mobile.presentation.common.assistedViewModel
import java.time.LocalDate

/**
 * Home timeline: greeting, compact stats, then one redesigned person card
 * (#897) per care recipient, filtered by the Today / Needs attention / All
 * sub-tabs.
 *
 * The fall-safety banner is pinned OUTSIDE the feed (Rule 6): it sits under
 * the shared top bar and above the sub-tabs, so scrolling can never hide it.
 * Filtering is pure client-side over the dashboard's single bounded load —
 * one row per recipient, so no pagination and no per-tab request.
 */
@Composable
fun HomeScreen(graph: AppGraph, navController: NavController) {
    val vm: HomeViewModel = assistedViewModel("home") {
        HomeViewModel(
            graph.auth,
            graph.settings,
            activeDisplayName = graph.activeAccountDisplayName,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    // A swap does not recreate the "home" ViewModel, so follow the stored name
    // here as well: the greeting must change immediately on registration and
    // account switches, not only on the next reload (#706).
    val storedName by graph.activeAccountDisplayName.collectAsState(initial = null)
    // Medication card over local tables; null (and hidden) in graphs with no
    // local database, the same self-disabling the backup service uses.
    val medicationsVm = graph.medications?.let { store ->
        assistedViewModel("medications") { MedicationViewModel(store) }
    }
    when (val s = state) {
        HomeState.Loading, HomeState.Idle -> HomeLoadingRows()
        is HomeState.Error -> HomeErrorState(
            flags = s.lastContent?.flags.orEmpty(),
            onRetry = vm::refresh,
        )
        is HomeState.Content -> HomeTimeline(
            content = s.content.copy(
                greeting = s.content.greeting.copy(
                    // The account's display name is the only name the header
                    // uses; with no account the greeting reads "Welcome"
                    // instead of a name derived from a saved email (#895).
                    name = storedName?.takeIf { it.isNotBlank() },
                ),
            ),
            refreshing = s.refreshing,
            onRefresh = vm::refresh,
            navController = navController,
            medications = medicationsVm,
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
    medications: MedicationViewModel?,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Today) }
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
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
            state = pullRefreshState,
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            // Metrolist keeps the stock M3 spinner, just re-anchored to the
            // content top (their player-aware inset padding has no
            // mini-player counterpart here, so no padding is needed).
            indicator = {
                Indicator(
                    isRefreshing = refreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val cards = homeCardsForTab(content.cards, selectedTab)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // The shell's FAB floats over the last row otherwise.
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item(key = "greeting") {
                    // Home header (#895): the date in small muted text above
                    // one heading. The heading carries a semantics heading
                    // role so screen readers can navigate straight to it.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.GapTight),
                        modifier = Modifier.padding(
                            start = AppDimens.ScreenHPadding,
                            top = AppDimens.GapForm,
                            end = AppDimens.ScreenHPadding,
                        ),
                    ) {
                        Text(
                            text = DateFormats.dayMonth(content.greeting.date, appLocale()),
                            fontFamily = PlexArabic,
                            fontSize = 13.sp,
                            color = CaregiverColors.Muted,
                        )
                        Text(
                            text = greetingText(content.greeting),
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            color = CaregiverColors.Ink,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
                item(key = "stats") {
                    HomeStatsRow(
                        done = content.done,
                        total = content.total,
                        attention = content.cards.count(::needsAttention),
                    )
                }
                item(key = "medications") {
                    // Medication tracker (standalone local tables): hidden
                    // when the graph has no local database.
                    if (medications != null) {
                        val medState by medications.state.collectAsStateWithLifecycle()
                        val medError by medications.error.collectAsStateWithLifecycle()
                        MedicationsCard(
                            state = medState,
                            people = content.cards.map { MedicationsPerson(it.recipientId, it.name) },
                            onSetTaken = medications::setTaken,
                            onAdd = medications::add,
                            onUpdate = medications::update,
                            onDelete = medications::delete,
                            modifier = Modifier.padding(horizontal = AppDimens.ScreenHPadding),
                            error = medError,
                            onErrorShown = medications::clearError,
                        )
                    }
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
                    else -> {
                        // #897: one card per person, people without today's
                        // note first, then a dashed "Add person" row.
                        val ordered = peopleWithoutTodayNoteFirst(cards)
                        itemsIndexed(ordered, key = { _, card -> card.recipientId }) { _, card ->
                            PersonCard(
                                card = card,
                                onOpen = {
                                    navController.navigate(AppRoutes.recipientDetail(card.recipientId))
                                },
                                modifier = Modifier.padding(horizontal = AppDimens.ScreenHPadding),
                            )
                        }
                        item(key = "add-person") {
                            AddPersonRow(
                                onClick = { navController.navigate(AppDestinations.AddRecipient.base) },
                                modifier = Modifier.padding(horizontal = AppDimens.ScreenHPadding),
                            )
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
 * One care recipient as a redesigned card (#897): avatar, name, a muted
 * day-precision last-note line and a "Done"/"Not yet" status chip for today.
 * The whole card is the tap target that opens Recipient Detail. Values come
 * from the dashboard's single bounded load — no extra request per row.
 *
 * No wall-clock time is shown: note storage carries only a day-precision
 * date, so the prototype's "9:40" would be invented data.
 */
@Composable
private fun PersonCard(
    card: HomeCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneToday = RecipientChip.DoneToday in card.chips
    // The tag/click live on an outer Box: SectionCard sets its own test tag
    // on its Surface, which would otherwise overwrite ours.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpen)
            .testTag("home_person_card"),
    ) {
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
            ) {
                PersonAvatar(initial = RecipientNameRules.initial(card.name))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Bidi.isolate(card.name),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CaregiverColors.Ink,
                    )
                    val noteLabel = lastNoteLabel(card)
                    if (noteLabel.isNotBlank()) {
                        Text(
                            text = noteLabel,
                            fontFamily = PlexArabic,
                            fontSize = 13.sp,
                            color = CaregiverColors.Muted,
                        )
                    }
                }
                StatusChip(
                    label = stringResource(
                        if (doneToday) R.string.redesign_chip_done else R.string.redesign_chip_not_yet,
                    ),
                    variant = if (doneToday) StatusChipVariant.Done else StatusChipVariant.NotYet,
                )
            }
        }
    }
}

/** Muted "Last note: today / yesterday / <date>" line for one person. */
@Composable
private fun lastNoteLabel(card: HomeCard): String {
    val note = card.lastNote ?: return ""
    return when (lastNoteWhen(note.date, LocalDate.now())) {
        LastNoteWhen.Today -> stringResource(R.string.home_last_note_today)
        LastNoteWhen.Yesterday -> stringResource(R.string.home_last_note_yesterday)
        LastNoteWhen.Older -> stringResource(
            R.string.person_last_note_date,
            Bidi.isolate(
                DateFormats.historyDay(note.date, appLocale().language == "ar"),
            ),
        )
        null -> stringResource(R.string.person_last_note_date, note.date)
    }
}

/** Dashed "Add person" row that ends the people list and opens Add Recipient. */
@Composable
private fun AddPersonRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dashedBorder(1.dp, CaregiverColors.AuthFieldBorder, AppDimens.ControlRadius)
            .clickable(onClick = onClick)
            .testTag("home_add_person"),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = CaregiverColors.Primary,
            modifier = Modifier.size(AppDimens.IconSm),
        )
        Spacer(Modifier.width(AppDimens.GapTight))
        Text(
            text = stringResource(R.string.people_add),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.Primary,
        )
    }
}

/** A rounded dashed outline. Compose has no dashed BorderStroke, so draw it. */
private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    radius: Dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
    )
    drawRoundRect(
        color = color,
        size = size,
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke,
    )
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
private fun greetingText(greeting: Greeting): String {
    // No account display name means a plain, localized "Welcome" — not a
    // name guessed from an email and not a time-of-day phrase without a name.
    val name = greeting.name?.let { Bidi.isolate(it) }
        ?: return stringResource(R.string.home_greeting_welcome)
    return when (greeting.period) {
        GreetingPeriod.MORNING -> stringResource(R.string.home_greeting_morning, name)
        GreetingPeriod.AFTERNOON -> stringResource(R.string.home_greeting_afternoon, name)
        GreetingPeriod.EVENING -> stringResource(R.string.home_greeting_evening, name)
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
