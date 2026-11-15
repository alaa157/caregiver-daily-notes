package com.caregiver.mobile.presentation.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.core.util.appLocale
import com.caregiver.mobile.data.summary.SavedSummaryStore
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.time.Instant
import java.time.ZoneId

/**
 * Saved summaries for one recipient: every stored generation with its
 * coverage period, a care-flags pill when present, tap-to-reopen (no
 * provider call), and a confirmed delete. Reads and writes go through
 * [SavedSummaryStore]; the list is device-local, so nothing here reaches
 * a backend or a provider.
 */
@Composable
fun SavedSummariesScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: SavedSummariesViewModel = assistedViewModel("saved-summaries-$recipientId") {
        SavedSummariesViewModel(recipientId, graph.savedSummaryStore)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    val arabic = appLocale().language == "ar"
    var pendingDelete by remember {
        mutableStateOf<SavedSummaryStore.SavedSummaryItem?>(null)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            AppTopBar(
                title = stringResource(R.string.detail_saved_summaries),
                onBack = { navController.popBackStack() },
            )
            when (val s = state) {
                SavedSummariesState.Loading -> LoadingRow()
                SavedSummariesState.Error -> LoadFailed(onRetry = vm::refresh)
                is SavedSummariesState.Content -> {
                    if (s.items.isEmpty()) {
                        Text(
                            text = stringResource(R.string.summary_saved_empty),
                            fontFamily = PlexArabic,
                            fontSize = 15.sp,
                            color = CaregiverColors.Muted,
                        )
                    } else {
                        s.items.forEach { item ->
                            SavedSummaryRow(
                                item = item,
                                arabic = arabic,
                                onOpen = {
                                    navController.navigate(
                                        AppRoutes.summaryResult(
                                            recipientId,
                                            item.periodDays,
                                            end = item.periodEnd,
                                            saved = item.id,
                                        ),
                                    )
                                },
                                onDelete = { pendingDelete = item },
                            )
                        }
                    }
                }
            }
            if (deleteError) {
                Text(
                    text = stringResource(R.string.people_delete_failed),
                    fontFamily = PlexArabic,
                    fontSize = 13.sp,
                    color = CaregiverColors.Danger,
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.summary_delete_title)) },
            text = { Text(stringResource(R.string.summary_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.delete(item.id)
                    },
                    modifier = Modifier.testTag("summary_delete_confirm"),
                ) {
                    Text(stringResource(R.string.summary_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavedSummaryRow(
    item: SavedSummaryStore.SavedSummaryItem,
    arabic: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        Modifier
            .testTag("saved_summary_${item.id}")
            .clickable(onClick = onOpen),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = DateFormats.historyDay(
                        Instant.ofEpochMilli(item.createdAt)
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                        arabic,
                    ),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CaregiverColors.Ink,
                )
                Text(
                    text = stringResource(
                        R.string.summary_saved_period,
                        item.periodDays,
                        Bidi.isolate(item.periodStart),
                        Bidi.isolate(item.periodEnd),
                    ),
                    fontFamily = PlexArabic,
                    fontSize = 13.sp,
                    color = CaregiverColors.Muted,
                )
                if (item.hasFlags) {
                    GrayPill(stringResource(R.string.summary_has_flags))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.summary_delete),
                )
            }
        }
    }
}
