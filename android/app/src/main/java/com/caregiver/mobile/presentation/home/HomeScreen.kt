package com.caregiver.mobile.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.TimelineItem
import com.caregiver.mobile.presentation.common.assistedViewModel
import java.util.Locale

/**
 * Home (spec screen 2): AppBar greeting, today-status Card, PrimaryButton
 * home.addNote, SecondaryButton home.summaryCta, BottomNav (scaffold),
 * recent TimelineItems, AlertSafety when a latest fall alert exists.
 * The spec FAB lives on this screen (add-note action).
 * States: loading (Skeleton), empty, error, success.
 */
@Composable
fun HomeScreen(graph: AppGraph, navController: NavController) {
    val vm: HomeDemoViewModel = assistedViewModel("home-demo") {
        HomeDemoViewModel(graph.demoRecipients, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = CaregiverColors.Background,
        floatingActionButton = {
            if (state is DemoHomeState.Content && !(state as DemoHomeState.Content).empty) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val target = (state as DemoHomeState.Content).content.targetRecipientId
                        if (target != null) navController.navigate(AppRoutes.noteEditor(target))
                        else navController.navigate(AppRoutes.tab(MainTab.People))
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_addNote)) },
                    containerColor = CaregiverColors.Primary,
                    contentColor = CaregiverColors.Surface,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = AppSpacing.md),
        ) {
            when (val s = state) {
                DemoHomeState.Loading -> AppLoadingSkeleton()
                DemoHomeState.Error -> AppErrorState(
                    message = stringResource(R.string.state_error),
                    onRetry = vm::refresh,
                )
                is DemoHomeState.Content -> if (s.empty) {
                    HomeEmpty { navController.navigate(AppRoutes.tab(MainTab.People)) }
                } else {
                    HomeContent(s.content, navController)
                }
            }
        }
    }
}

@Composable
private fun HomeEmpty(onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AppTopBar(title = stringResource(R.string.nav_home))
        AppEmptyState(
            icon = "○",
            title = stringResource(R.string.nav_home),
            subtitle = stringResource(R.string.people_empty),
            actionLabel = stringResource(R.string.people_add),
            onAction = onAdd,
        )
    }
}

@Composable
private fun HomeContent(content: DemoHomeContent, navController: NavController) {
    val arabic = Locale.getDefault().language == "ar"
    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        item {
            AppTopBar(
                // Spec greeting template; time-of-day words own no copy key,
                // so the token is dropped (flagged). Values stay verbatim.
                title = stringResource(R.string.home_greeting).replace("{timeOfDay}", "").trim(),
            )
        }
        item {
            AppCard {
                Text(
                    "${content.doneToday} / ${content.totalRecipients}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CaregiverColors.TextPrimary,
                )
            }
        }
        if (content.fallRecipients.isNotEmpty()) {
            item {
                // Demo rule output rendered through the alert.redFlag copy.
                SafetyAlertCard(
                    title = stringResource(R.string.alert_redFlag_fall),
                    body = content.fallRecipients.joinToString(", ") { Bidi.isolate(it) },
                )
            }
        }
        item {
            val target = content.targetRecipientId
            PrimaryButton(
                label = stringResource(R.string.home_addNote),
                onClick = {
                    if (target != null) navController.navigate(AppRoutes.noteEditor(target))
                    else navController.navigate(AppRoutes.tab(MainTab.People))
                },
            )
        }
        item {
            val target = content.targetRecipientId
            SecondaryButton(
                label = stringResource(R.string.home_summaryCta),
                onClick = {
                    if (target != null) navController.navigate(AppRoutes.summary(target))
                    else navController.navigate(AppRoutes.tab(MainTab.People))
                },
            )
        }
        items(content.recent, key = { it.note.id }) { item ->
            TimelineItem(
                dateCaption = DateFormats.historyDay(item.note.date, arabic),
                summary = Bidi.isolate(item.recipientName) + " · " + item.note.text.take(120),
                onOpen = { navController.navigate(AppRoutes.noteDetail(item.note.id)) },
            )
        }
    }
}

/** Shared loading/error helpers reused across the rewritten screens. */
@Composable
fun LoadingRow() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLoadingSkeleton()
    }
}

@Composable
fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppErrorState(
            message = stringResource(R.string.state_error),
            onRetry = onRetry,
        )
    }
}
