package com.caregiver.mobile.presentation.summary

import android.content.Intent
import kotlinx.coroutines.launch

import android.os.Build
import android.util.Log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppWarningState
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.BusyBar
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.LockBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.UnclearBox
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import com.caregiver.mobile.ui.component.ChipsRow
import com.caregiver.mobile.ui.component.DefaultDialog
import java.time.LocalDate

/**
 * Summary period picker (board 10): blue info alert + period chips +
 * primary 56dp generate. Local chip state, no VM until generate.
 */
@Composable
fun SummaryPeriodScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    var period by rememberSaveable { mutableIntStateOf(14) }
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            AppTopBar(
                title = stringResource(R.string.summary_title),
                onBack = { navController.popBackStack() },
            )
            InfoAlertCard(
                title = stringResource(R.string.summary_info),
                body = stringResource(R.string.summary_info2),
            )
            SectionTitle(stringResource(R.string.summary_choose_period))
            Text(
                stringResource(R.string.summary_period),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CaregiverColors.Ink,
            )
            // Metrolist-pattern chips row (ui/component/ChipsRow.kt):
            // borderless rounded-16 FilterChips in a horizontally scrolling
            // row; board tint + PlexArabic labels passed through the
            // personalization hooks. The old ✓ prefix is replaced by the
            // M3 selected-chip highlight.
            ChipsRow(
                chips = SummaryViewModel.PERIODS.map { days -> days to periodLabel(days) },
                currentValue = period,
                onValueUpdate = { period = it },
                containerColor = CaregiverColors.Surface,
                textStyle = LocalTextStyle.current.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Bold),
                selectedContainerColor = CaregiverColors.PrimarySoft,
                selectedLabelColor = CaregiverColors.Primary,
            )
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.summary_generate),
                onClick = { navController.navigate("summary-result/$recipientId/$period") },
                height = 56.dp,
                large = true,
            )
        }
    }
}

/**
 * Summary result (board 11, 1480px): red safety alert + overview +
 * important notes + unclear dashed box + meds lock footnote.
 * Trends are data-driven from evidence/uncertainties; no invented values.
 */
@Composable
fun SummaryResultScreen(
    recipientId: String,
    periodDays: Int,
    graph: AppGraph,
    navController: NavController,
) {
    val hasKey = com.caregiver.mobile.ai.AiKeyStore(graph.context).hasKey()
    if (!hasKey) {
        Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding)) {
            AppTopBar(title = stringResource(R.string.summary_title))
            Text(stringResource(R.string.summary_setup_ai))
        }
        return
    }
    // Same effective locale the UI uses for its own copy: the AI summary
    // should speak the language the user chose in the app, not the
    // device's. Configuration.locales/LocaleList require API 24
    // (minSdk is 23); on API 23 the wrapped configuration still carries
    // its locale in the deprecated Configuration.locale.
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val effectiveLocale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            configuration.locale
        }
    val language = effectiveLocale.language
    val vm: SummaryViewModel = assistedViewModel("summary-$recipientId") {
        SummaryViewModel(recipientId, graph.apis, graph.auth, null, periodDays, com.caregiver.mobile.ai.AiKeyStore(graph.context), graph.llmClientFactory, language = language)
    }
    val state by vm.state.collectAsState()
    val exportScope = rememberCoroutineScope()
    val showConsent = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showConsent.value) {
        com.caregiver.mobile.ai.AiConsentDialog(
            onAccept = {
                com.caregiver.mobile.ai.AiKeyStore(graph.context).setConsentGiven()
                showConsent.value = false
                vm.suggestPlan()
            },
            onDismiss = { showConsent.value = false },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding)) {
        when (val s = state) {
            SummaryState.Loading -> {
                Spacer(Modifier.height(16.dp))
                BusyBar(stringResource(R.string.state_summary_loading))
            }
            is SummaryState.AiUnavailable -> Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                val flags = ((s.last?.redFlags ?: emptyList()) + s.redFlags).distinct()
                FlagsBanner(flags)
                AppWarningState(
                    message = stringResource(R.string.state_ai_down),
                    detail = stringResource(R.string.state_ai_down_hint),
                )
                PrimaryButton(
                    label = stringResource(R.string.common_retry),
                    onClick = vm::refresh,
                )
            }
            is SummaryState.Rejected -> Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                s.last?.let { FlagsBanner(it.redFlags) }
                Text(
                    text = stringResource(R.string.summary_error_generic),
                    fontFamily = PlexArabic,
                    color = CaregiverColors.Danger,
                )
                PrimaryButton(
                    label = stringResource(R.string.common_retry),
                    onClick = vm::refresh,
                )
            }
            is SummaryState.NeedsConsent -> {
                com.caregiver.mobile.ai.AiConsentDialog(
                    onAccept = { vm.grantConsent() },
                    onDismiss = { navController.popBackStack() },
                )
            }
            is SummaryState.Content -> SummaryBody(
                graph, s.summary, periodDays, graph.isDemo, navController, s.aiGenerated,
                onSuggestPlan = {
                    val store = com.caregiver.mobile.ai.AiKeyStore(graph.context)
                    if (!store.consentGiven()) {
                        showConsent.value = true
                    } else {
                        vm.suggestPlan()
                    }
                },
                suggesting = vm.suggesting.collectAsState().value,
                suggestedForCurrent = vm.suggestedForCurrent.collectAsState().value,
                suggestStatus = vm.suggestStatus.collectAsState().value,
            )
        }
    }
}

