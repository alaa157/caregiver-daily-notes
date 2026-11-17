package com.caregiver.mobile.presentation.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import com.caregiver.mobile.data.recipient.CareDetails
import com.caregiver.mobile.data.recipient.CareDetailsField
import com.caregiver.mobile.data.recipient.CareDetailsIssue
import com.caregiver.mobile.data.recipient.CareDetailsRules
import com.caregiver.mobile.data.recipient.RecipientNameRules
import com.caregiver.mobile.data.recipient.RelationshipOptions
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.ForwardChevron
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.PersonAvatar
import com.caregiver.mobile.presentation.common.PersonAvatarSize
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.StatusChip
import com.caregiver.mobile.presentation.common.StatusChipVariant
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SmartToastHost
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.common.rememberSmartToastState
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.optionLabel

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
                            text = pluralStringResource(R.plurals.people_count, count, count),
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
                    // Archive state only exists when the graph has a local
                    // admin; a server-backed graph would show a permanently
                    // empty tab, so the filter is hidden with the actions.
                    if (hasAdmin) {
                        FilterChip(
                            selected = filter == PeopleFilter.Archived,
                            onClick = { onFilter(PeopleFilter.Archived) },
                            label = { Text(stringResource(R.string.people_filter_archived)) },
                            modifier = Modifier.testTag("people_filter_archived"),
                        )
                    }
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
                                    stringResource(R.string.people_empty_subtitle)
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
    AppCard(
        modifier = Modifier.clickable(
            enabled = !busy,
            role = Role.Button,
            onClick = onOpen,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(RecipientNameRules.initial(recipient.name))
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
                    stringResource(R.string.detail_delete_summaries, counts.summaries),
                    color = CaregiverColors.Muted,
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

/** Add-person form (missing-page spec): name and optional relationship. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth, admin = graph.recipientAdmin)
    }
    val addState by vm.addState.collectAsStateWithLifecycle()
    addState.addedId?.let {
        LaunchedEffect(it) {
            val id = it
            vm.consumeAdded()
            navController.navigate(AppRoutes.recipientDetail(id)) {
                popUpTo(AppDestinations.AddRecipient.base) { inclusive = true }
            }
        }
    }
    val nameErrorRes = when (addState.nameError) {
        NameError.Required -> R.string.add_error_name
        NameError.TooLong -> R.string.add_error_name_long
        null -> null
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
                nameErrorRes?.let { errorRes ->
                    Text(
                        stringResource(errorRes),
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Danger,
                    )
                }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
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
        Text(
            stringResource(R.string.relationship_field),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.Ink,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            RelationshipOptions.codes.forEach { code ->
                FilterChip(
                    selected = addState.relationship == code,
                    onClick = { vm.setRelationship(code) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    label = {
                        Text(stringResource(requireNotNull(relationshipLabelRes(code))))
                    },
                )
            }
        }
        if (addState.relationship == "OTHER") {
            OutlinedTextField(
                value = addState.relationshipOther,
                onValueChange = vm::setRelationshipOther,
                placeholder = { Text(stringResource(R.string.relationship_other_placeholder)) },
                isError = addState.relationshipError,
                supportingText = {
                    if (addState.relationshipError) {
                        Text(
                            stringResource(R.string.relationship_error_long),
                            fontFamily = PlexArabic,
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(AppDimens.ControlRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.InputHeight),
            )
        }
        PrimaryButton(
            label = stringResource(R.string.add_save),
            onClick = vm::add,
            enabled = !addState.busy,
            modifier = Modifier.testTag("add_person_save"),
        )
        if (addState.failed) {
            Text(
                stringResource(R.string.add_error_save_failed),
                color = CaregiverColors.Danger,
                modifier = Modifier.testTag("add_person_error"),
            )
        }
        addState.duplicate?.let { match ->
            AppCard(modifier = Modifier.testTag("add_person_duplicate")) {
                Text(
                    stringResource(R.string.add_duplicate_title),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    color = CaregiverColors.Ink,
                )
                Text(
                    stringResource(R.string.add_duplicate_body),
                    fontFamily = PlexArabic,
                    color = CaregiverColors.Muted,
                )
                Text(Bidi.isolate(match.name), fontFamily = PlexArabic)
                if (match.archived) {
                    Text(
                        stringResource(R.string.people_archived),
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Muted,
                    )
                }
                SecondaryButton(
                    label = stringResource(R.string.add_duplicate_open),
                    onClick = {
                        navController.navigate(AppRoutes.recipientDetail(match.id)) {
                            popUpTo(AppDestinations.AddRecipient.base) { inclusive = true }
                        }
                    },
                    modifier = Modifier.testTag("add_person_duplicate_open"),
                )
                PrimaryButton(
                    label = stringResource(R.string.add_duplicate_create_anyway),
                    onClick = vm::createAnyway,
                    modifier = Modifier.testTag("add_person_create_anyway"),
                )
                TextButton(onClick = vm::dismissDuplicate) {
                    Text(stringResource(R.string.common_cancel), fontFamily = PlexArabic)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRecipientScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val admin = graph.recipientAdmin
    if (admin == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.ScreenHPadding),
        ) {
            AppTopBar(
                title = stringResource(R.string.edit_title),
                onBack = { navController.popBackStack() },
            )
            Text(
                stringResource(R.string.edit_unavailable),
                color = CaregiverColors.Danger,
            )
        }
        return
    }

    val vm: EditRecipientViewModel = assistedViewModel("edit-person-$recipientId") {
        EditRecipientViewModel(recipientId, graph.apis, graph.auth, admin)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    state.savedId?.let { savedId ->
        LaunchedEffect(savedId) { navController.popBackStack() }
    }
    val nameErrorRes = when (state.nameError) {
        NameError.Required -> R.string.add_error_name
        NameError.TooLong -> R.string.add_error_name_long
        null -> null
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
            title = stringResource(R.string.edit_title),
            onBack = { navController.popBackStack() },
        )
        when {
            state.missing -> {
                Text(
                    stringResource(R.string.edit_unavailable),
                    color = CaregiverColors.Danger,
                )
            }
            !state.loaded -> LoadingRow()
            else -> {
                Text(
                    stringResource(R.string.add_name),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CaregiverColors.Ink,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onName,
                    enabled = !state.archived && !state.busy,
                    isError = state.nameError != null,
                    supportingText = {
                        nameErrorRes?.let { errorRes ->
                            Text(
                                stringResource(errorRes),
                                fontFamily = PlexArabic,
                                color = CaregiverColors.Danger,
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { vm.save() },
                    ),
                    shape = RoundedCornerShape(AppDimens.ControlRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CaregiverColors.Primary,
                        unfocusedBorderColor = CaregiverColors.Border,
                        focusedContainerColor = CaregiverColors.Surface,
                        unfocusedContainerColor = CaregiverColors.Surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.InputHeight)
                        .testTag("edit_person_name"),
                )
                Text(
                    stringResource(R.string.relationship_field),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CaregiverColors.Ink,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    RelationshipOptions.codes.forEach { code ->
                        FilterChip(
                            selected = state.relationship == code,
                            onClick = { vm.setRelationship(code) },
                            modifier = Modifier.semantics { role = Role.RadioButton },
                            label = {
                                Text(stringResource(requireNotNull(relationshipLabelRes(code))))
                            },
                        )
                    }
                }
                if (state.relationship == "OTHER") {
                    OutlinedTextField(
                        value = state.relationshipOther,
                        onValueChange = vm::setRelationshipOther,
                        placeholder = {
                            Text(stringResource(R.string.relationship_other_placeholder))
                        },
                        isError = state.relationshipError,
                        supportingText = {
                            if (state.relationshipError) {
                                Text(
                                    stringResource(R.string.relationship_error_long),
                                    fontFamily = PlexArabic,
                                    color = CaregiverColors.Danger,
                                )
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(AppDimens.ControlRadius),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CaregiverColors.Primary,
                            unfocusedBorderColor = CaregiverColors.Border,
                            focusedContainerColor = CaregiverColors.Surface,
                            unfocusedContainerColor = CaregiverColors.Surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.InputHeight),
                    )
                }
                if (state.archived) {
                    Text(
                        stringResource(R.string.people_archived_readonly),
                        color = CaregiverColors.Muted,
                    )
                } else {
                    PrimaryButton(
                        label = stringResource(R.string.edit_save),
                        onClick = vm::save,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("edit_person_save"),
                    )
                    if (state.failed) {
                        Text(
                            stringResource(R.string.add_error_save_failed),
                            color = CaregiverColors.Danger,
                            modifier = Modifier.testTag("edit_person_error"),
                        )
                    }
                    state.duplicate?.let { match ->
                        AppCard(modifier = Modifier.testTag("edit_person_duplicate")) {
                            Text(
                                stringResource(R.string.add_duplicate_title),
                                fontFamily = PlexArabic,
                                fontWeight = FontWeight.Bold,
                                color = CaregiverColors.Ink,
                            )
                            Text(
                                stringResource(R.string.add_duplicate_body),
                                fontFamily = PlexArabic,
                                color = CaregiverColors.Muted,
                            )
                            Text(Bidi.isolate(match.name), fontFamily = PlexArabic)
                            if (match.archived) {
                                Text(
                                    stringResource(R.string.people_archived),
                                    fontFamily = PlexArabic,
                                    color = CaregiverColors.Muted,
                                )
                            }
                            PrimaryButton(
                                label = stringResource(R.string.edit_duplicate_save_anyway),
                                onClick = vm::saveAnyway,
                                modifier = Modifier.testTag("edit_person_save_anyway"),
                            )
                            TextButton(onClick = vm::dismissDuplicate) {
                                Text(
                                    stringResource(R.string.common_cancel),
                                    fontFamily = PlexArabic,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CareDetailsScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val admin = graph.recipientAdmin
    if (admin == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.ScreenHPadding),
        ) {
            AppTopBar(
                title = stringResource(R.string.care_details_title),
                onBack = { navController.popBackStack() },
            )
            Text(
                stringResource(R.string.edit_unavailable),
                color = CaregiverColors.Danger,
            )
        }
        return
    }

    val vm: CareDetailsViewModel = assistedViewModel("care-details-$recipientId") {
        CareDetailsViewModel(recipientId, graph.apis, graph.auth, admin)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
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
            title = stringResource(R.string.care_details_title),
            onBack = { navController.popBackStack() },
        )
        Text(
            stringResource(R.string.care_details_hint),
            color = CaregiverColors.Muted,
        )
        when {
            state.missing -> {
                Text(
                    stringResource(R.string.edit_unavailable),
                    color = CaregiverColors.Danger,
                )
            }
            !state.loaded -> LoadingRow()
            else -> {
                val fieldsEnabled = !state.archived && !state.busy
                CareDetailsInput(
                    label = stringResource(R.string.care_dob),
                    value = state.dateOfBirth,
                    onValueChange = vm::onDateOfBirth,
                    issue = state.errors[CareDetailsField.DateOfBirth],
                    supportingHint = R.string.care_dob_hint,
                    enabled = fieldsEnabled,
                    forceLtr = true,
                    modifier = Modifier.testTag("care_details_dob"),
                )
                CareDetailsInput(
                    label = stringResource(R.string.care_allergies),
                    value = state.allergies,
                    onValueChange = vm::onAllergies,
                    issue = state.errors[CareDetailsField.Allergies],
                    maxLength = CareDetailsRules.ALLERGIES_MAX_LENGTH,
                    enabled = fieldsEnabled,
                    multiLine = true,
                    modifier = Modifier.testTag("care_details_allergies"),
                )
                CareDetailsInput(
                    label = stringResource(R.string.care_conditions),
                    value = state.conditions,
                    onValueChange = vm::onConditions,
                    issue = state.errors[CareDetailsField.Conditions],
                    maxLength = CareDetailsRules.CONDITIONS_MAX_LENGTH,
                    enabled = fieldsEnabled,
                    multiLine = true,
                    modifier = Modifier.testTag("care_details_conditions"),
                )
                CareDetailsInput(
                    label = stringResource(R.string.care_contact_name),
                    value = state.emergencyContactName,
                    onValueChange = vm::onEmergencyContactName,
                    issue = state.errors[CareDetailsField.EmergencyContactName],
                    maxLength = CareDetailsRules.EMERGENCY_CONTACT_NAME_MAX_LENGTH,
                    enabled = fieldsEnabled,
                    modifier = Modifier.testTag("care_details_contact_name"),
                )
                CareDetailsInput(
                    label = stringResource(R.string.care_contact_phone),
                    value = state.emergencyContactPhone,
                    onValueChange = vm::onEmergencyContactPhone,
                    issue = state.errors[CareDetailsField.EmergencyContactPhone],
                    maxLength = 30,
                    keyboardType = KeyboardType.Phone,
                    enabled = fieldsEnabled,
                    forceLtr = true,
                    modifier = Modifier.testTag("care_details_contact_phone"),
                )
                if (state.archived) {
                    Text(
                        stringResource(R.string.people_archived_readonly),
                        color = CaregiverColors.Muted,
                    )
                } else {
                    PrimaryButton(
                        label = stringResource(R.string.care_save),
                        onClick = vm::save,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("care_details_save"),
                    )
                    if (state.failed) {
                        Text(
                            stringResource(R.string.add_error_save_failed),
                            color = CaregiverColors.Danger,
                            modifier = Modifier.testTag("care_details_error"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CareDetailsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    issue: CareDetailsIssue?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    supportingHint: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiLine: Boolean = false,
    forceLtr: Boolean = false,
) {
    val errorRes = when (issue) {
        CareDetailsIssue.InvalidDate -> R.string.care_error_date
        CareDetailsIssue.InvalidPhone -> R.string.care_error_phone
        CareDetailsIssue.TooLong -> R.string.care_error_long
        null -> null
    }
    val input: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(maxLength?.let(newValue::take) ?: newValue)
            },
            enabled = enabled,
            label = { Text(label) },
            isError = issue != null,
            supportingText = {
                when {
                    errorRes != null -> Text(
                        stringResource(errorRes),
                        color = CaregiverColors.Danger,
                    )
                    supportingHint != null -> Text(stringResource(supportingHint))
                }
            },
            singleLine = !multiLine,
            minLines = if (multiLine) 3 else 1,
            maxLines = if (multiLine) 5 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(AppDimens.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.InputHeight),
        )
    }
    if (forceLtr) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            input()
        }
    } else {
        input()
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.reload()
    }

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
                onCareDetails = {
                    navController.navigate(AppRoutes.careDetails(s.detail.id))
                },
                onEdit = { navController.navigate(AppRoutes.editRecipient(s.detail.id)) },
                onAddNote = { navController.navigate(AppRoutes.noteEditor(s.detail.id)) },
                onViewToday = {
                    s.detail.todayNoteId?.let { navController.navigate(AppRoutes.noteDetail(it)) }
                },
                onAddCorrection = {
                    s.detail.todayNoteId?.let { navController.navigate(AppRoutes.addendum(it)) }
                },
                onHistory = { navController.navigate(AppRoutes.tab(MainTab.History)) },
                onSummary = { navController.navigate(AppRoutes.summary(s.detail.id)) },
                onSavedSummaries = {
                    navController.navigate(AppRoutes.savedSummaries(s.detail.id))
                },
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

internal sealed interface LastNoteItem {
    data class Option(val group: OptionGroup, val value: String) : LastNoteItem
    data class Pain(val value: Int) : LastNoteItem
    data object FallReported : LastNoteItem
}

internal fun lastNoteItems(note: NoteDto): List<LastNoteItem> = buildList {
    val options = listOf(
        OptionGroup.Mood to note.mood,
        OptionGroup.Appetite to note.appetite,
        OptionGroup.Sleep to note.sleep,
        OptionGroup.Mobility to note.mobility,
        OptionGroup.Medication to note.medicationTaken,
    )
    options.forEach { (group, value) ->
        if (value.isNotBlank()) add(LastNoteItem.Option(group, value))
    }
    if (note.painRecorded) add(LastNoteItem.Pain(note.pain))
    if (note.fallRecorded && note.fall) add(LastNoteItem.FallReported)
}

internal data class CareDetailLine(
    val field: CareDetailsField,
    val value: String,
)

internal fun careDetailLines(details: CareDetails): List<CareDetailLine> = buildList {
    fun addLine(field: CareDetailsField, value: String?) {
        value?.trim()?.takeIf(String::isNotEmpty)?.let { add(CareDetailLine(field, it)) }
    }

    addLine(CareDetailsField.DateOfBirth, details.dateOfBirth)
    addLine(CareDetailsField.Allergies, details.allergies)
    addLine(CareDetailsField.Conditions, details.conditions)
    addLine(CareDetailsField.EmergencyContactName, details.emergencyContactName)
    addLine(CareDetailsField.EmergencyContactPhone, details.emergencyContactPhone)
}

/**
 * Recipient Detail header (#899): a round 48-dp back button at the start, a
 * 72-dp avatar, the name as the single heading with today's status chip, and
 * a round 48-dp edit button at the end.
 *
 * The row and its buttons are start/end-relative, so the whole header swaps
 * sides in Arabic; the back icon is `AutoMirrored`, so it points back in the
 * reading direction instead of always pointing left. All sizes and test tags
 * are local to this redesigned header — the shared AppTopBar and its 12-dp
 * rounded back button are unchanged for every other screen.
 */
@Composable
private fun DetailHeader(
    name: String,
    doneToday: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.GapForm)
            .testTag("detail_header"),
    ) {
        RoundDetailIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.testTag("detail_header_back"),
        )
        PersonAvatar(
            initial = RecipientNameRules.initial(name),
            size = PersonAvatarSize.Large,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Bidi.isolate(name),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = CaregiverColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(AppDimens.GapTight))
            StatusChip(
                label = stringResource(
                    if (doneToday) {
                        R.string.redesign_chip_done
                    } else {
                        R.string.redesign_chip_not_yet
                    },
                ),
                variant = if (doneToday) {
                    StatusChipVariant.Done
                } else {
                    StatusChipVariant.NotYet
                },
            )
        }
        RoundDetailIconButton(
            icon = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.detail_edit_person),
            onClick = onEdit,
            modifier = Modifier.testTag("detail_header_edit"),
        )
    }
}

