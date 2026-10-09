package com.caregiver.mobile.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.data.demo.DemoPlanItem
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.FieldError
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.LightBluePill
import com.caregiver.mobile.presentation.common.LockBar
import com.caregiver.mobile.presentation.common.Pill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/**
 * Plan proposal (spec screen 9): AppBar, a Card per proposed action,
 * PrimaryButton plan.accept, SecondaryButton plan.edit, SecondaryButton
 * plan.dismiss, AlertInfo plan.disclaimer. Medication items are read-only.
 * Stub content is fixed sample text labeled "Demo". Plan history opens
 * from here (owner decision).
 * States: loading, empty, error, success.
 */
// DEMO-ONLY: "Demo" is a non-translated demo-chrome marker. Flagged.
private const val DEMO_BADGE = "Demo"

@Composable
fun PlanProposalScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanProposalDemoViewModel = assistedViewModel("plan-proposal-demo") {
        PlanProposalDemoViewModel(graph.demoPlans)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().testTag("screen_plan_proposal")) {
        when (val s = state) {
            PlanProposalDemoState.Loading -> LoadingRow()
            PlanProposalDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlanProposalDemoState.Content -> {
                if (s.items.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    ) {
                        AppTopBar(
                            title = stringResource(R.string.plan_accept),
                            onBack = { navController.popBackStack() },
                        )
                        AppEmptyState(
                            icon = "○",
                            title = stringResource(R.string.plan_accept),
                            subtitle = stringResource(R.string.plan_disclaimer),
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = vm::refresh,
                        )
                    }
                } else {
                    ProposalBody(s.items, s.status, vm, navController)
                }
            }
        }
    }
}

@Composable
private fun ProposalBody(
    items: List<DemoPlanItem>,
    status: String,
    vm: PlanProposalDemoViewModel,
    navController: NavController,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            AppTopBar(
                title = stringResource(R.string.plan_accept),
                onBack = { navController.popBackStack() },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Raw server-style status data (flagged: needs status copy).
                Pill(status, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
                Pill(DEMO_BADGE, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
            }
            InfoAlertCard(title = stringResource(R.string.plan_disclaimer))
            items.forEach { item ->
                AppCard {
                    Text(
                        text = Bidi.isolate(item.text),
                        style = MaterialTheme.typography.titleMedium,
                        color = CaregiverColors.TextPrimary,
                    )
                    Text(
                        text = Bidi.isolate(item.rationale),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CaregiverColors.TextPrimary,
                    )
                    if (item.medication) {
                        // Medication is read-only: lock bar, no edit control.
                        LockBar(stringResource(R.string.plan_disclaimer))
                    }
                }
            }
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.plan_accept),
                onClick = vm::accept,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                SecondaryButton(
                    label = stringResource(R.string.plan_edit),
                    onClick = { navController.navigate(AppRoutes.planEdit("demo")) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    label = stringResource(R.string.plan_dismiss),
                    onClick = vm::dismiss,
                    danger = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                label = stringResource(R.string.nav_history),
                onClick = { navController.navigate(AppRoutes.planVersions("demo")) },
            )
        }
    }
}

/**
 * Plan history (spec screen 10): AppBar, Timeline of versions with a Pill
 * per version status. Statuses render as raw server-style data until the
 * owner supplies status copy (flagged).
 * States: loading, empty, error, success.
 */
