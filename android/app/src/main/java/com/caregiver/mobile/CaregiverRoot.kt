package com.caregiver.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.caregiver.mobile.presentation.auth.DemoSignInScreen

/**
 * Root gate (Step 6): demo session active → tabs, otherwise the demo
 * sign-in. A null first emission means "no session stored", so it also
 * shows sign-in — null must never render a waiting state, or fresh
 * installs deadlock on it (only Start demo can flip the flag).
 */
@Composable
fun CaregiverRoot(graph: AppGraph) {
    LaunchedEffect(Unit) { graph.ensureSeeded() }
    val active by graph.demoSession.active.collectAsState(initial = null)
    if (active == true) {
        MainScaffold(graph)
    } else {
        DemoSignInScreen(graph)
    }
}
