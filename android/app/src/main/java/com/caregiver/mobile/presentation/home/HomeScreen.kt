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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.BusyBar
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.RedPill
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.YellowPill
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        when (val s = state) {
            HomeState.Loading, HomeState.Idle -> {
                Spacer(Modifier.height(16.dp))
                AppLoadingSkeleton()
            }
            is HomeState.Error -> {
                Spacer(Modifier.height(16.dp))
                val flags = s.lastContent?.flags.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (flags.isNotEmpty()) {
                        SafetyBanner(flags)
                    }
                    AppErrorState(
                        message = stringResource(R.string.common_loading_failed),
                        onRetry = vm::refresh,
                    )
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
}

@Composable
private fun HomeContent(content: HomeContent, navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Header: h1 22sp Bold + date 13sp muted, settings action.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greetingText(content.greeting),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = CaregiverColors.Ink,
                    )
                    // Date line comes from the greeting content when available.
                    Text(
                        text = stringResource(
                            R.string.home_today_progress,
                            content.done,
                            content.total,
                        ),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = CaregiverColors.Ink,
                    )
                }
            }
        }
        if (content.flags.isNotEmpty()) {
            item { SafetyBanner(content.flags) }
        }
        item {
            // Stats row: ملاحظات اليوم + تحتاج إلى الانتباه.
            val attention = content.cards.count {
                it.chips.contains(RecipientChip.HighPain) ||
                    it.chips.contains(RecipientChip.FallFlag)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard(
                    modifier = Modifier.weight(1f),
                    padding = 12.dp,
                ) {
                    Text(
                        stringResource(R.string.home_notes_today),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                    Text(
                        stringResource(R.string.home_today_progress, content.done, content.total),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CaregiverColors.Ink,
                    )
                }
                AppCard(
                    modifier = Modifier.weight(1f),
                    padding = 12.dp,
                ) {
                    Text(
                        stringResource(R.string.home_needs_attention),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                    Text(
                        "$attention",
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CaregiverColors.Ink,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.home_section_today),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = CaregiverColors.Ink,
            )
        }
        if (content.cards.isEmpty()) {
            item {
                AppEmptyState(
                    icon = "👤",
                    title = stringResource(R.string.people_empty),
                    subtitle = stringResource(R.string.home_section_today),
                    actionLabel = stringResource(R.string.people_add),
                    onAction = { navController.navigate("people") },
                )
            }
        } else {
            items(content.cards, key = { it.recipientId }) { card ->
                PersonCard(card) {
                    navController.navigate(AppRoutes.recipientDetail(card.recipientId))
                }
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
    val first = flags.firstOrNull()
    SafetyAlertCard(
        title = stringResource(R.string.home_safety_title),
        body = if (first != null) {
            stringResource(R.string.home_safety_fall, Bidi.isolate(first.recipientName))
        } else {
            ""
        },
    )
}

@Composable
private fun PersonCard(card: HomeCard, onOpen: () -> Unit) {
    val arabic = Locale.getDefault().language == "ar"
    AppCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Bidi.isolate(card.name).take(1))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = Bidi.isolate(card.name),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CaregiverColors.Ink,
                )
                card.lastNote?.let {
                    Text(
                        text = stringResource(R.string.person_last_note) + ": " +
                            OptionLabels.label(OptionGroup.Appetite, it.appetite, arabic) + " · " +
                            OptionLabels.label(OptionGroup.Sleep, it.sleep, arabic),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.chips.forEach { ChipView(it) }
                }
            }
        }
        // In-card primary action when today's note is missing (HTML board).
        if (card.chips.contains(RecipientChip.MissedToday) ||
            card.chips.contains(RecipientChip.NoNote)
        ) {
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaregiverColors.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    stringResource(R.string.detail_add_note),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                )
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
    when (chip) {
        RecipientChip.DoneToday -> GreenPill(label)
        RecipientChip.HighPain, RecipientChip.FallFlag -> RedPill(label)
        RecipientChip.NoNote, RecipientChip.MissedToday -> GrayPill(label)
    }
}

@Composable
private fun greetingText(greeting: Greeting): String {
    val name = greeting.name?.let { Bidi.isolate(it) }
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
        AppLoadingSkeleton()
    }
}

@Composable
fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppErrorState(
            message = stringResource(R.string.common_loading_failed),
            onRetry = onRetry,
        )
    }
}
