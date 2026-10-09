package com.caregiver.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.caregiver.mobile.presentation.auth.DemoSignInScreen
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.caregiver.mobile.core.theme.AppSpacing

/**
 * Root gate (Step 6): no demo session → demo sign-in; session active →
 * tabs. No login, no network, no credentials.
 */
@Composable
fun CaregiverRoot(graph: AppGraph) {
    LaunchedEffect(Unit) { graph.ensureSeeded() }
    val active by graph.demoSession.active.collectAsState(initial = null)
    when (active) {
        null -> Column(
            Modifier.fillMaxSize().padding(AppSpacing.lg),
        ) { AppLoadingSkeleton() }
        false -> DemoSignInScreen(graph)
        true -> MainScaffold(graph)
    }
}
