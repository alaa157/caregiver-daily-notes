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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.FieldError
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.LightBluePill
import com.caregiver.mobile.presentation.common.LockBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.UnclearBox
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/** Plans list: rows → proposal; empty state with entry help. */
@Composable
fun PlansScreen(graph: AppGraph, navController: NavController) {
    val vm: PlansViewModel = assistedViewModel("plans") {
        PlansViewModel(graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(title = stringResource(R.string.plans_title))
        when (val s = state) {
            PlansState.Loading -> LoadingRow()
            PlansState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlansState.Content -> {
                if (s.refreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (s.plans.isEmpty()) {
                    AppEmptyState(
                        icon = "✓",
                        title = stringResource(R.string.plans_empty),
                        subtitle = stringResource(R.string.plan_versions_hint),
                        actionLabel = stringResource(R.string.plans_view_proposals),
                        onAction = { navController.navigate(MainTab.People.route) },
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(s.plans, key = { it.id }) { row ->
                            AppCard(
                                modifier = Modifier.clickable {
                                    navController.navigate(AppRoutes.planProposal(row.id))
                                },
                            ) {
                                Text(
                                    text = Bidi.isolate(row.recipientName),
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CaregiverColors.TextPrimary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    StatusChip(row.latestStatus)
                                    Text(
                                        text = stringResource(
                                            R.string.plans_versions_count,
                                            row.versionCount,
                                        ),
                                        fontFamily = AppFontFamily,
                                        fontSize = 13.sp,
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

/**
 * Plan proposal (board 12, 1420px): status line + blue alert + based-on +
 * recommendation cards + lock bar + unclear box + footer (primary accept +
 * row of 2 secondary, reject in red).
 */
@Composable
fun PlanProposalScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanDetailViewModel = assistedViewModel("plan-$planId") {
        PlanDetailViewModel(planId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    val error by vm.actionError.collectAsState()
    val busy by vm.busy.collectAsState()
    Column(Modifier.fillMaxSize()) {
        when (val s = state) {
            PlanDetailState.Loading -> LoadingRow()
            PlanDetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlanDetailState.Content -> {
                val latest = s.detail.versions.firstOrNull()
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppTopBar(
                        title = stringResource(R.string.plan_proposal_title),
                        onBack = { navController.popBackStack() },
                    )
                    // Status line: pill + review caption.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        latest?.let { StatusChip(it.status) }
                        Text(
                            stringResource(R.string.plan_suggested_review),
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp,
                            color = CaregiverColors.TextSecondary,
                        )
                    }
                    InfoAlertCard(
                        title = stringResource(R.string.plan_not_prescription_title),
                        body = stringResource(R.string.plan_not_prescription_body),
                    )
                    latest?.let {
                        SectionTitle(stringResource(R.string.plan_based_on))
                        it.items.forEach { item ->
                            AppCard {
                                Text(
                                    text = Bidi.isolate(item),
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CaregiverColors.TextPrimary,
                                )
                                LockBar(stringResource(R.string.plan_meds_locked))
                            }
                        }
                        if (it.reason.isNotBlank()) {
                            SectionTitle(stringResource(R.string.summary_unclear))
                            UnclearBox(Bidi.isolate(it.reason))
                        }
                    }
                    error?.let {
                        FieldError(
                            stringResource(
                                when (it) {
                                    PlanActionError.IllegalTransition -> R.string.plan_action_illegal
                                    PlanActionError.Failed -> R.string.plan_action_failed
                                },
                            ),
                        )
                    }
                }
                if (latest?.status != "Archived") {
                    BottomActionBar {
                        PrimaryButton(
                            label = stringResource(R.string.plan_accept_full),
                            onClick = { vm.transition(PlanAction.Accept) },
                            enabled = !busy,
                            height = AppSizes.inputHeight,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            SecondaryButton(
                                label = stringResource(R.string.plan_edit_accept),
                                onClick = { navController.navigate(AppRoutes.planEdit(planId)) },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                label = stringResource(R.string.plan_dismiss),
                                onClick = { vm.transition(PlanAction.Dismiss) },
                                enabled = !busy,
                                danger = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SecondaryButton(
                            label = stringResource(R.string.plan_versions_title),
                            onClick = { navController.navigate(AppRoutes.planVersions(planId)) },
                        )
                    }
                } else {
                    BottomActionBar {
                        SecondaryButton(
                            label = stringResource(R.string.plan_versions_title),
                            onClick = { navController.navigate(AppRoutes.planVersions(planId)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Plan edit (board 13): blue alert + item editors + reason field +
 * primary 56dp save-as-new-version.
 */
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
    Column(Modifier.fillMaxSize()) {
        when (val s = state) {
            PlanEditState.Loading -> LoadingRow()
            PlanEditState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlanEditState.Content -> {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppTopBar(
                        title = stringResource(R.string.plan_edit_title),
                        onBack = { navController.popBackStack() },
                    )
                    InfoAlertCard(
                        title = stringResource(R.string.plan_not_prescription_title),
                        body = stringResource(R.string.plan_not_prescription_body),
                    )
                    s.lines.forEachIndexed { index, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            OutlinedTextField(
                                value = line,
                                onValueChange = { vm.updateLine(index, it) },
                                singleLine = true,
                                shape = RoundedCornerShape(AppSpacing.sm),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CaregiverColors.Primary,
                                    unfocusedBorderColor = CaregiverColors.Border,
                                    focusedContainerColor = CaregiverColors.Surface,
                                    unfocusedContainerColor = CaregiverColors.Surface,
                                ),
                                modifier = Modifier.weight(1f).height(64.dp),
                            )
                            SecondaryButton(
                                label = "×",
                                onClick = { vm.removeLine(index) },
                                modifier = Modifier.weight(0.25f),
                            )
                        }
                    }
                    SecondaryButton(
                        label = stringResource(R.string.plan_edit_add_item),
                        onClick = vm::addLine,
                    )
                    if (saveFailed) {
                        FieldError(stringResource(R.string.plan_action_failed))
                    }
                }
                BottomActionBar {
                    PrimaryButton(
                        label = stringResource(R.string.plan_edit_save),
                        onClick = vm::save,
                        enabled = !busy,
                        height = AppSizes.buttonHeightLarge,
                        large = true,
                    )
                }
            }
        }
    }
}

/**
 * Plan versions (board 14): newest-first cards + ↓ separators + hint.
 */
@Composable
fun PlanVersionsScreen(planId: String, graph: AppGraph, navController: NavController) {
    val vm: PlanDetailViewModel = assistedViewModel("plan-versions-$planId") {
        PlanDetailViewModel(planId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(
            title = stringResource(R.string.plan_versions_title),
            onBack = { navController.popBackStack() },
        )
        when (val s = state) {
            PlanDetailState.Loading -> LoadingRow()
            PlanDetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is PlanDetailState.Content -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                items(s.detail.versions, key = { it.version }) { version ->
                    VersionRow(version)
                    Text(
                        "↓",
                        fontFamily = AppFontFamily,
                        textAlign = TextAlign.Center,
                        color = CaregiverColors.TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.plan_versions_hint),
                        fontFamily = AppFontFamily,
                        fontSize = 13.sp,
                        color = CaregiverColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: PlanVersionDto) {
    AppCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.plan_version_label, version.version),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CaregiverColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            StatusChip(version.status)
        }
        version.items.forEach { item ->
            Text(
                text = "• ${Bidi.isolate(item)}",
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                color = CaregiverColors.TextPrimary,
            )
        }
        if (version.reason.isNotBlank()) {
            Text(
                text = Bidi.isolate(version.reason),
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                color = CaregiverColors.TextSecondary,
            )
        }
    }
}
