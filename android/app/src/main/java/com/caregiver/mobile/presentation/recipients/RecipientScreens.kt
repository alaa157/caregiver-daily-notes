package com.caregiver.mobile.presentation.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.ForwardChevron
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.OptionLabels
import java.util.Locale

/**
 * Recipients list (board 3) with the add-person entry point. The list shares
 * one activity-scoped [RecipientsViewModel] with the add form below, so a
 * creation refreshes exactly the list the back stack returns to; entry
 * refreshes cover sign-out/sign-in turnover on the same activity.
 *
 * HTML: h1 22sp + subtitle 13sp, full-width secondary add button,
 * person cards (avatar 48 + name), bottom nav الأشخاص. No per-person
 * note captions: the list payload has no note data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.state.collectAsState()

    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    // Task 2 contract: re-tapping the active People tab publishes "people";
    // scroll the list back to the first person when the event is ours.
    LaunchedEffect(MainTab.People.route) {
        ScrollToTop.events.collect { route ->
            if (route == MainTab.People.route && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }
    // Pull-to-refresh (Metrolist idiom): bare M3 primitives around the list,
    // stock Indicator re-anchored to the content top. The VM keeps loaded
    // rows composed under Content.refreshing while the reload runs.
    val refreshing = (state as? PeopleState.Content)?.refreshing ?: false
    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = refreshing,
        onRefresh = vm::refresh,
        indicator = {
            Indicator(
                isRefreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            val count = (state as? PeopleState.Content)?.recipients?.size
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.tab_people),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = CaregiverColors.Ink,
                )
                if (count != null) {
                    Text(
                        text = if (count == 1) "$count person" else "$count people",
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                }
            }
            SecondaryButton(
                label = stringResource(R.string.add_title),
                onClick = { navController.navigate(AppDestinations.AddRecipient.base) },
            )
            when (val s = state) {
                PeopleState.Loading -> LoadingRow()
                PeopleState.Error -> LoadFailed(onRetry = vm::refresh)
                is PeopleState.Content -> {
                    if (s.recipients.isEmpty()) {
                        AppEmptyState(
                            icon = Icons.Filled.PersonAdd,
                            title = stringResource(R.string.people_empty),
                            subtitle = stringResource(R.string.tab_people),
                            actionLabel = stringResource(R.string.people_add),
                            onAction = { navController.navigate(AppDestinations.AddRecipient.base) },
                        )
                    } else {
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(s.recipients, key = { it.id }) { recipient ->
                                PersonRow(recipient) {
                                    navController.navigate(AppRoutes.recipientDetail(recipient.id))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(recipient: RecipientDto, onOpen: () -> Unit) {
    // The list response carries only id/name/active — no per-person note
    // data — so no "last note" caption is shown here. Note state is visible
    // on the detail screen, which loads the recipient's history.
    AppCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Bidi.isolate(recipient.name).take(1))
            Text(
                text = Bidi.isolate(recipient.name),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CaregiverColors.Ink,
                modifier = Modifier.weight(1f),
            )
            ForwardChevron()
        }
    }
}

/** Add-person form (missing-page spec): name only, inline required error. */
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val addState by vm.addState.collectAsState()
    addState.addedId?.let {
        LaunchedEffect(it) {
            vm.consumeAdded()
            navController.popBackStack()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = AppDimens.ScreenHPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        AppTopBar(
            title = stringResource(R.string.add_title),
            onBack = { navController.popBackStack() },
        )
        Text(
            stringResource(R.string.add_name),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.Ink,
        )
        OutlinedTextField(
            value = addState.name,
            onValueChange = vm::onName,
            isError = addState.nameError != null,
            supportingText = {
                if (addState.nameError != null) {
                    Text(
                        stringResource(R.string.add_error_name),
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Danger,
                    )
                }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { vm.add() },
            ),
            shape = RoundedCornerShape(AppDimens.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.InputHeight).testTag("add_person_name"),
        )
        PrimaryButton(
            label = stringResource(R.string.add_save),
            onClick = vm::add,
            enabled = !addState.busy,
            modifier = Modifier.testTag("add_person_save"),
        )
    }
}

/**
 * Recipient detail (board 4): status card + latest note + 3 design actions.
 * HTML: back + name, status card (pill + primary 52dp), latest-note section
 * when history returned one, 3 secondary space-between buttons. Plan and
 * summary status is intentionally not shown: it is unverified on this
 * screen (no plan/summary payload is loaded here).
 */
@Composable
fun RecipientDetailScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: RecipientDetailViewModel = assistedViewModel("detail-$recipientId") {
        RecipientDetailViewModel(recipientId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding)) {
        when (val s = state) {
            DetailState.Loading -> LoadingRow()
            DetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is DetailState.Content -> DetailContent(s.detail, navController)
        }
    }
}

@Composable
private fun DetailContent(detail: RecipientDetail, navController: NavController) {
    val arabic = Locale.getDefault().language == "ar"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        AppTopBar(
            title = Bidi.isolate(detail.name),
            onBack = { navController.popBackStack() },
        )
        // Status card: pill + primary add-note 52dp.
        AppCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detail.lastNote == null) {
                    GrayPill(stringResource(R.string.chip_no_note))
                } else {
                    GreenPill(stringResource(R.string.chip_done))
                }
            }
            PrimaryButton(
                label = stringResource(R.string.detail_add_note),
                onClick = { navController.navigate(AppRoutes.noteEditor(detail.id)) },
                height = 52.dp,
            )
        }
        // Latest note section — only when history actually returned one.
        // The status card above already covers the no-notes empty state.
        detail.lastNote?.let { note ->
            SectionTitle(stringResource(R.string.person_last_note))
            Text(
                text = OptionLabels.label(OptionGroup.Mood, note.mood, arabic) + " · " +
                    OptionLabels.label(OptionGroup.Appetite, note.appetite, arabic) + " · " +
                    OptionLabels.label(OptionGroup.Mobility, note.mobility, arabic),
                fontFamily = PlexArabic,
                fontSize = 15.sp,
                color = CaregiverColors.Ink,
            )
        }
        // No plan/summary status cards: this screen loads only the recipient
        // and their note history, so plan acceptance and summary existence
        // are unverified here. The actions below navigate to the screens
        // that load that real data.
        // 3 design actions: history, summary, plan — secondary space-between.
        ActionButton(R.string.detail_history) {
            navController.navigate(AppRoutes.tab(MainTab.History))
        }
        ActionButton(R.string.detail_summary) {
            navController.navigate(AppRoutes.summary(detail.id))
        }
        ActionButton(R.string.detail_plan) {
            navController.navigate(AppDestinations.Plans.base)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(AppDimens.ControlRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, CaregiverColors.Border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = CaregiverColors.Surface,
            contentColor = CaregiverColors.Ink,
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(label),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ForwardChevron()
        }
    }
}
