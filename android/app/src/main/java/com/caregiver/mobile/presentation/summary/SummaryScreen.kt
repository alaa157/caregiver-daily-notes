package com.caregiver.mobile.presentation.summary

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiConsentDialog
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.SummaryWindow
import com.caregiver.mobile.ai.TrendDto
import com.caregiver.mobile.ai.dataUseNotice
import com.caregiver.mobile.ai.stringRes
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AiFailureSource
import com.caregiver.mobile.presentation.common.AiRecovery
import com.caregiver.mobile.presentation.common.AiRecoveryAction
import com.caregiver.mobile.presentation.common.AiRecoveryCopy
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.core.util.appLocale
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.export.CareSummaryPdfExporter
import com.caregiver.mobile.data.summary.SavedSummaryStore
import com.caregiver.mobile.presentation.history.HistoryDates
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
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.UnclearBox
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.common.resolvedMessage
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import com.caregiver.mobile.ui.component.ChipsRow
import com.caregiver.mobile.ui.component.DefaultDialog
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Summary period picker (board 10): blue info alert + period chips +
 * primary 56dp generate. Local chip state, no VM until generate.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SummaryPeriodScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    var period by rememberSaveable { mutableIntStateOf(14) }
    var endDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val arabic = appLocale().language == "ar"
    // The newest saved summary, if any: reopening it costs no provider call.
    // Demo graphs have no store, so the card simply never appears there.
    val savedStore = graph.savedSummaryStore
    var latestSaved by remember { mutableStateOf<SavedSummaryStore.SavedSummaryItem?>(null) }
    LaunchedEffect(recipientId, savedStore) {
        latestSaved = savedStore?.latest(recipientId)
    }
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
            SecondaryButton(
                label = stringResource(
                    R.string.summary_end_date,
                    Bidi.isolate(DateFormats.historyDay(endDate, arabic)),
                ),
                onClick = { showDatePicker = true },
                modifier = Modifier.testTag("summary_end_date_button"),
            )
            latestSaved?.let { item ->
                AppCard {
                    Text(
                        text = stringResource(R.string.summary_latest_saved),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CaregiverColors.Ink,
                    )
                    Text(
                        text = "${DateFormats.historyDay(
                            Instant.ofEpochMilli(item.createdAt)
                                .atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                            arabic,
                        )} · ${periodLabel(item.periodDays)}",
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                }
                SecondaryButton(
                    label = stringResource(R.string.summary_open_saved),
                    onClick = {
                        navController.navigate(
                            AppRoutes.summaryResult(recipientId, item.periodDays, saved = item.id),
                        )
                    },
                    modifier = Modifier.testTag("summary_open_saved"),
                )
            }
            SecondaryButton(
                label = stringResource(R.string.summary_saved_all),
                onClick = { navController.navigate(AppRoutes.savedSummaries(recipientId)) },
                modifier = Modifier.testTag("summary_saved_all"),
            )
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.summary_generate),
                onClick = {
                    navController.navigate("summary-result/$recipientId/$period?end=$endDate")
                },
                height = 56.dp,
                large = true,
            )
        }
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = HistoryDates.toPickerMillis(LocalDate.parse(endDate)),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !HistoryDates.fromPickerMillis(utcTimeMillis).isAfter(today)

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { selected ->
                            endDate = HistoryDates.fromPickerMillis(selected).toString()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.common_ok), fontFamily = PlexArabic)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel), fontFamily = PlexArabic)
                }
            },
        ) {
            DatePicker(picker)
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
    end: String? = null,
    saved: String? = null,
) {
    val initialEnd = parseEndArg(end, LocalDate.now())
    // No key is not an error state. [SummaryViewModel] falls back to the
    // backend (or the local deterministic summary) and still returns the
    // safety flags; short-circuiting here hid those flags behind a setup
    // message. An unreadable/invalidated key follows the same fallback, and
    // the failure/setup copy on the summary itself explains the next step.
    //
    // Same effective locale the UI uses for its own copy: the AI summary
    // should speak the language the user chose in the app, not the device's.
    val effectiveLocale = appLocale()
    val language = effectiveLocale.language
    val vm: SummaryViewModel = assistedViewModel("summary-$recipientId") {
        SummaryViewModel(
            recipientId,
            graph.apis,
            graph.auth,
            null,
            periodDays,
            AiKeyStore(graph.context, graph.activeAccountIdFlow),
            graph.llmClientFactory,
            language = language,
            initialEnd = initialEnd,
            savedStore = graph.savedSummaryStore,
            initialSavedId = saved,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val savedId by vm.savedId.collectAsStateWithLifecycle()
    val showingSaved by vm.showingSaved.collectAsStateWithLifecycle()
    val activePeriodDays by vm.period.collectAsStateWithLifecycle()
    val onRecoveryAction: (AiRecoveryAction) -> Unit = { action ->
        when (action) {
            AiRecoveryAction.Retry -> vm.refresh()
            AiRecoveryAction.OpenAiSettings ->
                navController.navigate(AppDestinations.AiSettings.base)
            AiRecoveryAction.AddNote ->
                navController.navigate(AppRoutes.noteEditor(recipientId))
            AiRecoveryAction.ExtendPeriod ->
                vm.setPeriod(AiRecovery.EXTENDED_PERIOD_DAYS)
            // Handled by the AiUnavailable branch's local handler, which
            // knows the provider to switch to; no-op here.
            AiRecoveryAction.SwitchToProvider -> Unit
        }
    }
    val consentProvider = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<AiProvider?>(null)
    }
    consentProvider.value?.let { provider ->
        val model = kotlinx.coroutines.runBlocking { AiKeyStore(graph.context, graph.activeAccountIdFlow).model(provider) } ?: provider.defaultModel
        AiConsentDialog(
            providerName = provider.displayName,
            dataUseNoticeRes = AiModelCatalog.dataUseNotice(provider, model)?.stringRes(),
            onAccept = {
                kotlinx.coroutines.runBlocking { AiKeyStore(graph.context, graph.activeAccountIdFlow).setConsentGiven(provider) }
                consentProvider.value = null
                vm.suggestPlan()
            },
            onDismiss = { consentProvider.value = null },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenHPadding)) {
        if (state !is SummaryState.Content) {
            AppTopBar(
                title = stringResource(R.string.summary_title),
                onBack = { navController.popBackStack() },
            )
        }
        when (val s = state) {
            SummaryState.Loading -> {
                Spacer(Modifier.height(16.dp))
                BusyBar(stringResource(R.string.state_summary_loading))
                Spacer(Modifier.height(12.dp))
                // The user can always stop waiting: bounded generation means
                // an escape hatch visible in the same place as the spinner.
                Button(
                    onClick = vm::cancelGeneration,
                    modifier = Modifier.fillMaxWidth().testTag("summary_cancel"),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
            is SummaryState.AiUnavailable -> Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                val flags = ((s.last?.summary?.redFlags ?: emptyList()) + s.redFlags).distinct()
                FlagsBanner(flags)
                val otherProvider = s.switchToProvider
                val onAiAction: (AiRecoveryAction) -> Unit = { action ->
                    if (action == AiRecoveryAction.SwitchToProvider && otherProvider != null) {
                        vm.switchProvider(otherProvider)
                    } else {
                        onRecoveryAction(action)
                    }
                }
                SummaryFailureCard(
                    copy = AiRecovery.copyFor(
                        s.reasonCode,
                        activePeriodDays,
                        otherProviderName = otherProvider?.displayName,
                    ),
                    onAction = onAiAction,
                )
                // The PDF is useful even when generation failed (flags,
                // recorded observations, disclaimer) — offer it below the
                // failure card whenever there is something to export.
                if (summaryExportVisible(s.reasonCode, flags, s.last?.summary)) {
                    SummaryExportActions(
                        graph = graph,
                        summary = exportDtoFor(recipientId, activePeriodDays, flags, s.last?.summary),
                        periodDays = activePeriodDays,
                        isDemo = graph.isDemo,
                    )
                }
                PreviousSummaryResult(s.last, s.reasonCode, navController)
            }
            is SummaryState.Rejected -> Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                val flags = s.last?.summary?.redFlags ?: emptyList()
                s.last?.let { FlagsBanner(it.summary.redFlags) }
                SummaryFailureCard(
                    copy = AiRecovery.copyFor(
                        s.code,
                        activePeriodDays,
                        AiFailureSource.Service,
                    ),
                    onAction = onRecoveryAction,
                )
                if (summaryExportVisible(s.code, flags, s.last?.summary)) {
                    SummaryExportActions(
                        graph = graph,
                        summary = exportDtoFor(recipientId, activePeriodDays, flags, s.last?.summary),
                        periodDays = activePeriodDays,
                        isDemo = graph.isDemo,
                    )
                }
                PreviousSummaryResult(s.last, s.code, navController)
            }
            is SummaryState.NeedsConsent -> {
                val consentStore = AiKeyStore(graph.context, graph.activeAccountIdFlow)
                val consentModel = kotlinx.coroutines.runBlocking { consentStore.model(s.provider) } ?: s.provider.defaultModel
                AiConsentDialog(
                    providerName = s.provider.displayName,
                    modelName = consentModel,
                    dataUseNoticeRes = AiModelCatalog.dataUseNotice(s.provider, consentModel)?.stringRes(),
                    onAccept = { vm.grantConsent() },
                    onDismiss = { navController.popBackStack() },
                )
            }
            is SummaryState.Content -> SummaryBody(
                graph, s.data, activePeriodDays, graph.isDemo, navController, s.aiGenerated,
                savedId = savedId,
                showingSaved = showingSaved,
                onGenerateNew = vm::refresh,
                onSuggestPlan = {
                    kotlinx.coroutines.runBlocking {
                        val store = AiKeyStore(graph.context, graph.activeAccountIdFlow)
                        val provider = store.selectedProvider()
                        if (!store.consentGiven(provider)) {
                            consentProvider.value = provider
                        } else {
                            vm.suggestPlan()
                        }
                    }
                },
                suggesting = vm.suggesting.collectAsStateWithLifecycle().value,
                suggestedForCurrent = vm.suggestedForCurrent.collectAsStateWithLifecycle().value,
                suggestStatus = vm.suggestStatus.collectAsStateWithLifecycle().value,
            )
        }
    }
}

internal fun parseEndArg(raw: String?, today: LocalDate): LocalDate =
    raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.takeUnless { it.isAfter(today) }
        ?: today

/**
 * One recovery step per failure: plain-language cause first, then the
 * smallest set of buttons that can actually fix it. Raw reason codes never
 * reach this card.
 */
@Composable
private fun SummaryFailureCard(
    copy: AiRecoveryCopy,
    onAction: (AiRecoveryAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm)) {
        AppWarningState(message = copy.resolvedMessage())
        copy.primary?.let { action ->
            PrimaryButton(
                label = recoveryActionLabel(action, copy.switchProviderName),
                onClick = { onAction(action) },
            )
        }
        copy.secondary?.let { action ->
            SecondaryButton(
                label = recoveryActionLabel(action, copy.switchProviderName),
                onClick = { onAction(action) },
            )
        }
    }
}

