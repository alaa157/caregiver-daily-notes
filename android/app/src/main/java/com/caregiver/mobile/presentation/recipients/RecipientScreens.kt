package com.caregiver.mobile.presentation.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.TimelineItem
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Care recipients (spec screen 3): AppBar, one Card per recipient with
 * initials Avatar, name, last note date, FAB (people.add), BottomNav
 * (scaffold), EmptyState. The spec FAB lives on this screen.
 * States: loading, empty (people.empty), error, success.
 */
@Composable
fun RecipientsScreen(graph: AppGraph, navController: NavController) {
    val vm: RecipientsDemoViewModel = assistedViewModel("people-demo") {
        RecipientsDemoViewModel(graph.demoRecipients, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    val arabic = Locale.getDefault().language == "ar"
    Scaffold(
        containerColor = CaregiverColors.Background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(AppDestinations.AddRecipient.base) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.people_add)) },
                containerColor = CaregiverColors.Primary,
                contentColor = CaregiverColors.Surface,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            AppTopBar(title = stringResource(R.string.nav_people))
            when (val s = state) {
                DemoRecipientsState.Loading -> LoadingRow()
                DemoRecipientsState.Error -> LoadFailed(onRetry = vm::refresh)
                is DemoRecipientsState.Content -> if (s.rows.isEmpty()) {
                    AppEmptyState(
                        icon = "○",
                        title = stringResource(R.string.nav_people),
                        subtitle = stringResource(R.string.people_empty),
                        actionLabel = stringResource(R.string.people_add),
                        onAction = { navController.navigate(AppDestinations.AddRecipient.base) },
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(s.rows, key = { it.id }) { row ->
                            AppCard(
                                modifier = Modifier.clickable {
                                    navController.navigate(AppRoutes.recipientDetail(row.id))
                                },
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(Bidi.isolate(row.name).take(1))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = Bidi.isolate(row.name),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = CaregiverColors.TextPrimary,
                                        )
                                        lastNoteCaption(row.lastNoteDate, arabic)?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CaregiverColors.TextSecondary,
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
}

/**
 * Add-recipient flow (extra, not in spec): reached only from the spec
 * Care-recipients FAB/empty action. Name only; inline generic error.
 */
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val vm: AddRecipientDemoViewModel = assistedViewModel("add-recipient-demo") {
        AddRecipientDemoViewModel(graph.demoRecipients)
    }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }
    if (saved) {
        androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = stringResource(R.string.people_add),
            onBack = { navController.popBackStack() },
        )
        Text(
            text = stringResource(R.string.note_freeText_label),
            style = MaterialTheme.typography.labelLarge,
            color = CaregiverColors.TextPrimary,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onName,
            isError = state.failed,
            supportingText = {
                if (state.failed) {
                    Text(
                        stringResource(R.string.state_error),
                        color = CaregiverColors.Danger,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(AppSizes.cardRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().height(AppSizes.inputHeight).testTag("add_person_name"),
        )
        PrimaryButton(
            label = stringResource(R.string.note_save),
            onClick = {
                scope.launch {
                    if (vm.save() != null) saved = true
                }
            },
            loading = state.busy,
            modifier = Modifier.testTag("add_person_save"),
        )
    }
}

/**
 * Recipient detail (spec screen 4): AppBar with back, profile Card,
 * PrimaryButton for new note, recent notes as TimelineItems.
 * States: loading, empty (no recent notes), error, success.
 */
@Composable
fun RecipientDetailScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: RecipientDetailDemoViewModel = assistedViewModel("detail-demo-$recipientId") {
        RecipientDetailDemoViewModel(recipientId, graph.demoRecipients, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.md)) {
        when (val s = state) {
            DetailDemoState.Loading -> LoadingRow()
            DetailDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            is DetailDemoState.Content -> DetailContent(s, navController, vm)
        }
    }
}

@Composable
private fun DetailContent(
    s: DetailDemoState.Content,
    navController: NavController,
    vm: RecipientDetailDemoViewModel,
) {
    val arabic = Locale.getDefault().language == "ar"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = Bidi.isolate(s.name),
            onBack = { navController.popBackStack() },
        )
        AppCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(Bidi.isolate(s.name).take(1))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Bidi.isolate(s.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = CaregiverColors.TextPrimary,
                    )
                    lastNoteCaption(s.lastDate, arabic)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = CaregiverColors.TextSecondary,
                        )
                    }
                }
            }
        }
        if (s.recent.any { it.note.fall }) {
            // Demo rule output through the alert.redFlag copy.
            SafetyAlertCard(
                title = stringResource(R.string.alert_redFlag_fall),
                body = stringResource(R.string.alert_redFlag_fall),
            )
        }
        PrimaryButton(
            label = stringResource(R.string.home_addNote),
            onClick = { navController.navigate(AppRoutes.noteEditor(s.id)) },
        )
        if (s.recent.isEmpty()) {
            AppEmptyState(
                icon = "○",
                title = Bidi.isolate(s.name),
                subtitle = stringResource(R.string.people_empty),
                actionLabel = stringResource(R.string.home_addNote),
                onAction = { navController.navigate(AppRoutes.noteEditor(s.id)) },
            )
        } else {
            s.recent.forEach { item ->
                TimelineItem(
                    dateCaption = lastNoteCaption(item.note.date, arabic) ?: item.note.date,
                    summary = item.note.text.take(120),
                    onOpen = { navController.navigate(AppRoutes.noteDetail(item.note.id)) },
                )
            }
        }
    }
}
