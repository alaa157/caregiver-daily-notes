package com.caregiver.mobile.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/** Plans list (missing page): rows → proposal; empty state with entry help. */
@Composable
fun PlansScreen(graph: AppGraph, navController: NavController) {
    val vm: PlansViewModel = assistedViewModel("plans") {
        PlansViewModel(graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.plans_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            PlansState.Loading -> LoadingRow()
            PlansState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlansState.Content -> {
                if (s.refreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (s.plans.isEmpty()) {
                    Text(stringResource(R.string.plans_empty))
                    Spacer(Modifier.height(8.dp))
                    // No plan exists yet, so there is nothing to propose from:
                    // point at People, where a recipient (and then a summary)
                    // is the entry point to future plans.
                    OutlinedButton(onClick = { navController.navigate(MainTab.People.route) }) {
                        Text(stringResource(R.string.plans_view_proposals))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.plans, key = { it.id }) { row ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    navController.navigate(AppRoutes.planProposal(row.id))
                                },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = row.recipientName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatusChip(row.latestStatus)
                                        Text(
                                            text = stringResource(
                                                R.string.plans_versions_count,
                                                row.versionCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
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

@Composable
fun StatusChip(status: String) {
    val label = PlanStatusText.res(status)?.let { stringResource(it) } ?: status
    // Archived is terminal with no further action, so it deliberately keeps
    // the default neutral chip instead of a semantic success/danger color.
    val colors = when (status) {
        "Accepted", "Edited-and-Accepted" -> AssistChipDefaults.assistChipColors(
            containerColor = CaregiverColors.SuccessContainer,
            labelColor = CaregiverColors.Success,
        )
        "Dismissed" -> AssistChipDefaults.assistChipColors(
            containerColor = CaregiverColors.DangerContainer,
            labelColor = CaregiverColors.Danger,
        )
        "Suggested" -> AssistChipDefaults.assistChipColors(
            containerColor = CaregiverColors.InfoContainer,
            labelColor = CaregiverColors.Info,
        )
        else -> AssistChipDefaults.assistChipColors()
    }
    AssistChip(onClick = {}, label = { Text(label) }, colors = colors)
}

/** Plan proposal (board 12): latest items plus accept/dismiss/archive/edit. */
@Composable
fun PlanProposalScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanDetailViewModel = assistedViewModel("plan-$planId") {
        PlanDetailViewModel(planId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    val error by vm.actionError.collectAsState()
    val busy by vm.busy.collectAsState()
    when (val s = state) {
        PlanDetailState.Loading -> LoadingRow()
        PlanDetailState.Error -> LoadFailed(onRetry = vm::refresh)
        is PlanDetailState.Content -> {
            val latest = s.detail.versions.firstOrNull()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.plan_proposal_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                latest?.let {
                    StatusChip(it.status)
                    it.items.forEach { item -> Text(text = "• $item") }
                }
                error?.let {
                    Text(
                        text = stringResource(
                            when (it) {
                                PlanActionError.IllegalTransition -> R.string.plan_action_illegal
                                PlanActionError.Failed -> R.string.plan_action_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (latest?.status != "Archived") {
                    Button(
                        onClick = { vm.transition(PlanAction.Accept) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(R.string.plan_accept))
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(AppRoutes.planEdit(planId)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.plan_edit))
                    }
                    OutlinedButton(
                        onClick = { vm.transition(PlanAction.Dismiss) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.plan_dismiss))
                    }
                    OutlinedButton(
                        onClick = { vm.transition(PlanAction.Archive) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.plan_archive))
                    }
                }
                OutlinedButton(
                    onClick = { navController.navigate(AppRoutes.planVersions(planId)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.plan_versions_title))
                }
            }
        }
    }
}

/** Plan edit (board 13): item list editor; save appends an edited version. */
@Composable
fun PlanEditScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanEditViewModel = assistedViewModel("plan-edit-$planId") {
        PlanEditViewModel(planId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val saved by vm.saved.collectAsState()
    val saveFailed by vm.saveFailed.collectAsState()
    if (saved) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    }
    when (val s = state) {
        PlanEditState.Loading -> LoadingRow()
        PlanEditState.Error -> LoadFailed(onRetry = vm::refresh)
        is PlanEditState.Content -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            s.lines.forEachIndexed { index, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = line,
                        onValueChange = { vm.updateLine(index, it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { vm.removeLine(index) }) {
                        Text("×")
                    }
                }
            }
            OutlinedButton(onClick = vm::addLine) {
                Text(stringResource(R.string.plan_edit_add_item))
            }
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.plan_action_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = vm::save,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.plan_edit_save))
            }
        }
    }
}

/** Plan versions (board 14): newest-first history with server reasons. */
@Composable
fun PlanVersionsScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanDetailViewModel = assistedViewModel("plan-versions-$planId") {
        PlanDetailViewModel(planId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    when (val s = state) {
        PlanDetailState.Loading -> LoadingRow()
        PlanDetailState.Error -> LoadFailed(onRetry = vm::refresh)
        is PlanDetailState.Content -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.plan_versions_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            items(s.detail.versions, key = { it.version }) { version ->
                VersionRow(version)
            }
        }
    }
}

@Composable
private fun VersionRow(version: PlanVersionDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.plan_version_label, version.version),
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusChip(version.status)
            }
            version.items.forEach { item -> Text(text = "• $item") }
            Text(
                text = version.reason,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
