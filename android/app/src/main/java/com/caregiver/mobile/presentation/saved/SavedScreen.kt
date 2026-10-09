package com.caregiver.mobile.presentation.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.TimelineItem
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale

/**
 * Saved (spec screen 11): AppBar, list of saved notes, BottomNav
 * (scaffold), TimelineItems where applicable. Selecting a saved note
 * opens Note detail; saved notes remain immutable.
 * States: loading, empty, error, success.
 */
@Composable
fun SavedScreen(graph: AppGraph, navController: NavController) {
    val vm: SavedDemoViewModel = assistedViewModel("saved-demo") {
        SavedDemoViewModel(graph.demoRecipients, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    val arabic = Locale.getDefault().language == "ar"
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md).testTag("screen_saved"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(title = stringResource(R.string.nav_saved))
        when (val s = state) {
            SavedDemoState.Loading -> LoadingRow()
            SavedDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            is SavedDemoState.Content -> if (s.entries.isEmpty()) {
                AppEmptyState(
                    icon = "○",
                    title = stringResource(R.string.nav_saved),
                    subtitle = stringResource(R.string.people_empty),
                    actionLabel = stringResource(R.string.home_addNote),
                    onAction = { navController.navigate(AppRoutes.tab(MainTab.Home)) },
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    items(s.entries, key = { it.note.id }) { entry ->
                        TimelineItem(
                            dateCaption = DateFormats.historyDay(entry.note.date, arabic),
                            summary = Bidi.isolate(entry.recipientName) +
                                " · " + entry.note.text.take(120),
                            onOpen = { navController.navigate(AppRoutes.noteDetail(entry.note.id)) },
                        )
                    }
                }
            }
        }
    }
}
