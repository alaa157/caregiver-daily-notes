package com.caregiver.mobile.presentation.recipients

import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.ForwardChevron
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SmartToastHost
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.common.rememberSmartToastState
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
 * person cards (avatar 48 + name), bottom nav الأشخاص. The Active/Archived
 * filter, archive actions and delete confirmation are local-only additions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsScreen(
    graph: AppGraph,
    navController: NavController,
    admin: RecipientAdmin? = graph.recipientAdmin,
) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth, admin = admin)
    }
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val busyIds by vm.busyIds.collectAsStateWithLifecycle()
    val pendingUndoId by vm.pendingUndoId.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val deleteCounts by vm.deleteCounts.collectAsStateWithLifecycle()

    RecipientsContent(
        state = state,
        filter = filter,
        busyIds = busyIds,
        pendingUndoId = pendingUndoId,
        error = error,
        deleteCounts = deleteCounts,
        hasAdmin = admin != null,
        onRefresh = vm::refresh,
        onFilter = vm::setFilter,
        onOpen = { navController.navigate(AppRoutes.recipientDetail(it)) },
        onAdd = { navController.navigate(AppDestinations.AddRecipient.base) },
        onArchive = vm::archive,
        onUnarchive = vm::unarchive,
        onRequestDelete = vm::counts,
        onConfirmDelete = vm::delete,
        onCancelDelete = vm::clearCounts,
        onUndo = vm::undoArchive,
        onConsumeUndo = vm::consumeUndo,
        onErrorShown = vm::consumeError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipientsContent(
    state: PeopleState,
    filter: PeopleFilter,
    busyIds: Set<String>,
    pendingUndoId: String?,
    error: PeopleActionError?,
    deleteCounts: DeleteCountsState?,
    hasAdmin: Boolean,
    onRefresh: () -> Unit,
    onFilter: (PeopleFilter) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onUndo: (String) -> Unit,
    onConsumeUndo: (String?) -> Unit,
    onErrorShown: () -> Unit,
) {
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val snackbar = rememberSmartToastState()

    // Task 2 contract: re-tapping the active People tab publishes "people";
    // scroll the list back to the first person when the event is ours.
    LaunchedEffect(MainTab.People.route) {
        ScrollToTop.events.collect { route ->
            if (route == MainTab.People.route && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val undoMessage = stringResource(R.string.people_archived_message)
    val undoLabel = stringResource(R.string.people_archive_undo)
    LaunchedEffect(pendingUndoId) {
        val id = pendingUndoId ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = undoMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo(id)
        onConsumeUndo(id)
    }

    val errorMessage = when (error) {
        PeopleActionError.ArchiveFailed -> stringResource(R.string.people_archive_failed)
        PeopleActionError.UnarchiveFailed -> stringResource(R.string.people_unarchive_failed)
        PeopleActionError.DeleteFailed -> stringResource(R.string.people_delete_failed)
        null -> null
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    val refreshing = (state as? PeopleState.Content)?.refreshing ?: false
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = refreshing,
            onRefresh = onRefresh,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == PeopleFilter.Active,
                        onClick = { onFilter(PeopleFilter.Active) },
                        label = { Text(stringResource(R.string.people_filter_active)) },
                        modifier = Modifier.testTag("people_filter_active"),
                    )
                    FilterChip(
                        selected = filter == PeopleFilter.Archived,
                        onClick = { onFilter(PeopleFilter.Archived) },
                        label = { Text(stringResource(R.string.people_filter_archived)) },
                        modifier = Modifier.testTag("people_filter_archived"),
                    )
                }
                when (val s = state) {
                    PeopleState.Loading -> LoadingRow()
                    PeopleState.Error -> LoadFailed(onRetry = onRefresh)
                    is PeopleState.Content -> {
                        if (s.recipients.isEmpty()) {
                            AppEmptyState(
                                icon = Icons.Filled.PersonAdd,
                                title = if (filter == PeopleFilter.Archived) {
                                    stringResource(R.string.people_empty_archived)
                                } else {
                                    stringResource(R.string.people_empty)
                                },
                                subtitle = if (filter == PeopleFilter.Archived) {
                                    stringResource(R.string.people_archived_readonly)
                                } else {
                                    stringResource(R.string.tab_people)
                                },
                                actionLabel = stringResource(R.string.people_add),
                                onAction = onAdd,
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 96.dp),
                            ) {
                                items(s.recipients, key = { it.id }) { recipient ->
                                    PersonRow(
                                        recipient = recipient,
                                        busy = recipient.id in busyIds,
                                        hasAdmin = hasAdmin,
                                        onOpen = { onOpen(recipient.id) },
                                        onArchive = { onArchive(recipient.id) },
                                        onUnarchive = { onUnarchive(recipient.id) },
                                        onDelete = { onRequestDelete(recipient.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SmartToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val deleteTarget = deleteCounts?.counts?.let { counts ->
        (state as? PeopleState.Content)?.recipients?.firstOrNull { it.id == deleteCounts.recipientId }
            ?.let { it to counts }
    }
    deleteTarget?.let { (recipient, counts) ->
        DeleteRecipientDialog(
            name = recipient.name,
            counts = counts,
            onConfirm = { onConfirmDelete(recipient.id) },
            onDismiss = onCancelDelete,
        )
    }
}

@Composable
private fun PersonRow(
    recipient: RecipientDto,
    busy: Boolean,
    hasAdmin: Boolean,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    // The list response carries only id/name/active/archivedAt — no per-person
    // note data — so no "last note" caption is shown here. Note state is
    // visible on the detail screen, which loads the recipient's history.
    AppCard(modifier = Modifier.clickable(enabled = !busy, onClick = onOpen)) {
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
            if (recipient.archivedAt != null) {
                GrayPill(stringResource(R.string.people_archived))
            }
            if (hasAdmin) {
                PersonOverflowMenu(
                    recipient = recipient,
                    busy = busy,
                    onArchive = onArchive,
                    onUnarchive = onUnarchive,
                    onDelete = onDelete,
                )
            }
            ForwardChevron()
        }
    }
}

@Composable
private fun PersonOverflowMenu(
    recipient: RecipientDto,
    busy: Boolean,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            enabled = !busy,
            modifier = Modifier.testTag("people_menu_${recipient.id}"),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.people_actions),
                tint = CaregiverColors.Muted,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (recipient.archivedAt != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.people_unarchive)) },
                    onClick = {
                        open = false
                        onUnarchive()
                    },
                    modifier = Modifier.testTag("people_unarchive_${recipient.id}"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.people_delete)) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("people_delete_${recipient.id}"),
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.people_archive)) },
                    onClick = {
                        open = false
                        onArchive()
                    },
                    modifier = Modifier.testTag("people_archive_${recipient.id}"),
                )
            }
        }
    }
}

