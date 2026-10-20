package com.caregiver.mobile.presentation.onboarding

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.presentation.common.AppCard
import kotlinx.coroutines.launch

/**
 * First-run onboarding: three cards of what the app is for, with an ar/en
 * language toggle up top — the first thing a caregiver may get wrong is the
 * language, and switching here writes through the same DataStore + mirror
 * pipeline as the settings toggle (then recreates the activity so RTL and
 * resources flip in one frame).
 */
@Composable
fun OnboardingScreen(graph: AppGraph, onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    val pages = listOf(
        Triple(R.string.onboarding_title_1, R.string.onboarding_body_1, R.string.onboarding_point_1),
        Triple(R.string.onboarding_title_2, R.string.onboarding_body_2, R.string.onboarding_point_2),
        Triple(R.string.onboarding_title_3, R.string.onboarding_body_3, R.string.onboarding_point_3),
    )
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = CaregiverColors.Ink,
                modifier = Modifier.weight(1f).testTag("onboarding_title"),
            )
            // Same pair, labels and guard as the settings language row: the
            // selected chip shows where you are, the other one switches.
            FilterChip(
                selected = language == "en",
                onClick = {
                    if (language != "en") {
                        scope.launch {
                            graph.settings.setLanguage("en")
                            activity?.recreate()
                        }
                    }
                },
                label = { Text("English") },
                modifier = Modifier.testTag("onboarding_language_en"),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = language == "ar",
                onClick = {
                    if (language != "ar") {
                        scope.launch {
                            graph.settings.setLanguage("ar")
                            activity?.recreate()
                        }
                    }
                },
                label = { Text("العربية") },
                modifier = Modifier.testTag("onboarding_language_ar"),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val (t, b, p) = pages[page]
            // Each page gets full card chrome; the column scrolls so large
            // font scales can never clip the body text.
            AppCard(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(t),
                        style = MaterialTheme.typography.headlineSmall,
                        color = CaregiverColors.Ink,
                    )
                    Text(
                        stringResource(b),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CaregiverColors.Ink,
                    )
                    Text(
                        "• " + stringResource(p),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CaregiverColors.Muted,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                CaregiverColors.BorderSoft
                            },
                        ),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    scope.launch { graph.settings.setOnboardingCompleted() }
                    onFinished()
                },
                modifier = Modifier.testTag("onboarding_skip"),
            ) { Text(stringResource(R.string.onboarding_skip)) }
            // The empty Box keeps the middle slot allocated so the row does
            // not reshuffle when Back appears on page two.
            Box {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.testTag("onboarding_back"),
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
            }
            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        scope.launch { graph.settings.setOnboardingCompleted() }
                        onFinished()
                    }
                },
                modifier = Modifier.testTag("onboarding_next"),
            ) {
                Text(
                    if (pagerState.currentPage < 2) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_get_started)
                    },
                )
            }
        }
    }
}
