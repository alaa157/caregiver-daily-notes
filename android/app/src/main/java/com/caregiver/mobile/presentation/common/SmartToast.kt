package com.caregiver.mobile.presentation.common

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Lightweight replacement for scattered android.widget.Toast calls.
 * Screens can host this once and call [SnackbarHostState.showSnackbar] (or the
 * helper below) to surface transient messages consistently.
 */
@Composable
fun SmartToastHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}

@Composable
fun rememberSmartToastState(): SnackbarHostState = remember { SnackbarHostState() }