/** The circular 48-dp icon button used at both ends of the detail header. */
@Composable
private fun RoundDetailIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = CaregiverColors.AvatarContainer,
        modifier = modifier.size(AppDimens.BackSize),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(AppDimens.BackSize)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = CaregiverColors.AvatarContent,
                modifier = Modifier.size(22.dp),
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
    onEdit: () -> Unit,
    onAddNote: () -> Unit,
    onHistory: () -> Unit,
    onSummary: () -> Unit,
    onPlan: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onViewToday: () -> Unit = {},
    onAddCorrection: () -> Unit = {},
    onCareDetails: () -> Unit = {},
    onSavedSummaries: () -> Unit = {},
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        DetailHeader(
            name = detail.name,
            doneToday = detail.todayNoteId != null,
            onBack = onBack,
            onEdit = onEdit,
        )
        if (!detail.relationship.isNullOrBlank()) {
            Text(
                stringResource(
                    R.string.detail_relationship,
                    Bidi.isolate(relationshipLabel(detail.relationship)),
                ),
                fontFamily = PlexArabic,
                fontSize = 14.sp,
                color = CaregiverColors.Muted,
            )
        }
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
                if (detail.todayNoteId == null) {
                    GrayPill(stringResource(R.string.detail_today_missing))
                } else {
                    GreenPill(stringResource(R.string.detail_today_done))
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
                if (detail.todayNoteId != null) {
                    SecondaryButton(
                        label = stringResource(R.string.editor_duplicate_view),
                        onClick = onViewToday,
                        enabled = !busy,
                        modifier = Modifier.testTag("detail_view_today"),
                    )
                    SecondaryButton(
                        label = stringResource(R.string.detail_add_correction),
                        onClick = onAddCorrection,
                        enabled = !busy,
                        modifier = Modifier.testTag("detail_add_correction"),
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
        }
        SectionTitle(stringResource(R.string.care_details_title))
        val careLines = careDetailLines(detail.careDetails)
        if (careLines.isEmpty()) {
            Text(
                stringResource(R.string.detail_care_empty),
                fontFamily = PlexArabic,
                fontSize = 14.sp,
                color = CaregiverColors.Muted,
            )
        } else {
            careLines.forEach { line ->
                val label = stringResource(
                    when (line.field) {
                        CareDetailsField.DateOfBirth -> R.string.care_dob
                        CareDetailsField.Allergies -> R.string.care_allergies
                        CareDetailsField.Conditions -> R.string.care_conditions
                        CareDetailsField.EmergencyContactName -> R.string.care_contact_name
                        CareDetailsField.EmergencyContactPhone -> R.string.care_contact_phone
                    },
                )
                val value = when (line.field) {
                    CareDetailsField.DateOfBirth,
                    CareDetailsField.EmergencyContactPhone,
                    -> Bidi.isolate(line.value)
                    CareDetailsField.Allergies,
                    CareDetailsField.Conditions,
                    CareDetailsField.EmergencyContactName,
                    -> line.value
                }
                Text(
                    "$label: $value",
                    fontFamily = PlexArabic,
                    fontSize = 14.sp,
                    color = CaregiverColors.Ink,
                )
            }
        }
        if (hasAdmin && !detail.archived) {
            SecondaryButton(
                label = stringResource(R.string.detail_care_edit),
                onClick = onCareDetails,
                enabled = !busy,
                modifier = Modifier.testTag("detail_care_edit"),
            )
        }
        // The status card above covers the no-notes empty state.
        detail.lastNote?.let { note ->
            val items = lastNoteItems(note)
            if (items.isNotEmpty()) {
                SectionTitle(stringResource(R.string.person_last_note))
                Text(
                    text = stringResource(
                        R.string.person_last_note_date,
                        Bidi.isolate(note.date),
                    ),
                    fontFamily = PlexArabic,
                    fontSize = 13.sp,
                    color = CaregiverColors.Muted,
                )
                val labels = mutableListOf<String>()
                for (item in items) {
                    labels += when (item) {
                        is LastNoteItem.Option -> optionLabel(item.group, item.value)
                        is LastNoteItem.Pain -> stringResource(
                            R.string.person_last_note_pain,
                            item.value,
                        )
                        LastNoteItem.FallReported -> stringResource(R.string.person_last_note_fall)
                    }
                }
                Text(
                    text = labels.joinToString(" · "),
                    fontFamily = PlexArabic,
                    fontSize = 15.sp,
                    color = CaregiverColors.Ink,
                )
            }
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
        SecondaryButton(
            label = stringResource(R.string.detail_saved_summaries),
            onClick = onSavedSummaries,
            enabled = !busy,
            modifier = Modifier.testTag("detail_saved_summaries"),
        )
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
                    label = stringResource(R.string.detail_edit_person),
                    onClick = onEdit,
                    enabled = !busy,
                    modifier = Modifier.testTag("detail_edit"),
                )
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
