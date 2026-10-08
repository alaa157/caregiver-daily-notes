package com.caregiver.mobile.presentation.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/** viewModel() with a constructor-injected dependency, keyed per destination. */
@Composable
inline fun <reified VM : ViewModel> assistedViewModel(
    key: String,
    crossinline create: () -> VM,
): VM {
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        },
    )
}
