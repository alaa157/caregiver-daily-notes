package com.caregiver.mobile.presentation.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.caregiver.mobile.data.demo.DemoSummary
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppWarningState
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.Pill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadingRow

/**
 * Summary (spec screen 8): AppBar, segmented 7/14/30 control, PrimaryButton
 * summary.generate, result Card, AlertSafety when applicable, AlertInfo
 * plan.disclaimer. Tapping the result Card opens Plan proposal (owner
 * decision). Stub content is fixed sample text labeled "Demo".
 * States: idle, generating (Skeleton + button label change), unavailable
 * (notes still accessible), success; errors use the shared error state.
 */
// DEMO-ONLY: "Demo" is a non-translated demo-chrome marker (copy owns no
// demo keys). Flagged in the report.
private const val DEMO_BADGE = "Demo"

@Composable
fun SummaryPeriodScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: SummaryDemoViewModel = assistedViewModel("summary-demo-$recipientId") {
        SummaryDemoViewModel(recipientId, graph.demoNotes, graph.demoSummary)
    }
    val period by vm.period.collectAsState()
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = stringResource(R.string.home_summaryCta),
            onBack = { navController.popBackStack() },
        )
        PeriodRow(period, vm::setPeriod)
        val generating = state is SummaryDemoState.Generating
        PrimaryButton(
            // Generating state changes the button label (screens.md).
            label = stringResource(
                if (generating) R.string.state_loading else R.string.summary_generate,
            ),
            onClick = vm::generate,
            loading = generating,
            height = AppSizes.buttonHeightLarge,
            large = true,
        )
        when (val s = state) {
            SummaryDemoState.Idle -> Unit
            SummaryDemoState.Generating -> AppLoadingSkeleton()
            SummaryDemoState.Unavailable -> {
                AppWarningState(message = stringResource(R.string.summary_unavailable))
                SecondaryButton(
                    label = stringResource(R.string.nav_history),
                    onClick = { navController.navigate(AppRoutes.tab(MainTab.History)) },
                )
            }
            SummaryDemoState.Error -> AppErrorState(
                message = stringResource(R.string.state_error),
                onRetry = vm::generate,
            )
            is SummaryDemoState.Content -> SummaryBody(s.summary, navController)
        }
    }
}

/**
 * Summary result (spec screen 8, result part): banner, body, evidence,
 * disclaimer. Kept as a separate destination for back-stack parity.
 */
@Composable
fun SummaryResultScreen(
    recipientId: String,
    periodDays: Int,
    graph: AppGraph,
    navController: NavController,
) {
    val vm: SummaryDemoViewModel = assistedViewModel("summary-demo-$recipientId") {
        SummaryDemoViewModel(recipientId, graph.demoNotes, graph.demoSummary, periodDays)
    }
    val period by vm.period.collectAsState()
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(
            title = stringResource(R.string.home_summaryCta),
            onBack = { navController.popBackStack() },
        )
        PeriodRow(period, vm::setPeriod)
        val generating = state is SummaryDemoState.Generating
        PrimaryButton(
            label = stringResource(
                if (generating) R.string.state_loading else R.string.summary_generate,
            ),
            onClick = vm::generate,
            loading = generating,
            height = AppSizes.buttonHeightLarge,
            large = true,
        )
        when (val s = state) {
            SummaryDemoState.Idle -> LoadingRow()
            SummaryDemoState.Generating -> AppLoadingSkeleton()
            SummaryDemoState.Unavailable -> {
                AppWarningState(message = stringResource(R.string.summary_unavailable))
                SecondaryButton(
                    label = stringResource(R.string.nav_history),
                    onClick = { navController.navigate(AppRoutes.tab(MainTab.History)) },
                )
            }
            SummaryDemoState.Error -> AppErrorState(
                message = stringResource(R.string.state_error),
                onRetry = vm::generate,
            )
            is SummaryDemoState.Content -> SummaryBody(s.summary, navController)
        }
    }
}

@Composable
private fun PeriodRow(period: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        listOf(7, 14, 30).forEach { days ->
            FilterChip(
                selected = period == days,
                onClick = { onSelect(days) },
                label = { Text(periodLabel(days)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = CaregiverColors.Surface,
                    labelColor = CaregiverColors.TextPrimary,
                    selectedContainerColor = CaregiverColors.Primary,
                    selectedLabelColor = CaregiverColors.Surface,
                ),
            )
        }
    }
}

@Composable
private fun SummaryBody(summary: DemoSummary, navController: NavController) {
    if (summary.redFlags.isNotEmpty()) {
        // Demo rule output rendered through the alert.redFlag copy.
        SafetyAlertCard(
            title = stringResource(R.string.alert_redFlag_fall),
            body = stringResource(R.string.alert_redFlag_fall),
        )
    }
    // Owner decision: tapping the result Card opens Plan proposal.
    AppCard(
        modifier = Modifier.clickable { navController.navigate(AppRoutes.planProposal("demo")) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(DEMO_BADGE, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
        }
        Text(
            text = summary.text,
            style = MaterialTheme.typography.bodyLarge,
            color = CaregiverColors.TextPrimary,
        )
    }
    if (summary.evidence.isNotEmpty()) {
        summary.evidence.forEach { item ->
            AppCard {
                Text(
                    text = "“${Bidi.isolate(item.quote)}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CaregiverColors.TextPrimary,
                )
            }
        }
    }
    if (summary.uncertainties.isNotEmpty()) {
        summary.uncertainties.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.TextSecondary,
            )
        }
    }
    InfoAlertCard(title = stringResource(R.string.plan_disclaimer))
}

@Composable
private fun periodLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.summary_period_7
        14 -> R.string.summary_period_14
        else -> R.string.summary_period_30
    },
)