@Composable
internal fun DeleteRecipientDialog(
    name: String,
    counts: RecipientDeletionCounts,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_delete_title, Bidi.isolate(name))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.people_delete_body,
                        counts.notes,
                        counts.addenda,
                        counts.plans,
                        counts.medications,
                        counts.intakes,
                        counts.outbox,
                    ),
                )
                Text(
                    stringResource(R.string.people_delete_backup_caveat),
                    color = CaregiverColors.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("people_delete_confirm"),
            ) {
                Text(stringResource(R.string.people_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** Add-person form (missing-page spec): name only, inline required error. */
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val addState by vm.addState.collectAsStateWithLifecycle()
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
 * Archived recipients hide write actions and offer Unarchive/Delete instead.
 */
@Composable
fun RecipientDetailScreen(
    recipientId: String,
    graph: AppGraph,
    navController: NavController,
    admin: RecipientAdmin? = graph.recipientAdmin,
) {
    val vm: RecipientDetailViewModel = assistedViewModel("detail-$recipientId") {
        RecipientDetailViewModel(recipientId, graph.apis, graph.auth, admin = admin)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val deleteCounts by vm.deleteCounts.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) {
        if (deleted) {
            vm.consumeDeleted()
            navController.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding)) {
        if (state !is DetailState.Content) {
            AppTopBar(
                title = stringResource(R.string.tab_people),
                onBack = { navController.popBackStack() },
            )
        }
        when (val s = state) {
            DetailState.Loading -> LoadingRow()
            DetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is DetailState.Content -> DetailContent(
                detail = s.detail,
                busy = busy,
                error = error,
                hasAdmin = admin != null,
                deleteCounts = deleteCounts?.counts,
                onBack = { navController.popBackStack() },
                onAddNote = { navController.navigate(AppRoutes.noteEditor(s.detail.id)) },
                onHistory = { navController.navigate(AppRoutes.tab(MainTab.History)) },
                onSummary = { navController.navigate(AppRoutes.summary(s.detail.id)) },
                onPlan = { navController.navigate(AppDestinations.Plans.base) },
                onArchive = vm::archive,
                onUnarchive = vm::unarchive,
                onRequestDelete = vm::counts,
                onConfirmDelete = vm::delete,
                onCancelDelete = vm::clearCounts,
            )
        }
    }
}

@Composable
internal fun DetailContent(
    detail: RecipientDetail,
    busy: Boolean,
    error: DetailActionError?,
    hasAdmin: Boolean,
    deleteCounts: RecipientDeletionCounts?,
    onBack: () -> Unit,
    onAddNote: () -> Unit,
    onHistory: () -> Unit,
    onSummary: () -> Unit,
    onPlan: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val arabic = Locale.getDefault().language == "ar"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        AppTopBar(
            title = Bidi.isolate(detail.name),
            onBack = onBack,
        )
        // Status card: pill + primary add-note 52dp. Archived records are
        // read-only, so the write action is absent instead of disabled.
        AppCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detail.archived) {
                    GrayPill(stringResource(R.string.people_archived))
                }
                if (detail.lastNote == null) {
                    GrayPill(stringResource(R.string.chip_no_note))
                } else {
                    GreenPill(stringResource(R.string.chip_done))
                }
            }
            if (detail.archived) {
                Text(
                    text = stringResource(R.string.people_archived_readonly),
                    fontFamily = PlexArabic,
                    fontSize = 13.sp,
                    color = CaregiverColors.Muted,
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.detail_add_note),
                    onClick = onAddNote,
                    enabled = !busy,
                    height = 52.dp,
                )
            }
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
        // Archived recipients keep read surfaces but lose every write path.
        ActionButton(R.string.detail_history, onHistory)
        ActionButton(R.string.detail_summary, onSummary)
        if (!detail.archived) {
            ActionButton(R.string.detail_plan, onPlan)
        }
        error?.let {
            Text(
                text = stringResource(
                    when (it) {
                        DetailActionError.ArchiveFailed -> R.string.people_archive_failed
                        DetailActionError.UnarchiveFailed -> R.string.people_unarchive_failed
                        DetailActionError.DeleteFailed -> R.string.people_delete_failed
                        DetailActionError.DeleteUnavailable -> R.string.people_delete_unavailable
                    },
                ),
                fontFamily = PlexArabic,
                color = CaregiverColors.Danger,
                fontSize = 13.sp,
            )
        }
        if (hasAdmin) {
            if (detail.archived) {
                SecondaryButton(
                    label = stringResource(R.string.people_unarchive),
                    onClick = onUnarchive,
                    enabled = !busy,
                    modifier = Modifier.testTag("detail_unarchive"),
                )
                SecondaryButton(
                    label = stringResource(R.string.people_delete),
                    onClick = {
                        confirmingDelete = true
                        onRequestDelete()
                    },
                    enabled = !busy,
                    danger = true,
                    modifier = Modifier.testTag("detail_delete"),
                )
            } else {
                SecondaryButton(
                    label = stringResource(R.string.people_archive),
                    onClick = onArchive,
                    enabled = !busy,
                    modifier = Modifier.testTag("detail_archive"),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val counts = deleteCounts
    if (confirmingDelete && counts != null) {
        DeleteRecipientDialog(
            name = detail.name,
            counts = counts,
            onConfirm = {
                confirmingDelete = false
                onConfirmDelete()
            },
            onDismiss = {
                confirmingDelete = false
                onCancelDelete()
            },
        )
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
