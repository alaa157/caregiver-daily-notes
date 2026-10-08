package com.caregiver.mobile.presentation.home

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.OptionLabels
import java.util.Locale

/** Home dashboard (board 2): greeting, today progress, safety banner, cards. */
@Composable
fun HomeScreen(graph: AppGraph, navController: NavController) {
    val vm: HomeViewModel = assistedViewModel("home") {
        HomeViewModel(graph.auth, graph.settings)
    }
    val state by vm.state.collectAsState()
    when (val s = state) {
        HomeState.Loading, HomeState.Idle -> LoadingRow()
        is HomeState.Error -> {
            // Known flags survive failures: the banner stays above the retry.
            val flags = s.lastContent?.flags.orEmpty()
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                if (flags.isNotEmpty()) {
                    SafetyBanner(flags)
                }
                LoadFailed(onRetry = vm::refresh)
            }
        }
        is HomeState.Content -> {
            if (s.refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            HomeContent(s.content, navController)
        }
    }
}

@Composable
private fun HomeContent(content: HomeContent, navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = greetingText(content.greeting),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.home_today_progress,
                    content.done,
                    content.total,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (content.flags.isNotEmpty()) {
            item { SafetyBanner(content.flags) }
        }
        items(content.cards, key = { it.recipientId }) { card ->
            PersonCard(card) {
                navController.navigate(AppRoutes.recipientDetail(card.recipientId))
            }
        }
    }
}

/**
 * Fall-only banner, labeled as such: it covers reported falls from the
 * dashboard window, not every safety flag. Non-dismissible by construction —
 * no close action exists anywhere in this tree.
 */
@Composable
private fun SafetyBanner(flags: List<SafetyFlag>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.home_safety_title),
                style = MaterialTheme.typography.titleMedium,
                color = CaregiverColors.Danger,
            )
            flags.forEach { flag ->
                Text(
                    text = stringResource(R.string.home_safety_fall, flag.recipientName),
                    color = CaregiverColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun PersonCard(card: HomeCard, onOpen: () -> Unit) {
    val arabic = Locale.getDefault().language == "ar"
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp)) {
            Text(text = card.name, style = MaterialTheme.typography.titleMedium)
            card.lastNote?.let {
                Text(
                    text = stringResource(R.string.person_last_note) + ": " +
                        OptionLabels.label(OptionGroup.Appetite, it.appetite, arabic) + " · " +
                        OptionLabels.label(OptionGroup.Sleep, it.sleep, arabic),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.chips.forEach { ChipView(it) }
            }
        }
    }
}

@Composable
private fun ChipView(chip: RecipientChip) {
    val label = when (chip) {
        RecipientChip.DoneToday -> stringResource(R.string.chip_done)
        RecipientChip.MissedToday -> stringResource(R.string.chip_missed)
        RecipientChip.HighPain -> stringResource(R.string.chip_high_pain)
        RecipientChip.FallFlag -> stringResource(R.string.chip_fall)
        RecipientChip.NoNote -> stringResource(R.string.chip_no_note)
    }
    val colors = when (chip) {
        RecipientChip.DoneToday -> AssistChipDefaults.assistChipColors(
            containerColor = CaregiverColors.SuccessContainer,
            labelColor = CaregiverColors.Success,
        )
        RecipientChip.HighPain, RecipientChip.FallFlag -> AssistChipDefaults.assistChipColors(
            containerColor = CaregiverColors.DangerContainer,
            labelColor = CaregiverColors.Danger,
        )
        RecipientChip.NoNote, RecipientChip.MissedToday -> AssistChipDefaults.assistChipColors()
    }
    AssistChip(onClick = {}, label = { Text(label) }, colors = colors)
}

@Composable
private fun greetingText(greeting: Greeting): String {
    val name = greeting.name
    return if (greeting.morning) {
        if (name == null) {
            stringResource(R.string.home_greeting_morning_plain)
        } else {
            stringResource(R.string.home_greeting_morning, name)
        }
    } else {
        if (name == null) {
            stringResource(R.string.home_greeting_evening_plain)
        } else {
            stringResource(R.string.home_greeting_evening, name)
        }
    }
}

@Composable
fun LoadingRow() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.common_loading_failed))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}
