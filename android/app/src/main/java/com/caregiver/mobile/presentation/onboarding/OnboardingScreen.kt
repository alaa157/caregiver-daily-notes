package com.caregiver.mobile.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(graph: AppGraph, onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val pages = listOf(
        Triple(R.string.onboarding_title_1, R.string.onboarding_body_1, R.string.onboarding_point_1),
        Triple(R.string.onboarding_title_2, R.string.onboarding_body_2, R.string.onboarding_point_2),
        Triple(R.string.onboarding_title_3, R.string.onboarding_body_3, R.string.onboarding_point_3),
    )
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Caregiver Daily Notes", style = MaterialTheme.typography.titleLarge)
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val (t, b, p) = pages[page]
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(t), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(b), style = MaterialTheme.typography.bodyLarge)
                Text("• " + stringResource(p), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                scope.launch { graph.settings.setOnboardingCompleted() }
                onFinished()
            }) { Text(stringResource(R.string.onboarding_skip)) }
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Button(onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    scope.launch { graph.settings.setOnboardingCompleted() }
                    onFinished()
                }
            }) {
                Text(if (pagerState.currentPage < 2) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_get_started))
            }
        }
    }
}