@Composable
fun PlanVersionsScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanHistoryDemoViewModel = assistedViewModel("plan-history-demo") {
        PlanHistoryDemoViewModel(graph.demoPlans)
    }
    val state by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md).testTag("screen_plan_history"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = stringResource(R.string.nav_history),
            onBack = { navController.popBackStack() },
        )
        when (val s = state) {
            PlanHistoryDemoState.Loading -> LoadingRow()
            PlanHistoryDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlanHistoryDemoState.Content -> if (s.versions.isEmpty()) {
                AppEmptyState(
                    icon = "○",
                    title = stringResource(R.string.nav_history),
                    subtitle = stringResource(R.string.plan_disclaimer),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::refresh,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    items(s.versions, key = { it.version }) { version ->
                        AppCard {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "v${version.version}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CaregiverColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Pill(version.status, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
                            }
                            version.items.forEach { item ->
                                Text(
                                    text = "• ${Bidi.isolate(item.text)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CaregiverColors.TextPrimary,
                                )
                            }
                            if (version.reason.isNotBlank()) {
                                Text(
                                    text = Bidi.isolate(version.reason),
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

/**
 * Plan edit (extra, not in spec): reached from the spec Plan proposal via
 * plan.edit. Non-medication items are editable; medication stays read-only
 * with a lock bar. Saving appends an edited in-memory version.
 */
@Composable
fun PlanEditScreen(planId: String, graph: AppGraph, navController: NavController) {
    val arabic = java.util.Locale.getDefault().language == "ar"
    var lines by remember(planId) {
        mutableStateOf(graph.demoPlans.proposal(arabic).filter { !it.medication }.map { it.text })
    }
    var saved by remember { mutableStateOf(false) }
    if (saved) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            AppTopBar(
                title = stringResource(R.string.plan_edit),
                onBack = { navController.popBackStack() },
            )
            InfoAlertCard(title = stringResource(R.string.plan_disclaimer))
            lines.forEachIndexed { index, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            OutlinedTextField(
                                value = line,
                                onValueChange = { v ->
                                    lines = lines.toMutableList().also { it[index] = v }
                                },
                        singleLine = true,
                        shape = RoundedCornerShape(AppSpacing.sm),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CaregiverColors.Primary,
                            unfocusedBorderColor = CaregiverColors.Border,
                            focusedContainerColor = CaregiverColors.Surface,
                            unfocusedContainerColor = CaregiverColors.Surface,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        label = "×",
                        onClick = { lines = lines.toMutableList().also { it.removeAt(index) } },
                        modifier = Modifier.weight(0.25f),
                    )
                }
            }
            SecondaryButton(
                label = stringResource(R.string.note_save),
                onClick = { lines = lines + "" },
            )
            LockBar(stringResource(R.string.plan_disclaimer))
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.note_save),
                onClick = {
                    val items = lines.filter { it.isNotBlank() }.map {
                        DemoPlanItem(it, "", false)
                    }
                    graph.demoPlans.editAccept(items, arabic)
                    saved = true
                },
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}

/** Plans list (extra, not in spec): orphaned from nav in the offline demo. */
@Composable
fun PlansScreen(graph: AppGraph, navController: NavController) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = stringResource(R.string.plan_disclaimer),
            onBack = { navController.popBackStack() },
        )
        AppEmptyState(
            icon = "○",
            title = stringResource(R.string.plan_disclaimer),
            subtitle = stringResource(R.string.people_empty),
            actionLabel = stringResource(R.string.home_summaryCta),
            onAction = { navController.navigate(MainTab.People.route) },
        )
    }
}

@Composable
fun StatusChip(status: String) {
    val label = PlanStatusText.res(status)?.let { stringResource(it) } ?: status
    when (status) {
        "Accepted", "Edited-and-Accepted" -> GreenPill(label)
        "Dismissed" -> RedPill(label)
        "Suggested" -> LightBluePill(label)
        else -> GreenPill(label)
    }
}

@Composable
private fun VersionRow(version: PlanVersionDto) {
    AppCard {
        Column(Modifier.padding(AppSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(
                    text = stringResource(R.string.plan_disclaimer),
                    style = MaterialTheme.typography.titleMedium,
                    color = CaregiverColors.TextPrimary,
                )
                StatusChip(version.status)
            }
            version.items.forEach { item -> Text(text = "• $item") }
            Text(
                text = version.reason,
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.TextSecondary,
            )
        }
    }
}