/**
 * Known flags stay visible in every state. No dismiss action exists by
 * construction — the banner is information, not a dialog.
 */
@Composable
private fun FlagsBanner(redFlags: List<String>) {
    if (redFlags.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        redFlags.forEach { flag ->
            SafetyAlertCard(
                title = stringResource(R.string.home_safety_title),
                body = flagText(flag),
            )
        }
    }
}

@Composable
private fun SummaryBody(
    graph: com.caregiver.mobile.AppGraph,
    summary: SummaryDto,
    periodDays: Int,
    isDemo: Boolean,
    navController: NavController,
    aiGenerated: Boolean = false,
    onSuggestPlan: (() -> Unit)? = null,
    suggesting: Boolean = false,
    suggestedForCurrent: Boolean = false,
    suggestStatus: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
    ) {
        AppTopBar(
            title = stringResource(R.string.summary_title),
            onBack = { navController.popBackStack() },
        )
        InfoAlertCard(
            title = stringResource(R.string.plan_not_prescription_title),
            body = stringResource(R.string.plan_not_prescription_body),
        )
        // Shown only when the serving provider is the offline demo backend
        // (see AppGraph.isDemo): marks the text below as illustrative so it
        // is never confused with the source-note evidence. Production
        // summaries never carry this label.
        if (isDemo) {
            InfoAlertCard(
                title = stringResource(R.string.summary_demo_title),
                body = stringResource(R.string.summary_demo_body),
            )
        }
        if (aiGenerated) {
            Text(
                text = stringResource(R.string.ai_generated),
                fontFamily = PlexArabic,
                fontSize = 12.sp,
                color = CaregiverColors.Muted,
            )
        }
        if (summary.redFlags.isNotEmpty()) {
            FlagsBanner(summary.redFlags)
        }
        SectionTitle(stringResource(R.string.summary_overview))
        Text(
            text = if (summary.text.isBlank()) stringResource(R.string.summary_no_supported_observations) else summary.text,
            fontFamily = PlexArabic,
            fontSize = 16.sp,
            lineHeight = 29.sp,
            color = CaregiverColors.Ink,
        )
        if (summary.evidence.isNotEmpty()) {
            SectionTitle(stringResource(R.string.summary_important))
            summary.evidence.forEach { item ->
                AppCard {
                    Text(
                        text = "“${Bidi.isolate(item.quote)}”",
                        fontFamily = PlexArabic,
                        fontSize = 15.sp,
                        color = CaregiverColors.Ink,
                    )
                    GrayPill(stringResource(R.string.summary_source))
                }
            }
        }
        if (summary.uncertainties.isNotEmpty()) {
            SectionTitle(stringResource(R.string.summary_unclear))
            summary.uncertainties.forEach { item ->
                // System-generated topics get localized copy; anything the
                // model invents shows only its detail — never a raw English
                // topic key on an Arabic phone.
                UnclearBox(
                    when (item.topic) {
                        "ai_unavailable" -> stringResource(R.string.ai_unavailable)
                        "invalid_evidence" -> stringResource(R.string.summary_unclear_invalid_evidence)
                        "unverified_claim" -> stringResource(R.string.summary_unclear_unverified)
                        else -> item.detail.ifBlank { item.topic }
                    },
                )
            }
        }
        val exportScope = rememberCoroutineScope()
        var exporting by remember { mutableStateOf(false) }
        androidx.compose.material3.Button(
            onClick = {
                exporting = true
                exportScope.launch {
                    try {
                        val uri = com.caregiver.mobile.data.export.CareSummaryPdfExporter.export(
                            graph.context,
                            summary,
                            summary.recipientId,
                            java.util.Locale.getDefault().toLanguageTag(),
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        graph.context.startActivity(Intent.createChooser(share, null))
                    } catch (e: Exception) {
                        // ignore
                    } finally {
                        exporting = false
                    }
                }
            },
            enabled = !exporting,
        ) {
            Text(if (exporting) "..." else stringResource(R.string.export_pdf))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔒", fontSize = 16.sp)
            Text(
                stringResource(R.string.summary_meds_locked),
                fontFamily = PlexArabic,
                fontSize = 13.sp,
                color = CaregiverColors.Muted,
            )
        }
        // "Share with doctor": only when a real, usable summary exists.
        // The offline demo summary is an illustration, never shareable.
        //
        // Tapping never shares on its own: the tap opens a privacy
        // confirmation first (the summary may contain sensitive care or
        // health information), and only "Continue" opens the system share
        // sheet — exactly once per confirmation (launch guard), with
        // failures degrading silently so the screen can never crash and the
        // summary text can never leak into logs.
        val context = LocalContext.current
        // Effective per-app locale (the LocaleHelper-wrapped configuration),
        // not the process default: the app language may differ from the
        // device language, and the shared message — including its date
        // format — must follow the language the user actually sees.
        // Configuration.locales/LocaleList require API 24 (minSdk is 23);
        // on API 23 the wrapped configuration still carries its locale in
        // the deprecated Configuration.locale.
        val configuration = LocalConfiguration.current
        @Suppress("DEPRECATION")
        val effectiveLocale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.locales[0]
            } else {
                configuration.locale
            }
        val arabic = effectiveLocale.language == "ar"
        val appName = stringResource(R.string.app_name)
        val shareText = remember(summary.text, arabic) {
            CareSummaryShare.shareText(
                summaryText = summary.text,
                dateText = DateFormats.historyDay(LocalDate.now().toString(), arabic),
                appName = appName,
                arabic = arabic,
            )
        }
        var showShareConfirm by rememberSaveable { mutableStateOf(false) }
        var shareLaunched by remember { mutableStateOf(false) }
        if (shareText != null && !isDemo) {
            PrimaryButton(
                label = stringResource(R.string.share_summary_action),
                onClick = {
                    // Re-opening the confirmation rearms the launch guard.
                    shareLaunched = false
                    showShareConfirm = true
                },
                height = 52.dp,
            )
        }
        if (showShareConfirm && shareText != null) {
            DefaultDialog(
                onDismiss = { showShareConfirm = false },
                title = {
                    Text(
                        text = stringResource(R.string.share_summary_confirm_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                buttons = {
                    TextButton(
                        onClick = { showShareConfirm = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            if (!shareLaunched) {
                                shareLaunched = true
                                showShareConfirm = false
                                exportScope.launch {
                                    try {
                                        val uri = com.caregiver.mobile.data.export.CareSummaryPdfExporter.export(
                                            context,
                                            summary,
                                            summary.recipientId,
                                            java.util.Locale.getDefault().toLanguageTag(),
                                        )
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, null))
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.share_summary_confirm_continue))
                    }
                },
            ) {
                Text(stringResource(R.string.share_summary_confirm_body))
            }
        }
        PrimaryButton(
            label = stringResource(R.string.summary_proposals),
            onClick = { navController.navigate(AppDestinations.Plans.base) },
            height = 52.dp,
        )
        if (onSuggestPlan != null) {
            PrimaryButton(
                label = if (suggesting) stringResource(R.string.suggesting) else if (suggestedForCurrent) stringResource(R.string.plan_suggest_done) else stringResource(R.string.suggest_plan_ai),
                onClick = onSuggestPlan,
                enabled = !suggesting && !suggestedForCurrent,
                height = 48.dp,
            )
        }
        suggestStatus?.let { code ->
            val msg = when (code) {
                "success" -> stringResource(R.string.plan_suggest_success)
                "empty_after_filter" -> stringResource(R.string.plan_suggest_empty)
                "parse_error" -> stringResource(R.string.plan_suggest_parse_error)
                "llm_fallback" -> stringResource(R.string.plan_suggest_llm_fallback)
                "medications_rejected" -> stringResource(R.string.plan_suggest_medications_rejected)
                "oversized" -> stringResource(R.string.plan_suggest_oversized)
                "invalid_evidence" -> stringResource(R.string.plan_suggest_invalid_evidence)
                "invalid_action" -> stringResource(R.string.plan_suggest_invalid_action)
                else -> stringResource(R.string.plan_suggest_failed)
            }
            Text(text = msg, color = if (code == "success") CaregiverColors.Success else CaregiverColors.Danger)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun flagText(flag: String): String = when (flag) {
    "FALL_REPORTED" -> stringResource(R.string.flag_fall)
    "HIGH_PAIN" -> stringResource(R.string.flag_pain)
    "REPEATED_MISSED_MED" -> stringResource(R.string.flag_missed_med)
    "MEDICATION_UNVERIFIED" -> stringResource(R.string.flag_med_unverified)
    "POOR_APPETITE_DAYS" -> stringResource(R.string.flag_poor_appetite)
    else -> {
        Log.w("SummaryScreen", "Unknown safety flag: $flag")
        stringResource(R.string.flag_unknown)
    }
}

@Composable
private fun periodLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.summary_days_7
        14 -> R.string.summary_days_14
        else -> R.string.summary_days_30
    },
)
