package com.caregiver.mobile.presentation.settings

import android.content.Context
import android.util.Log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.ApiKeyState
import com.caregiver.mobile.ai.AiFreeTier
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.AiConsentDialog
import com.caregiver.mobile.ai.AnthropicLlmClient
import com.caregiver.mobile.ai.GeminiLlmClient
import com.caregiver.mobile.ai.GroundingProbe
import com.caregiver.mobile.ai.GroundingProbeStatus
import com.caregiver.mobile.ai.LlmClient
import com.caregiver.mobile.ai.LlmRequest
import com.caregiver.mobile.ai.LlmStatus
import com.caregiver.mobile.ai.OpenAiCompatibleLlmClient
import com.caregiver.mobile.ai.PromptNote
import com.caregiver.mobile.ai.PromptNotes
import com.caregiver.mobile.ai.Prompts
import com.caregiver.mobile.ai.SummaryResponseFormat
import com.caregiver.mobile.ai.dataUseNotice
import com.caregiver.mobile.ai.isCatalogModel
import com.caregiver.mobile.ai.stringRes
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.markUnsavedChanges
import com.caregiver.mobile.presentation.common.AiRecovery
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.CaregiverSettingsGroup
import com.caregiver.mobile.presentation.common.CaregiverSettingsRow
import com.caregiver.mobile.presentation.common.resolvedMessage
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun AiSettingsScreen(graph: AppGraph, navController: NavController, navEntry: NavBackStackEntry) {
    val scope = rememberCoroutineScope()
    val store = remember { AiKeyStore(graph.context) }
    val uriHandler = LocalUriHandler.current
    // The activity context carries the language the caregiver just picked;
    // `graph.context` is the application context, which keeps the locale the
    // process started with.
    val context = LocalContext.current
    val initialProvider = store.selectedProvider()
    val initialStoredKey = store.apiKey(initialProvider).orEmpty()
    var provider by remember { mutableStateOf(initialProvider) }
    var apiKey by remember { mutableStateOf(initialStoredKey) }
    var savedApiKey by remember { mutableStateOf(initialStoredKey) }
    // A pasted key usually carries a trailing space or newline. The tests
    // and the save have to use the same trimmed value, or a key that works
    // fine after saving fails the connection test.
    val trimmedKey = apiKey.trim()
    // A key that can no longer be decrypted (a lock-screen change invalidates
    // the keystore key) is not "no key": the caregiver has to enter it again.
    var keyState by remember { mutableStateOf(store.apiKeyState(initialProvider)) }
    var keyError by remember { mutableStateOf<String?>(null) }
    val initialStoredModel = store.model(initialProvider).orEmpty()
    var model by remember { mutableStateOf(initialStoredModel.ifBlank { initialProvider.defaultModel }) }
    var savedModel by remember { mutableStateOf(initialStoredModel) }
    val initialStoredBaseUrl = store.baseUrl(initialProvider).orEmpty()
    var baseUrl by remember { mutableStateOf(initialStoredBaseUrl.ifBlank { initialProvider.defaultBaseUrl }) }
    var savedBaseUrl by remember { mutableStateOf(initialStoredBaseUrl) }
    val modelInvalid = !AiSettingsValidation.isAcceptableModelId(model)
    val customBaseUrl = AiSettingsValidation.normalizedBaseUrl(baseUrl, provider).isNotEmpty()
    var systemPrompt by remember { mutableStateOf(store.systemPrompt()) }
    var summaryPrompt by remember { mutableStateOf(store.summarySystemPrompt()) }
    var planPrompt by remember { mutableStateOf(store.planSystemPrompt()) }
    val savedSystemPrompt = remember { store.systemPrompt() }
    val savedSummaryPrompt = remember { store.summarySystemPrompt() }
    val savedPlanPrompt = remember { store.planSystemPrompt() }
    // One "Check setup" run reports each stage's outcome; the stages share
    // a single job so they always run in order.
    var setupState by remember { mutableStateOf(pendingSetupCheckState()) }
    var setupJob by remember { mutableStateOf<Job?>(null) }
    // Consent is per provider: switching provider must show that provider's
    // own state, not the previous one's.
    var consentGiven by remember(provider) { mutableStateOf(store.consentGiven(provider)) }
    var consentStatus by remember { mutableStateOf<String?>(null) }

    // Dialog visibility
    var showProviderDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showSummaryPromptDialog by remember { mutableStateOf(false) }
    var showPlanPromptDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showRevokeConsentDialog by remember { mutableStateOf(false) }
    var pendingProvider by remember { mutableStateOf<AiProvider?>(null) }
    var guideLinkError by remember { mutableStateOf(false) }

    val hasUnsavedChanges =
        apiKey.trim() != savedApiKey.trim() ||
            AiSettingsValidation.normalizedModel(model, provider) !=
            AiSettingsValidation.normalizedModel(savedModel, provider) ||
            AiSettingsValidation.normalizedBaseUrl(baseUrl, provider) !=
            AiSettingsValidation.normalizedBaseUrl(savedBaseUrl, provider) ||
            systemPrompt != savedSystemPrompt ||
            summaryPrompt != savedSummaryPrompt ||
            planPrompt != savedPlanPrompt

    // The tab bar is outside this screen. Publish the dirty flag on the
    // screen's back-stack entry so a tab tap can ask before popping the
    // screen and throwing the edits away.
    SideEffect {
        navEntry.markUnsavedChanges(hasUnsavedChanges)
    }

    fun loadProvider(target: AiProvider) {
        provider = target
        val storedKey = store.apiKey(target).orEmpty()
        apiKey = storedKey
        savedApiKey = storedKey
        keyState = store.apiKeyState(target)
        val storedModel = store.model(target).orEmpty()
        model = storedModel.ifBlank { target.defaultModel }
        savedModel = storedModel
        val storedBaseUrl = store.baseUrl(target).orEmpty()
        baseUrl = storedBaseUrl.ifBlank { target.defaultBaseUrl }
        savedBaseUrl = storedBaseUrl
        keyError = null
        setupJob?.cancel()
        setupJob = null
        setupState = pendingSetupCheckState()
        consentStatus = null
        showRevokeConsentDialog = false
        guideLinkError = false
    }

    /**
     * Stores the credentials the caregiver is working with: the key in the
     * provider's own slot, the provider itself, the model and the base URL.
     *
     * The key is written before the provider is selected, so a refused
     * keystore write can never leave the app pointing at a provider whose
     * key was never saved. Returns false when the keystore refused the key.
     */
    fun persistCredentials(): Boolean {
        val credentials = AiSettingsValidation.credentialsFor(provider, trimmedKey, model, baseUrl)
        if (credentials == null) {
            // Emptying a saved key is an explicit removal; leaving the field
            // untouched keeps the key that is already stored.
            if (savedApiKey.isNotBlank()) store.clearApiKey(provider)
        } else if (!store.setApiKey(credentials.apiKey, credentials.provider)) {
            return false
        }
        store.setProvider(provider)
        store.setModel(AiSettingsValidation.normalizedModel(model, provider), provider)
        store.setBaseUrl(AiSettingsValidation.normalizedBaseUrl(baseUrl, provider), provider)
        return true
    }

    fun requestExit() {
        if (hasUnsavedChanges) {
            pendingProvider = null
            showDiscardDialog = true
        } else {
            navController.popBackStack()
        }
    }

    fun discardAndContinue() {
        val target = pendingProvider
        showDiscardDialog = false
        pendingProvider = null
        systemPrompt = savedSystemPrompt
        summaryPrompt = savedSummaryPrompt
        planPrompt = savedPlanPrompt
        if (target == null) {
            navController.popBackStack()
        } else {
            loadProvider(target)
        }
    }

    /**
     * Runs one stage of "Check setup" and reports its outcome. Every stage
     * is bounded by the same twenty-second budget; a stage that needs the
     * provider's consent reports [StageOutcome.NeedsConsent] instead of
     * sending anything.
     */
    suspend fun runSetupStage(stage: SetupStage): StageOutcome {
        return try {
            when (stage) {
                SetupStage.Connection -> withTimeout(TEST_TIMEOUT_SECONDS * 1_000L) {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        trimmedKey,
                    )
                    // A ping that has not answered in twenty seconds is not
                    // going to answer. The probe asks for a tiny JSON object
                    // in native JSON mode, so a pass proves the exact
                    // capability summaries rely on (not just reachability),
                    // and the reply is checked as parseable JSON.
                    val res = client.complete(
                        LlmRequest(
                            "Reply with only this JSON object and nothing else: {\"ok\":true}",
                            "ping",
                            jsonMode = true,
                            timeoutSeconds = TEST_TIMEOUT_SECONDS,
                        ),
                    )
                    val jsonOk = res.status == LlmStatus.OK && runCatching {
                        val element = kotlinx.serialization.json.Json.parseToJsonElement(res.text)
                        element is kotlinx.serialization.json.JsonObject
                    }.getOrDefault(false)
                    when {
                        res.status == LlmStatus.OK && !jsonOk ->
                            StageOutcome.Fail("json_mode_unsupported")
                        res.status == LlmStatus.OK -> {
                            // A test that worked is a setup the caregiver
                            // expects to stick. Without this the key stays in
                            // the text field only, and the summary screen —
                            // which reads the stored key — tells them to set
                            // AI up again.
                            if (persistCredentials()) {
                                savedApiKey = trimmedKey
                                savedModel = model
                                savedBaseUrl = baseUrl
                                keyState = store.apiKeyState(provider)
                                keyError = null
                                StageOutcome.Pass
                            } else {
                                // The keystore refused the key: the
                                // caregiver has to know it was not stored.
                                val failure = context.getString(R.string.settings_ai_key_store_failed)
                                keyError = failure
                                keyState = store.apiKeyState(provider)
                                StageOutcome.Fail("key_store_failed")
                            }
                        }
                        else -> StageOutcome.Fail(res.error ?: "unavailable")
                    }
                }
                SetupStage.Format -> {
                    // The JSON-shape fixture is only sent after this
                    // provider's consent was accepted through the shared
                    // consent dialog.
                    if (!store.consentGiven(provider)) {
                        StageOutcome.NeedsConsent
                    } else {
                        withTimeout(TEST_TIMEOUT_SECONDS * 1_000L) {
                            val client = llmClientFor(
                                graph,
                                provider,
                                baseUrl.ifBlank { provider.defaultBaseUrl },
                                model.ifBlank { provider.defaultModel },
                                trimmedKey,
                            )
                            val preferences = summaryPrompt.takeIf { it.isNotBlank() }
                                ?: systemPrompt.takeIf { it.isNotBlank() }
                            val prompt = Prompts.withPreferences(
                                Prompts.summarySystemPrompt(),
                                preferences,
                            )
                            val probe = PromptNotes.dataBlock(
                                listOf(
                                    PromptNote(
                                        id = "probe",
                                        date = LocalDate.now().toString(),
                                        text = context.getString(R.string.settings_ai_format_probe),
                                    ),
                                ),
                            )
                            val res = client.complete(
                                LlmRequest(prompt, probe, jsonMode = true, timeoutSeconds = TEST_TIMEOUT_SECONDS),
                            )
                            val reason = if (res.status == LlmStatus.FALLBACK) {
                                res.error ?: "unavailable"
                            } else if (SummaryResponseFormat.validate(res.text) != null) {
                                // Shape-validator details are internal;
                                // report a stable recovery code instead.
                                "malformed_output"
                            } else {
                                null
                            }
                            if (reason == null) StageOutcome.Pass else StageOutcome.Fail(reason)
                        }
                    }
                }
                SetupStage.Quality -> withTimeout(TEST_TIMEOUT_SECONDS * 1_000L) {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        trimmedKey,
                    )
                    val preferences = summaryPrompt.takeIf { it.isNotBlank() }
                        ?: systemPrompt.takeIf { it.isNotBlank() }
                    val language = if (context.resources.configuration.locales[0]?.language == "ar") "ar" else "en"
                    val result = GroundingProbe.runIfConsented(
                        client = client,
                        language = language,
                        consentGiven = store.consentGiven(provider),
                        promptPreferences = preferences,
                    )
                    when {
                        result == null -> StageOutcome.NeedsConsent
                        result.status == GroundingProbeStatus.PASS ->
                            StageOutcome.Graded(
                                result.groundedObservations,
                                result.totalObservations,
                                full = true,
                            )
                        result.status == GroundingProbeStatus.PARTIAL ->
                            StageOutcome.Graded(
                                result.groundedObservations,
                                result.totalObservations,
                                full = false,
                            )
                        result.status == GroundingProbeStatus.FAIL ->
                            StageOutcome.Fail("quality_fail")
                        else -> StageOutcome.Fail(result.reasonCode ?: "unavailable")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            StageOutcome.Fail("timeout")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The screen went away mid-request or the caregiver cancelled:
            // let the scope cancel.
            throw e
        } catch (e: Exception) {
            // Exception text is not a recovery step and may contain
            // transport internals; report the connect-and-retry code.
            StageOutcome.Fail("unavailable")
        }
    }

    val setupRunner = remember { AiSetupCheckRunner { stage -> runSetupStage(stage) } }

    fun startSetupCheck(from: SetupStage = SetupStage.Connection, previous: SetupCheckState? = null) {
        if (apiKey.isBlank()) {
            // Without a key there is nothing to send; mark the first stage
            // failed and leave the rest untouched.
            setupState = SetupCheckState(
                stages = mapOf(
                    SetupStage.Connection to StageStatus.Failed("missing_key"),
                    SetupStage.Format to StageStatus.Pending,
                    SetupStage.Quality to StageStatus.Pending,
                ),
                running = false,
                awaitingConsent = false,
            )
            return
        }
        setupJob = scope.launch {
            try {
                setupRunner.run(from = from, previous = previous) { setupState = it }
            } finally {
                setupJob = null
            }
        }
    }

    // The top bar, Cancel, and the system back button all take the same exit
    // path, so unsaved edits cannot disappear without a confirmation.
    BackHandler(enabled = hasUnsavedChanges && !showDiscardDialog) {
        requestExit()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Detail routes draw their own top bar with a back button (the same
        // contract as Settings and the other sub-screens), so this screen is
        // not a dead-end that relies on the system back button.
        AppTopBar(
            title = stringResource(R.string.settings_ai),
            onBack = { requestExit() },
        )

        // One untitled card holds the connection settings; the key rows keep
        // their place between provider and model. The group headers used to
        // repeat the first row's title ("API key", "System prompt").
        CaregiverSettingsGroup(
            items = buildList {
                add(
                    CaregiverSettingsRow(
                        title = stringResource(R.string.settings_ai_provider),
                        icon = Icons.Outlined.Cloud,
                        description = provider.displayName,
                        onClick = { showProviderDialog = true },
                    ),
                )
                add(
                    CaregiverSettingsRow(
                        title = stringResource(R.string.settings_ai_setup_guide),
                        icon = Icons.Outlined.Link,
                        description = provider.displayName,
                        onClick = {
                            guideLinkError = false
                            showGuideDialog = true
                        },
                    ),
                )
                add(
                    CaregiverSettingsRow(
                        title = stringResource(R.string.settings_ai_api_key),
                        icon = Icons.Outlined.Key,
                        description = when {
                            apiKey.isNotBlank() -> "••••••••"
                            keyState is ApiKeyState.Invalidated -> stringResource(R.string.settings_ai_key_invalidated)
                            keyState is ApiKeyState.Unavailable -> stringResource(R.string.settings_ai_key_unavailable)
                            else -> stringResource(R.string.settings_ai_set_api_key)
                        },
                        onClick = { showKeyDialog = true },
                    ),
                )
                if (apiKey.isNotBlank()) {
                    add(
                        CaregiverSettingsRow(
                            title = stringResource(R.string.settings_ai_clear_key),
                            icon = Icons.Outlined.Key,
                            // No description: it repeated the title verbatim.
                            onClick = { showClearKeyDialog = true },
                        ),
                    )
                }
                add(
                    CaregiverSettingsRow(
                        title = stringResource(R.string.settings_ai_model),
                        icon = Icons.Outlined.Memory,
                        description = model.ifBlank { provider.defaultModel },
                        onClick = { showModelDialog = true },
                    ),
                )
                add(
                    CaregiverSettingsRow(
                        title = stringResource(R.string.settings_ai_base_url),
                        icon = Icons.Outlined.Link,
                        description = baseUrl.ifBlank { provider.defaultBaseUrl },
                        onClick = { showUrlDialog = true },
                    ),
                )
            },
        )
        if (customBaseUrl) {
            Text(
                text = stringResource(R.string.settings_ai_custom_url_warning, provider.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("custom_url_warning"),
            )
        }

        CaregiverSettingsGroup(
            items = listOf(
                CaregiverSettingsRow(
                    title = stringResource(R.string.settings_ai_system_prompt),
                    icon = Icons.Outlined.Article,
                    description = if (systemPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                    onClick = { showPromptDialog = true },
                ),
                CaregiverSettingsRow(
                    title = stringResource(R.string.settings_ai_summary_prompt),
                    icon = Icons.Outlined.Description,
                    description = if (summaryPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                    onClick = { showSummaryPromptDialog = true },
                ),
                CaregiverSettingsRow(
                    title = stringResource(R.string.settings_ai_plan_prompt),
                    icon = Icons.Outlined.List,
                    description = if (planPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                    onClick = { showPlanPromptDialog = true },
                ),
            ),
        )

        // One button runs connection, summary format and accuracy in order;
        // each stage reports its own status underneath.
        Button(
            onClick = { startSetupCheck() },
            enabled = !setupState.running && !modelInvalid,
            modifier = Modifier.testTag("ai_check_setup"),
        ) {
            Text(stringResource(R.string.settings_ai_check_setup))
        }
        if (setupState.running) {
            // Without this the button stays disabled until the client gives
            // up, and a provider that never answers looks like a frozen
            // screen.
            TextButton(
                onClick = { setupJob?.cancel() },
                modifier = Modifier.testTag("ai_check_cancel"),
            ) { Text(stringResource(R.string.common_cancel)) }
        }
        Text(
            text = stringResource(R.string.settings_ai_check_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            SetupStage.Connection to R.string.settings_ai_stage_connection,
            SetupStage.Format to R.string.settings_ai_stage_format,
            SetupStage.Quality to R.string.settings_ai_stage_quality,
        ).forEach { (stage, labelRes) ->
            SetupStageRow(
                stage = stage,
                labelRes = labelRes,
                status = setupState.stages[stage] ?: StageStatus.Pending,
            )
        }

        if (showGuideDialog) {
            val url = when (provider) {
                AiProvider.OPENAI -> "https://help.openai.com/en/articles/4936850-where-do-i-find-my-openai-api-key"
                AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
                AiProvider.ANTHROPIC -> "https://platform.claude.com/docs/en/get-api-key"
                // xAI has its own console; the shared branch sent it to
                // OpenRouter, which is a different company.
                AiProvider.XAI -> "https://console.x.ai"
                AiProvider.OPEN_ROUTER -> "https://openrouter.ai"
            }
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text(stringResource(R.string.settings_ai_setup_guide_title, provider.displayName)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_ai_setup_step1))
                        Text(stringResource(R.string.settings_ai_setup_step2))
                        Text(stringResource(R.string.settings_ai_setup_step3))
                        Text(stringResource(R.string.settings_ai_setup_step4))
                        Text(stringResource(R.string.settings_ai_cost_note))
                        TextButton(onClick = {
                            guideLinkError = false
                            try {
                                uriHandler.openUri(url)
                            } catch (e: Exception) {
                                // A device with no browser must not fail
                                // silently: keep the dialog open and explain
                                // why the link did not open.
                                Log.w("AiSettings", "Unable to open the AI setup guide", e)
                                guideLinkError = true
                            }
                        }) { Text(stringResource(R.string.settings_ai_open_website)) }
                        if (guideLinkError) {
                            Text(
                                text = stringResource(R.string.settings_ai_browser_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text(stringResource(R.string.common_ok))
                    }
                },
            )
        }

        val baseUrlInvalid = !AiSettingsValidation.isAcceptableBaseUrl(baseUrl)
        if (baseUrlInvalid) {
            Text(
                text = stringResource(R.string.settings_ai_invalid_base_url),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = {
            // The key, the provider, the model and the base URL are stored
            // the same way whether the caregiver pressed Save or checked the
            // connection first: a key that works is a key that is kept.
            if (!persistCredentials()) {
                keyError = context.getString(R.string.settings_ai_key_store_failed)
                keyState = store.apiKeyState(provider)
                return@Button
            }
            store.setSystemPrompt(systemPrompt)
            store.setSummarySystemPrompt(summaryPrompt)
            store.setPlanSystemPrompt(planPrompt)
            keyError = null
            keyState = store.apiKeyState(provider)
            navController.popBackStack()
        }, enabled = !baseUrlInvalid && !modelInvalid) { Text(stringResource(R.string.settings_ai_save)) }

        keyError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { requestExit() }) { Text(stringResource(R.string.common_cancel)) }

        if (consentGiven) {
            TextButton(onClick = { showRevokeConsentDialog = true }) {
                Text(stringResource(R.string.settings_ai_revoke_consent, provider.displayName))
            }
        }
        consentStatus?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingProvider = null
            },
            title = { Text(stringResource(R.string.settings_ai_unsaved_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_ai_unsaved_message,
                        provider.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { discardAndContinue() }) {
                    Text(stringResource(R.string.settings_ai_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    pendingProvider = null
                }) {
                    Text(stringResource(R.string.settings_ai_keep_editing))
                }
            },
        )
    }

    if (showRevokeConsentDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeConsentDialog = false },
            title = { Text(stringResource(R.string.settings_ai_revoke_consent_title)) },
            text = { Text(stringResource(R.string.settings_ai_revoke_consent_message, provider.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    store.revokeConsent(provider)
                    consentGiven = false
                    consentStatus = context.getString(
                        R.string.settings_ai_consent_revoked,
                        provider.displayName,
                    )
                    showRevokeConsentDialog = false
                }) {
                    Text(stringResource(R.string.settings_ai_revoke_consent_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConsentDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (setupState.awaitingConsent) {
        val consentModel = model.ifBlank { provider.defaultModel }
        AiConsentDialog(
            providerName = provider.displayName,
            modelName = consentModel,
            dataUseNoticeRes = AiModelCatalog.dataUseNotice(provider, consentModel)?.stringRes(),
            onAccept = {
                store.setConsentGiven(provider)
                consentGiven = true
                val previous = setupState
                startSetupCheck(from = SetupStage.Format, previous = previous)
            },
            onDismiss = {
                // Declining consent leaves the run paused at the Format
                // stage; the caregiver can accept and run it again.
                setupState = setupState.copy(awaitingConsent = false)
            },
        )
    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text(stringResource(R.string.settings_ai_provider)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiProvider.values().forEach { p ->
                        TextButton(onClick = {
                            showProviderDialog = false
                            if (p != provider) {
                                if (hasUnsavedChanges) {
                                    pendingProvider = p
                                    showDiscardDialog = true
                                } else {
                                    // Every provider has its own key, model,
                                    // and base URL. Loading them here keeps
                                    // one provider's credential and endpoint
                                    // from leaking into another.
                                    loadProvider(p)
                                }
                            }
                        }) { Text(p.displayName) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProviderDialog = false }) { Text(stringResource(R.string.settings_ai_close)) } },
        )
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(stringResource(R.string.settings_ai_api_key)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                        // A key is one line: a pasted newline must not turn
                        // the field into a multi-line box.
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_key_saved_for, provider.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showKeyDialog = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text(stringResource(R.string.settings_ai_clear_key)) },
            text = { Text(stringResource(R.string.settings_ai_clear_key_message, provider.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    store.clearApiKey(provider)
                    apiKey = ""
                    savedApiKey = ""
                    keyState = store.apiKeyState(provider)
                    keyError = null
                    setupState = pendingSetupCheckState()
                    showClearKeyDialog = false
                }) { Text(stringResource(R.string.settings_ai_clear_key)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeyDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.settings_ai_model)) },
            text = {
                var otherSelected by remember {
                    mutableStateOf(!AiModelCatalog.isCatalogModel(provider, model))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.selectableGroup()) {
                        AiModelCatalog.options(provider).forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = model.trim() == option.id,
                                        role = Role.RadioButton,
                                        onClick = {
                                            model = option.id
                                            setupState = pendingSetupCheckState()
                                            otherSelected = false
                                        },
                                    )
                                    .testTag("model_option_${option.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = model.trim() == option.id,
                                    onClick = null,
                                )
                                Text(
                                    text = Bidi.isolate(option.id),
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.recommendedForSummaries) {
                                    RecommendedTag()
                                }
                                if (option.free) {
                                    FreeTag()
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = otherSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        otherSelected = true
                                        setupState = pendingSetupCheckState()
                                    },
                                )
                                .testTag("model_option_other"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = otherSelected, onClick = null)
                            Text(
                                text = stringResource(R.string.settings_ai_model_other),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (otherSelected) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = {
                                model = it
                                setupState = pendingSetupCheckState()
                            },
                            label = { Text(stringResource(R.string.settings_ai_custom_model)) },
                            singleLine = true,
                            isError = modelInvalid,
                            supportingText = {
                                if (modelInvalid) {
                                    Text(stringResource(R.string.settings_ai_invalid_model))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_custom_field"),
                        )
                    }
                    AiModelCatalog.dataUseNotice(provider, model.ifBlank { provider.defaultModel })
                        ?.let { notice ->
                            Text(
                                text = stringResource(notice.stringRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("model_data_use_notice"),
                            )
                        }
                    // What the provider offers for free, so the choice is
                    // made with the cost in view. Paid models are billed per
                    // use; they are never picked silently.
                    provider.freeTier?.let { tier ->
                        Text(
                            text = stringResource(
                                when (tier) {
                                    AiFreeTier.FREE_MODELS -> R.string.settings_ai_tier_free_models
                                    AiFreeTier.TRIAL_CREDITS -> R.string.settings_ai_tier_trial_credits
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_ai_model_paid_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showModelDialog = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.settings_ai_base_url)) },
            text = {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    supportingText = {
                        if (customBaseUrl) {
                            Text(stringResource(R.string.settings_ai_custom_url_warning, provider.displayName))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { showUrlDialog = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text(stringResource(R.string.settings_ai_system_prompt)) },
            text = {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPromptDialog = false }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    systemPrompt = ""
                    showPromptDialog = false
                }) { Text(stringResource(R.string.settings_ai_reset_default)) }
            },
        )
    }

    if (showSummaryPromptDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryPromptDialog = false },
            title = { Text(stringResource(R.string.settings_ai_summary_system_prompt)) },
            text = { OutlinedTextField(value = summaryPrompt, onValueChange = { summaryPrompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton(onClick = { showSummaryPromptDialog = false }) { Text(stringResource(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { summaryPrompt = ""; showSummaryPromptDialog = false }) { Text(stringResource(R.string.settings_ai_reset_default)) } },
        )
    }
    if (showPlanPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPlanPromptDialog = false },
            title = { Text(stringResource(R.string.settings_ai_plan_system_prompt)) },
            text = { OutlinedTextField(value = planPrompt, onValueChange = { planPrompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton(onClick = { showPlanPromptDialog = false }) { Text(stringResource(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { planPrompt = ""; showPlanPromptDialog = false }) { Text(stringResource(R.string.settings_ai_reset_default)) } },
        )
    }
}

private fun pendingSetupCheckState() = SetupCheckState(
    stages = SetupStage.values().associateWith { StageStatus.Pending },
    running = false,
    awaitingConsent = false,
)

/** Single place the settings screen builds a provider client (test + probes). */
private fun llmClientFor(
    graph: AppGraph,
    provider: AiProvider,
    baseUrl: String,
    model: String,
    apiKey: String,
): LlmClient = when (provider) {
    AiProvider.OPENAI, AiProvider.XAI, AiProvider.OPEN_ROUTER ->
        graph.llmClientFactory.createClient(baseUrl, model, apiKey)
    AiProvider.ANTHROPIC -> graph.llmClientFactory.createAnthropicClient(baseUrl, model, apiKey)
    AiProvider.GEMINI -> graph.llmClientFactory.createGeminiClient(baseUrl, model, apiKey)
}

/** One labelled row of the "Check setup" stage list. */
@Composable
private fun SetupStageRow(
    stage: SetupStage,
    labelRes: Int,
    status: StageStatus,
) {
    val context = LocalContext.current
    val tag = when (stage) {
        SetupStage.Connection -> "ai_stage_connection"
        SetupStage.Format -> "ai_stage_format"
        SetupStage.Quality -> "ai_stage_quality"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stageStatusText(context, stage, status),
            style = MaterialTheme.typography.bodySmall,
            color = if (status is StageStatus.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Maps a stage outcome to the localized text shown under its label. */
private fun stageStatusText(context: Context, stage: SetupStage, status: StageStatus): String =
    when (status) {
        StageStatus.Pending -> context.getString(R.string.settings_ai_stage_pending)
        StageStatus.Running -> context.getString(R.string.settings_ai_stage_running)
        StageStatus.Skipped -> context.getString(R.string.settings_ai_stage_skipped)
        StageStatus.Cancelled -> context.getString(R.string.settings_ai_test_cancelled)
        StageStatus.Passed -> when (stage) {
            SetupStage.Connection -> context.getString(R.string.settings_ai_connection_ok_saved)
            SetupStage.Format -> context.getString(R.string.settings_ai_format_ok)
            // The quality stage always reports Graded, never bare Passed.
            SetupStage.Quality -> context.getString(R.string.settings_ai_quality_pass, 0, 0)
        }
        is StageStatus.Graded -> if (status.full) {
            context.getString(R.string.settings_ai_quality_pass, status.grounded, status.total)
        } else {
            context.getString(R.string.settings_ai_quality_partial, status.grounded, status.total)
        }
        is StageStatus.Failed -> when (status.reasonCode) {
            "key_store_failed" -> context.getString(R.string.settings_ai_key_store_failed)
            "quality_fail" -> context.getString(R.string.settings_ai_quality_fail)
            else -> AiRecovery.copyFor(status.reasonCode).resolvedMessage(context)
        }
    }

/** The "free" marker on a model the caregiver does not have to pay for. */
@Composable
private fun FreeTag() {
    Text(
        text = stringResource(R.string.settings_ai_model_free),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("model_free_tag"),
    )
}

/** The "recommended for summaries" marker on the verified summary model. */
@Composable
private fun RecommendedTag() {
    Text(
        text = stringResource(R.string.settings_ai_model_recommended),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("model_recommended_tag"),
    )
}

/**
 * The budget for a connection test or a format check. The client's own
 * timeout is two minutes per attempt, which is a long time to stare at a
 * disabled button; a ping answers in seconds or not at all.
 */
private const val TEST_TIMEOUT_SECONDS = 20
