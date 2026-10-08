package com.caregiver.mobile.presentation.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/** Summary period picker (board 10): local chip state, no VM until generate. */
@Composable
fun SummaryPeriodScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    var period by rememberSaveable { mutableIntStateOf(7) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.summary_period))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryViewModel.PERIODS.forEach { days ->
                FilterChip(
                    selected = period == days,
                    onClick = { period = days },
                    label = { Text(periodLabel(days)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { navController.navigate("summary-result/$recipientId/$period") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.summary_generate))
        }
    }
}

/** Summary result (board 11): banner, body, evidence, uncertainties. */
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
    when (val s = state) {
        SummaryState.Loading -> LoadingRow()
        is SummaryState.AiUnavailable -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            s.last?.let { FlagsBanner(it.redFlags) }
            Text(stringResource(R.string.summary_ai_down))
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::refresh) { Text(stringResource(R.string.common_retry)) }
        }
        is SummaryState.Rejected -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            s.last?.let { FlagsBanner(it.redFlags) }
            Text(
                text = stringResource(R.string.summary_error_generic),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::refresh) { Text(stringResource(R.string.common_retry)) }
        }
        is SummaryState.Content -> SummaryBody(s.summary, navController)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            redFlags.forEach { flag ->
                Text(text = flagText(flag), color = CaregiverColors.Danger)
            }
        }
    }
}

@Composable
private fun SummaryBody(summary: SummaryDto, navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary.redFlags.isNotEmpty()) {
            FlagsBanner(summary.redFlags)
        }
        // Server-composed body and quotes render verbatim: they are data, and
        // the preview backend only composes them in English.
        Text(text = summary.text, style = MaterialTheme.typography.bodyLarge)
        if (summary.evidence.isNotEmpty()) {
            Text(
                text = stringResource(R.string.summary_evidence),
                style = MaterialTheme.typography.titleMedium,
            )
            summary.evidence.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(text = "“${item.quote}”")
                        Text(
                            text = stringResource(R.string.person_last_note) +
                                ": #${item.noteId.take(8)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (summary.uncertainties.isNotEmpty()) {
            Text(
                text = stringResource(R.string.summary_uncertain),
                style = MaterialTheme.typography.titleMedium,
            )
            summary.uncertainties.forEach { item ->
                Text(text = "${item.topic}: ${item.detail}")
            }
        }
        Text(
            text = stringResource(R.string.summary_disclaimer),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { navController.navigate(AppDestinations.Plans.base) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.summary_proposals))
        }
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