@Composable
private fun recoveryActionLabel(action: AiRecoveryAction, switchProviderName: String? = null): String = when (action) {
    AiRecoveryAction.Retry -> stringResource(R.string.common_retry)
    AiRecoveryAction.OpenAiSettings -> stringResource(R.string.ai_action_open_settings)
    AiRecoveryAction.AddNote -> stringResource(R.string.ai_action_add_note)
    AiRecoveryAction.ExtendPeriod -> stringResource(R.string.ai_action_try_30_days)
    AiRecoveryAction.SwitchToProvider ->
        if (switchProviderName != null) {
            stringResource(R.string.ai_action_switch_provider, switchProviderName)
        } else {
            stringResource(R.string.ai_action_switch_provider_generic)
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

/**
 * Observation cards replace the overview paragraph and the flat quote list
 * only when every evidence item carries a claim — a quote without its claim
 * would leave the caregiver guessing what it supports.
 */
internal fun usesObservationCards(evidence: List<SummaryDto.EvidenceDto>): Boolean =
    evidence.isNotEmpty() && evidence.all { it.claim.isNotBlank() }

@Composable
private fun SummaryResultContent(
    summary: SummaryDto,
    trends: List<TrendDto>,
    textDirection: TextDirection,
    navController: NavController,
) {
    TrendsSection(trends)
    if (usesObservationCards(summary.evidence)) {
        summary.evidence.forEachIndexed { index, item ->
            AppCard(Modifier.testTag("summary_observation_$index")) {
                Text(
                    text = item.claim,
                    style = TextStyle(textDirection = textDirection),
                    fontFamily = PlexArabic,
                    fontSize = 16.sp,
                    color = CaregiverColors.Ink,
                )
                Text(
                    text = "“${Bidi.isolate(item.quote)}”",
                    fontFamily = PlexArabic,
                    fontSize = 15.sp,
                    color = CaregiverColors.Ink,
                )
                TextButton(
                    onClick = { navController.navigate(AppRoutes.noteDetail(item.noteId)) },
                ) {
                    Text(stringResource(R.string.summary_open_source))
                }
            }
        }
    } else {
        SectionTitle(stringResource(R.string.summary_overview))
        Text(
            text = if (summary.text.isBlank()) {
                stringResource(R.string.summary_no_supported_observations)
            } else {
                summary.text
            },
            style = TextStyle(textDirection = textDirection),
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
    }
}

@Composable
private fun PreviousSummaryResult(
    last: SummaryResultData?,
    failureCode: String?,
    navController: NavController,
) {
    val data = last ?: return
    if (
        failureCode == "no_source_data" ||
        (data.summary.text.isBlank() && data.summary.evidence.isEmpty())
    ) {
        return
    }

    val textDirection = if (appLocale().language == "ar") TextDirection.Rtl else TextDirection.Ltr
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm)) {
        SectionTitle(stringResource(R.string.summary_previous_result))
        Text(
            text = stringResource(R.string.summary_previous_result_note),
            fontFamily = PlexArabic,
            fontSize = 12.sp,
            color = CaregiverColors.Muted,
        )
        SummaryResultContent(data.summary, data.trends, textDirection, navController)
    }
}

@Composable
private fun TrendsSection(trends: List<TrendDto>) {
    if (trends.none { it.previousSampleSize > 0 || it.currentSampleSize > 0 }) {
        return
    }

    val locale = appLocale()
    val numberFormat = remember(locale) { NumberFormat.getInstance(locale) }
    val countTemplate = stringResource(R.string.summary_trend_count)

    SectionTitle(stringResource(R.string.summary_trends))
    trends.forEach { trend ->
        val metricLabelRes = when (trend.metric) {
            "FALL_COUNT" -> R.string.summary_trend_fall
            "HIGH_PAIN_COUNT" -> R.string.summary_trend_pain
            "MISSED_MED_COUNT" -> R.string.summary_trend_missed_med
            "POOR_APPETITE_COUNT" -> R.string.summary_trend_poor_appetite
            else -> null
        } ?: return@forEach
        val metricLabel = stringResource(metricLabelRes)

        AppCard {
            Text(
                text = metricLabel,
                style = MaterialTheme.typography.titleMedium,
                color = CaregiverColors.Ink,
            )
            TrendPeriodRow(
                metricLabel = metricLabel,
                labelRes = R.string.summary_trend_previous,
                count = trend.previousCount,
                sampleSize = trend.previousSampleSize,
                previousCount = trend.previousCount,
                currentCount = trend.currentCount,
                countTemplate = countTemplate,
                numberFormat = numberFormat,
            )
            TrendPeriodRow(
                metricLabel = metricLabel,
                labelRes = R.string.summary_trend_current,
                count = trend.currentCount,
                sampleSize = trend.currentSampleSize,
                previousCount = trend.previousCount,
                currentCount = trend.currentCount,
                countTemplate = countTemplate,
                numberFormat = numberFormat,
            )
            if (trend.previousSampleSize > 0 && trend.currentSampleSize > 0) {
                val delta = if (trend.delta > 0) {
                    "+${numberFormat.format(trend.delta)}"
                } else {
                    numberFormat.format(trend.delta)
                }
                Text(
                    text = stringResource(R.string.summary_trend_delta, Bidi.isolate(delta)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CaregiverColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun TrendPeriodRow(
    metricLabel: String,
    labelRes: Int,
    count: Int,
    sampleSize: Int,
    previousCount: Int,
    currentCount: Int,
    countTemplate: String,
    numberFormat: NumberFormat,
) {
    val periodLabel = stringResource(labelRes)
    val valueText = if (sampleSize > 0) {
        // Keep the resource's integer placeholders while applying the app locale's number format.
        countTemplate
            .replace("%1\$d", Bidi.isolate(numberFormat.format(count)))
            .replace("%2\$d", Bidi.isolate(numberFormat.format(sampleSize)))
    } else {
        stringResource(R.string.summary_trend_no_data)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$metricLabel, $periodLabel, $valueText"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = CaregiverColors.Ink,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.Muted,
            )
        }
        if (sampleSize > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(CaregiverColors.PrimarySoft)
                    .clearAndSetSemantics { },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(trendBarFraction(count, previousCount, currentCount))
                        .fillMaxHeight()
                        .background(CaregiverColors.Primary),
                )
            }
        }
    }
}

internal fun trendBarFraction(count: Int, previousCount: Int, currentCount: Int): Float =
    count.toFloat() / maxOf(previousCount, currentCount, 1)

@Composable
private fun SummaryBody(
    graph: com.caregiver.mobile.AppGraph,
    data: SummaryResultData,
    periodDays: Int,
    isDemo: Boolean,
    navController: NavController,
    aiGenerated: Boolean = false,
    savedId: String? = null,
    showingSaved: Boolean = false,
    onGenerateNew: (() -> Unit)? = null,
    onSuggestPlan: (() -> Unit)? = null,
    suggesting: Boolean = false,
    suggestedForCurrent: Boolean = false,
    suggestStatus: String? = null,
) {
    // Effective per-app locale (the LocaleHelper-wrapped configuration), not
    // the process default: the UI text uses the language the user actually
    // sees. Export/share below resolve their own locale via appLocale().
    val textDirection =
        if (appLocale().language == "ar") TextDirection.Rtl else TextDirection.Ltr
    val summary = data.summary

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
        if (aiGenerated && data.provider != null && data.model != null) {
            // Provenance only: the provider's public name and the model id.
            // The API key is never part of SummaryResultData and cannot leak.
            val providerName = AiProvider.entries
                .firstOrNull { it.name == data.provider }
                ?.displayName ?: data.provider
            Text(
                text = stringResource(
                    R.string.summary_generated_with,
                    providerName,
                    Bidi.isolate(data.model),
                ),
                fontFamily = PlexArabic,
                fontSize = 12.sp,
                color = CaregiverColors.Muted,
            )
        }
        if (savedId != null) {
            Text(
                text = stringResource(R.string.summary_saved_note),
                fontFamily = PlexArabic,
                fontSize = 12.sp,
                color = CaregiverColors.Muted,
            )
        }
        if (showingSaved) {
            val arabic = textDirection == TextDirection.Rtl
            val savedDate = data.generatedAt?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            }
            if (savedDate != null) {
                Text(
                    text = stringResource(
                        R.string.summary_saved_from,
                        Bidi.isolate(DateFormats.historyDay(savedDate, arabic)),
                    ),
                    fontFamily = PlexArabic,
                    fontSize = 12.sp,
                    color = CaregiverColors.Muted,
                )
            }
            if (onGenerateNew != null) {
                PrimaryButton(
                    label = stringResource(R.string.summary_generate_new),
                    onClick = onGenerateNew,
                    height = 48.dp,
                    modifier = Modifier.testTag("summary_generate_new"),
                )
            }
        }
        val periodStart = data.periodStart
        val periodEnd = data.periodEnd
        if (periodStart != null && periodEnd != null) {
            Text(
                text = stringResource(
                    R.string.summary_coverage,
                    Bidi.isolate(periodStart),
                    Bidi.isolate(periodEnd),
                    periodDays,
                ),
                fontFamily = PlexArabic,
                fontSize = 12.sp,
                color = CaregiverColors.Muted,
            )
        }
        if (summary.redFlags.isNotEmpty()) {
            FlagsBanner(summary.redFlags)
        }
        SummaryResultContent(summary, data.trends, textDirection, navController)
        if (summary.uncertainties.isNotEmpty()) {
            SectionTitle(stringResource(R.string.summary_unclear))
            summary.uncertainties.forEach { item ->
                // System-generated topics get localized copy; anything the
                // model invents shows only its detail — never a raw English
                // topic key on an Arabic phone.
                UnclearBox(
                    when (item.topic) {
                        "ai_unavailable" -> stringResource(R.string.ai_unavailable)
                        "ai_not_configured" -> stringResource(R.string.summary_ai_not_configured)
                        "invalid_evidence" -> stringResource(R.string.summary_unclear_invalid_evidence)
                        "unverified_claim" -> stringResource(R.string.summary_unclear_unverified)
                        else -> Bidi.isolate(item.detail.ifBlank { item.topic })
                    },
                )
            }
        }
        // Export/share: ONE implementation shared with the failure states
        // (SummaryExportActions). The PDF carries the overview, care flags,
        // evidence, recorded observations, uncertainties and disclaimer, so
        // it remains useful even when the generated overview text is empty.
        SummaryExportActions(
            graph = graph,
            summary = summary,
            periodDays = periodDays,
            isDemo = isDemo,
        )

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
            // Provider failures keep their actual reason (invalid_key,
            // rate_limit, network, timeout, ...) and reuse the summary
            // recovery copy; only plan-specific codes use the plan strings.
            // Unknown codes fall back to the plan generic, never raw text.
            val msg = when (code) {
                "success" -> stringResource(R.string.plan_suggest_success)
                "empty_after_filter" -> stringResource(R.string.plan_suggest_empty)
                "parse_error" -> stringResource(R.string.plan_suggest_parse_error)
                "llm_fallback" -> stringResource(R.string.plan_suggest_llm_fallback)
                "medications_rejected" -> stringResource(R.string.plan_suggest_medications_rejected)
                "oversized" -> stringResource(R.string.plan_suggest_oversized)
                "invalid_evidence" -> stringResource(R.string.plan_suggest_invalid_evidence)
                "invalid_action" -> stringResource(R.string.plan_suggest_invalid_action)
                "invalid_key", "invalid_model", "rate_limit", "network", "timeout",
                "malformed_output", "no_key", "missing_key", "not_configured",
                "invalid_url", "blocked", "blocked_safety", "invalid_request",
                "json_mode_unsupported", "empty_output", "truncated", "incomplete",
                -> AiRecovery.copyFor(code).resolvedMessage()
                else -> if (code.startsWith("http_")) {
                    AiRecovery.copyFor(code).resolvedMessage()
                } else {
                    stringResource(R.string.plan_suggest_failed)
                }
            }
            Text(text = msg, color = if (code == "success") CaregiverColors.Success else CaregiverColors.Danger)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Export/share actions for the summary PDF, shared by the Content state
 * and the failure states. ONE set of state variables, ONE locale/share-text
 * computation based on the effective app locale ([appLocale]).
 *
 * The PDF carries the overview, care flags, evidence, recorded
 * observations, uncertainties and disclaimer, so it remains useful even
 * when the generated overview text is empty. Only illustrative demo data
 * is excluded from sharing.
 *
 * Tapping never shares on its own: the tap opens a privacy confirmation
 * first (the summary may contain sensitive care or health information), and
 * only "Continue" opens the system share sheet — exactly once per
 * confirmation (launch guard), with failures degrading silently so the
 * screen can never crash and the summary text can never leak into logs.
 */
@Composable
private fun SummaryExportActions(
    graph: AppGraph,
    summary: SummaryDto,
    periodDays: Int,
    isDemo: Boolean,
) {
    val context = LocalContext.current
    val effectiveLocale = appLocale()
    val arabic = effectiveLocale.language == "ar"
    val appName = stringResource(R.string.app_name)
    val shareTemplate = stringResource(R.string.share_summary_message)
    val shareText = remember(summary.text, arabic, appName, shareTemplate) {
        CareSummaryShare.shareText(
            summaryText = summary.text,
            dateText = DateFormats.historyDay(LocalDate.now().toString(), arabic),
            appName = appName,
            template = shareTemplate,
        )
    }

    val exportScope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var showShareConfirm by rememberSaveable { mutableStateOf(false) }
    var shareLaunched by remember { mutableStateOf(false) }

    val pdfLocaleTag = effectiveLocale.toLanguageTag()
    androidx.compose.material3.Button(
        onClick = {
            exportError = null
            exporting = true
            exportScope.launch {
                try {
                    val shared = shareSummaryPdf(
                        context = context,
                        graph = graph,
                        summary = summary,
                        periodDays = periodDays,
                        languageTag = pdfLocaleTag,
                        shareText = null,
                    )
                    if (!shared) {
                        exportError = context.getString(R.string.export_failed)
                    }
                } catch (e: CancellationException) {
                    // The screen went away mid-export: let the scope cancel.
                    throw e
                } catch (e: Exception) {
                    Log.e("SummaryScreen", "PDF export failed: ${e.javaClass.name}")
                    exportError = context.getString(R.string.export_failed)
                } finally {
                    exporting = false
                }
            }
        },
        enabled = !exporting,
    ) {
        Text(if (exporting) stringResource(R.string.exporting_pdf) else stringResource(R.string.export_pdf))
    }
    // "Share with doctor": only when a real, usable summary exists.
    // The offline demo summary is an illustration, never shareable.
    if (!isDemo) {
        PrimaryButton(
            label = stringResource(R.string.share_summary_action),
            onClick = {
                // Re-opening the confirmation rearms the launch guard.
                shareLaunched = false
                showShareConfirm = true
            },
            enabled = !exporting,
            height = 52.dp,
        )
    }
    exportError?.let { Text(it, color = CaregiverColors.Danger) }
    if (showShareConfirm) {
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
                            exportError = null
                            exporting = true
                            exportScope.launch {
                                try {
                                    val shared = shareSummaryPdf(
                                        context = context,
                                        graph = graph,
                                        summary = summary,
                                        periodDays = periodDays,
                                        languageTag = pdfLocaleTag,
                                        shareText = shareText,
                                    )
                                    if (!shared) {
                                        exportError = context.getString(R.string.export_failed)
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("SummaryScreen", "PDF share failed: ${e.javaClass.name}")
                                    exportError = context.getString(R.string.export_failed)
                                } finally {
                                    exporting = false
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
}

/**
 * Whether the export actions make sense for a failure state. Export is
 * offered when there is something meaningful in the PDF — care flags, or
 * a previous summary (so recorded notes exist) — and never when the
 * failure itself is "there is nothing to export" (`no_source_data`).
 * Internal for unit tests.
 */
internal fun summaryExportVisible(
    reasonCode: String?,
    redFlags: List<String>,
    last: SummaryDto?,
): Boolean =
    reasonCode != "no_source_data" && (redFlags.isNotEmpty() || last != null)

/**
 * Builds the summary carried by a failed state: the last good summary
 * enriched with the current flags when one exists, otherwise a bare
 * flags-only DTO so the PDF still contains care flags. Internal for
 * unit tests.
 */
internal fun exportDtoFor(
    recipientId: String,
    periodDays: Int,
    redFlags: List<String>,
    last: SummaryDto?,
): SummaryDto =
    last?.copy(redFlags = redFlags) ?: SummaryDto(
        id = "",
        recipientId = recipientId,
        periodDays = periodDays,
        text = "",
        redFlags = redFlags,
        evidence = emptyList(),
        uncertainties = emptyList(),
    )
/**
 * Builds the summary PDF off the main thread, then opens the system chooser
 * with an Activity context when one is available. Returns false when the
 * chooser could not be opened; the caller surfaces the localized error.
 * The notes/recipient fetch and render both run on Dispatchers.IO.
 */
private suspend fun shareSummaryPdf(
    context: Context,
    graph: AppGraph,
    summary: SummaryDto,
    periodDays: Int,
    languageTag: String,
    shareText: String?,
): Boolean {
    val uri = withContext(Dispatchers.IO) {
        CareSummaryPdfExporter.prepareUri(
            context,
            graph,
            summary,
            periodDays,
            languageTag,
        )
    }
    return CareSummaryShare.launchChooser(
        context,
        CareSummaryShare.pdfShareIntent(uri, shareText),
    )
}

@Composable
private fun flagText(flag: String): String = when (flag) {
    "FALL_REPORTED" -> stringResource(R.string.flag_fall)
    "HIGH_PAIN" -> stringResource(R.string.flag_pain)
    "BREATHING_TROUBLE" -> stringResource(R.string.flag_breathing_trouble)
    "LOSS_OF_CONSCIOUSNESS" -> stringResource(R.string.flag_loss_of_consciousness)
    "REPEATED_MISSED_MED" -> stringResource(R.string.flag_missed_med)
    "MEDICATION_UNVERIFIED" -> stringResource(R.string.flag_med_unverified)
    "POOR_APPETITE_DAYS" -> stringResource(R.string.flag_poor_appetite)
    "MODEL_URGENT" -> stringResource(R.string.flag_model_urgent)
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
