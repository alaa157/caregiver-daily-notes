package com.caregiver.mobile.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Provides an explicit [ViewModelStoreOwner] for `createComposeRule`
 * content. The headless rule ships no owner, so any screen calling
 * `viewModel()` would crash without this wrapper. Key the call site to
 * emulate activity recreation: a new key means new VMs, exactly like a
 * rotation that kills the activity while disk state survives.
 */
@Composable
fun WithTestOwner(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
