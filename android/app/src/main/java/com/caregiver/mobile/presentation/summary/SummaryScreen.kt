package com.caregiver.mobile.presentation.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.theme.AppRadius
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppWarningState
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.BusyBar
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.LockBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.UnclearBox
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/**
 * Summary period picker (board 10): blue info alert + period chips +
 * primary 56dp generate. Local chip state, no VM until generate.
 */
@Composable
fun SummaryPeriodScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    var period by rememberSaveable { mutableIntStateOf(14) }
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.summary_title),
                onBack = { navController.popBackStack() },
            )
            InfoAlertCard(
                title = stringResource(R.string.summary_info),
                body = stringResource(R.string.summary_info2),
            )
            SectionTitle(stringResource(R.string.summary_choose_period))
            Text(
                stringResource(R.string.summary_period),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CaregiverColors.TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryViewModel.PERIODS.forEach { days ->
                    FilterChip(
                        selected = period == days,
                        onClick = { period = days },
                        label = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (period == days) {
                                    Text("✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    periodLabel(days),
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        shape = RoundedCornerShape(AppRadius.pill),
                        border = BorderStroke(
                            AppSizes.borderWidth,
                            if (period == days) CaregiverColors.Primary else CaregiverColors.Border,
                        ),
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
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.summary_generate),
                onClick = { navController.navigate("summary-result/$recipientId/$period") },
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}

/**
 * Summary result (board 11, 1480px): red safety alert + overview +
 * important notes + unclear dashed box + meds lock footnote.
 * Trends are data-driven from evidence/uncertainties; no invented values.
 */
@Composable
fun SummaryResultScreen(
    recipientId: String,
    periodDays: Int,
    graph: AppGraph,
    navController: NavController,
) {
    val vm: SummaryViewModel = assistedViewModel("summary-$recipientId") {
        SummaryViewModel(recipientId, graph.apis, graph.auth, null, periodDays)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.md)) {
        when (val s = state) {
            SummaryState.Loading -> {
                Spacer(Modifier.height(AppSpacing.md))
                BusyBar(stringResource(R.string.state_summary_loading))
            }
            is SummaryState.AiUnavailable -> Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = AppSpacing.md),
            ) {
                s.last?.let { FlagsBanner(it.redFlags) }
                AppWarningState(
                    message = stringResource(R.string.state_ai_down),
                    detail = stringResource(R.string.state_ai_down_hint),
                )
                PrimaryButton(
                    label = stringResource(R.string.common_retry),
                    onClick = vm::refresh,
                )
            }
            is SummaryState.Rejected -> Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = AppSpacing.md),
            ) {
                s.last?.let { FlagsBanner(it.redFlags) }
                Text(
                    text = stringResource(R.string.summary_error_generic),
                    fontFamily = AppFontFamily,
                    color = CaregiverColors.Danger,
                )
                PrimaryButton(
                    label = stringResource(R.string.common_retry),
                    onClick = vm::refresh,
                )
            }
            is SummaryState.Content -> SummaryBody(s.summary, periodDays, navController)
        }
    }
}

/**
 * Known flags stay visible in every state. No dismiss action exists by
 * construction — the banner is information, not a dialog.
 */
@Composable
private fun FlagsBanner(redFlags: List<String>) {
    if (redFlags.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        redFlags.forEach { flag ->
            SafetyAlertCard(
                title = stringResource(R.string.home_safety_title),
                body = flagText(flag),
            )
        }
    }
}

@Composable
private fun SummaryBody(summary: SummaryDto, periodDays: Int, navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(
            title = stringResource(R.string.summary_title),
            onBack = { navController.popBackStack() },
        )
        if (summary.redFlags.isNotEmpty()) {
            FlagsBanner(summary.redFlags)
        }
        SectionTitle(stringResource(R.string.summary_overview))
        Text(
            text = summary.text,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            lineHeight = 29.sp,
            color = CaregiverColors.TextPrimary,
        )
        if (summary.evidence.isNotEmpty()) {
            SectionTitle(stringResource(R.string.summary_important))
            summary.evidence.forEach { item ->
                AppCard {
                    Text(
                        text = "“${Bidi.isolate(item.quote)}”",
                        fontFamily = AppFontFamily,
                        fontSize = 15.sp,
                        color = CaregiverColors.TextPrimary,
                    )
                    GrayPill(stringResource(R.string.summary_source, Bidi.isolate(item.noteId.take(8))))
                }
            }
        }
        if (summary.uncertainties.isNotEmpty()) {
            SectionTitle(stringResource(R.string.summary_unclear))
            summary.uncertainties.forEach { item ->
                UnclearBox("${item.topic}: ${item.detail}")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔒", fontSize = 16.sp)
            Text(
                stringResource(R.string.summary_meds_locked),
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                color = CaregiverColors.TextSecondary,
            )
        }
        PrimaryButton(
            label = stringResource(R.string.summary_proposals),
            onClick = { navController.navigate(AppDestinations.Plans.base) },
            height = AppSizes.inputHeight,
        )
        Spacer(Modifier.height(AppSpacing.xs))
    }
}

@Composable
private fun flagText(flag: String): String = when (flag) {
    "FALL_REPORTED" -> stringResource(R.string.flag_fall)
    "HIGH_PAIN" -> stringResource(R.string.flag_pain)
    "MEDICATION_UNCLEAR" -> stringResource(R.string.flag_meds)
    else -> flag
}

@Composable
private fun periodLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.summary_days_7
        14 -> R.string.summary_days_14
        else -> R.string.summary_days_30
    },
)
