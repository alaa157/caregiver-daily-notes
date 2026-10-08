package com.caregiver.mobile.presentation.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * viewModel() with a constructor-injected dependency, keyed per destination.
 * Pass the activity as [owner] to share one VM across destinations (note
 * detail + addendum form); default stays destination-scoped.
 */
@Composable
inline fun <reified VM : ViewModel> assistedViewModel(
    key: String,
    owner: ViewModelStoreOwner? = null,
    crossinline create: () -> VM,
): VM {
    return viewModel(
        key = key,
        viewModelStoreOwner = owner ?: checkNotNull(LocalViewModelStoreOwner.current),
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        },
    )
}
