package com.caregiver.mobile.presentation.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.ApiKeyState
import com.caregiver.mobile.ai.AiFreeTier
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.AnthropicLlmClient
import com.caregiver.mobile.ai.GeminiLlmClient
import com.caregiver.mobile.ai.LlmClient
import com.caregiver.mobile.ai.LlmRequest
import com.caregiver.mobile.ai.LlmStatus
import com.caregiver.mobile.ai.OpenAiCompatibleLlmClient
import com.caregiver.mobile.ai.PromptNote
import com.caregiver.mobile.ai.PromptNotes
import com.caregiver.mobile.ai.Prompts
import com.caregiver.mobile.ai.SummaryResponseFormat
import com.caregiver.mobile.core.navigation.markUnsavedChanges
import com.caregiver.mobile.presentation.common.AiRecovery
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.CaregiverSettingsGroup
import com.caregiver.mobile.presentation.common.CaregiverSettingsItem
import com.caregiver.mobile.presentation.common.resolvedMessage
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val store = remember { AiKeyStore(graph.context) }
    val uriHandler = LocalUriHandler.current
    val navEntry = remember(navController) { navController.currentBackStackEntry }
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
    var systemPrompt by remember { mutableStateOf(store.systemPrompt()) }
    var summaryPrompt by remember { mutableStateOf(store.summarySystemPrompt()) }
    var planPrompt by remember { mutableStateOf(store.planSystemPrompt()) }
    val savedSystemPrompt = remember { store.systemPrompt() }
    val savedSummaryPrompt = remember { store.summarySystemPrompt() }
    val savedPlanPrompt = remember { store.planSystemPrompt() }
    // Connection status and feature-level format status are deliberately
    // separate: reachability is not the same as a usable response shape.
    var status by remember { mutableStateOf<String?>(null) }
    var formatStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    // The in-flight test, so the caregiver can stop waiting for a provider
    // that is not going to answer.
    var testJob by remember { mutableStateOf<Job?>(null) }
    var formatJob by remember { mutableStateOf<Job?>(null) }
    var consentGiven by remember { mutableStateOf(store.consentGiven()) }
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
        navEntry?.markUnsavedChanges(hasUnsavedChanges)
    }
    DisposableEffect(navEntry) {
        onDispose { navEntry?.markUnsavedChanges(false) }
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
        status = null
        formatStatus = null
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
        CaregiverSettingsGroup {
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_provider),
                icon = Icons.Outlined.Cloud,
                description = provider.displayName,
                onClick = { showProviderDialog = true },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_setup_guide),
                icon = Icons.Outlined.Link,
                description = provider.displayName,
                onClick = {
                    guideLinkError = false
                    showGuideDialog = true
                },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_api_key),
                icon = Icons.Outlined.Key,
                description = when {
                    apiKey.isNotBlank() -> "••••••••"
                    keyState is ApiKeyState.Invalidated -> stringResource(R.string.settings_ai_key_invalidated)
                    else -> stringResource(R.string.settings_ai_set_api_key)
                },
                onClick = { showKeyDialog = true },
            )
            if (apiKey.isNotBlank()) {
                CaregiverSettingsItem(
                    title = stringResource(R.string.settings_ai_clear_key),
                    icon = Icons.Outlined.Key,
                    // No description: it repeated the title verbatim.
                    onClick = { showClearKeyDialog = true },
                )
            }
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_model),
                icon = Icons.Outlined.Memory,
                description = model.ifBlank { provider.defaultModel },
                onClick = { showModelDialog = true },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_base_url),
                icon = Icons.Outlined.Link,
                description = baseUrl.ifBlank { provider.defaultBaseUrl },
                onClick = { showUrlDialog = true },
            )
        }

        CaregiverSettingsGroup {
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_system_prompt),
                icon = Icons.Outlined.Article,
                description = if (systemPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                onClick = { showPromptDialog = true },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_summary_prompt),
                icon = Icons.Outlined.Description,
                description = if (summaryPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                onClick = { showSummaryPromptDialog = true },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai_plan_prompt),
                icon = Icons.Outlined.List,
                description = if (planPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom),
                onClick = { showPlanPromptDialog = true },
            )
        }

        Button(onClick = {
            if (apiKey.isBlank()) {
                status = graph.context.getString(
                    R.string.settings_ai_failed,
                    AiRecovery.copyFor("missing_key").resolvedMessage(graph.context),
                )
                return@Button
            }
            testing = true
            testJob = scope.launch {
                try {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        trimmedKey,
                    )
                    // A ping that has not answered in twenty seconds is not
                    // going to answer.
                    val res = client.complete(
                        LlmRequest("Reply with OK", "ping", timeoutSeconds = TEST_TIMEOUT_SECONDS),
                    )
                    status = if (res.status == LlmStatus.OK) {
                        // A test that worked is a setup the caregiver expects
                        // to stick. Without this the key stays in the text
                        // field only, and the summary screen — which reads
                        // the stored key — tells them to set AI up again.
                        if (persistCredentials()) {
                            savedApiKey = trimmedKey
                            savedModel = model
                            savedBaseUrl = baseUrl
                            keyState = store.apiKeyState(provider)
                            keyError = null
                            graph.context.getString(R.string.settings_ai_connection_ok_saved)
                        } else {
                            // The keystore refused the key: the caregiver has
                            // to know that it was not stored.
                            val failure = graph.context.getString(R.string.settings_ai_key_store_failed)
                            keyError = failure
                            keyState = store.apiKeyState(provider)
                            graph.context.getString(R.string.settings_ai_failed, failure)
                        }
                    } else {
                        graph.context.getString(
                            R.string.settings_ai_failed,
                            AiRecovery.copyFor(res.error ?: "unavailable")
                                .resolvedMessage(graph.context),
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // The screen went away mid-request: let the scope cancel.
                    throw e
                } catch (e: Exception) {
                    // Exception text is not a recovery step and may contain
                    // transport internals; show the connect-and-retry copy.
                    status = graph.context.getString(
                        R.string.settings_ai_failed,
                        AiRecovery.copyFor("unavailable").resolvedMessage(graph.context),
                    )
                } finally {
                    testing = false
                }
            }
        }, enabled = !testing) {
            Text(if (testing) stringResource(R.string.settings_ai_testing) else stringResource(R.string.settings_ai_test_connection))
        }
        if (testing) {
            // Without this the button stays disabled until the client gives
            // up, and a provider that never answers looks like a frozen
            // screen.
            TextButton(onClick = {
                testJob?.cancel()
                testJob = null
                testing = false
                status = graph.context.getString(R.string.settings_ai_test_cancelled)
            }) { Text(stringResource(R.string.common_cancel)) }
        }
        status?.let { Text(it) }

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

        // Feature-level check, kept distinct from connectivity: asks for a real
        // summary-format reply and validates its shape.
        Button(onClick = {
            if (apiKey.isBlank()) {
                formatStatus = graph.context.getString(
                    R.string.settings_ai_format_invalid,
                    AiRecovery.copyFor("missing_key").resolvedMessage(graph.context),
                )
                return@Button
            }
            validating = true
            formatJob = scope.launch {
                try {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        trimmedKey,
                    )
                    val prompt = summaryPrompt.takeIf { it.isNotBlank() }
                        ?: systemPrompt.takeIf { it.isNotBlank() }
                        ?: Prompts.SUMMARY_SYSTEM
                    val probe = PromptNotes.dataBlock(
                        listOf(
                            PromptNote(
                                id = "probe",
                                date = LocalDate.now().toString(),
                                text = "Format validation probe: no concerns reported today.",
                            ),
                        ),
                    )
                    // Same path production uses: native json mode, so the
                    // check proves the provider can answer the real request.
                    val res = client.complete(
                        LlmRequest(prompt, probe, jsonMode = true, timeoutSeconds = TEST_TIMEOUT_SECONDS),
                    )
                    val reason = if (res.status == LlmStatus.FALLBACK) {
                        res.error ?: "unavailable"
                    } else if (SummaryResponseFormat.validate(res.text) != null) {
                        // The shape validator's English debug strings are not
                        // user copy; map every shape failure to the localized
                        // "couldn't read the response" recovery message.
                        "malformed_output"
                    } else {
                        null
                    }
                    formatStatus = if (reason == null) {
                        graph.context.getString(R.string.settings_ai_format_ok)
                    } else {
                        graph.context.getString(
                            R.string.settings_ai_format_invalid,
                            AiRecovery.copyFor(reason).resolvedMessage(graph.context),
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // The screen went away mid-request: let the scope cancel.
                    throw e
                } catch (e: Exception) {
                    formatStatus = graph.context.getString(
                        R.string.settings_ai_format_invalid,
                        AiRecovery.copyFor("unavailable").resolvedMessage(graph.context),
                    )
                } finally {
                    validating = false
                }
            }
        }, enabled = !validating) {
            Text(if (validating) stringResource(R.string.settings_ai_validating) else stringResource(R.string.settings_ai_validate_format))
        }
        if (validating) {
            TextButton(onClick = {
                formatJob?.cancel()
                formatJob = null
                validating = false
                formatStatus = graph.context.getString(R.string.settings_ai_test_cancelled)
            }) { Text(stringResource(R.string.common_cancel)) }
        }
        formatStatus?.let { Text(it) }

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
                keyError = graph.context.getString(R.string.settings_ai_key_store_failed)
                keyState = store.apiKeyState(provider)
                return@Button
            }
            store.setSystemPrompt(systemPrompt)
            store.setSummarySystemPrompt(summaryPrompt)
            store.setPlanSystemPrompt(planPrompt)
            keyError = null
            keyState = store.apiKeyState(provider)
            navController.popBackStack()
        }, enabled = !baseUrlInvalid) { Text(stringResource(R.string.settings_ai_save)) }

        keyError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { requestExit() }) { Text(stringResource(R.string.common_cancel)) }

        if (consentGiven) {
            TextButton(onClick = { showRevokeConsentDialog = true }) {
                Text(stringResource(R.string.settings_ai_revoke_consent))
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
            text = { Text(stringResource(R.string.settings_ai_revoke_consent_message)) },
            confirmButton = {
                TextButton(onClick = {
                    store.revokeConsent()
                    consentGiven = false
                    consentStatus = graph.context.getString(R.string.settings_ai_consent_revoked)
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
                    status = null
                    formatStatus = null
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.settings_ai_custom_model)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // What the provider offers for free, so the choice is
                    // made with the cost in view.
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
                    Text(stringResource(R.string.settings_ai_suggestions))
                    AiModelCatalog.options(provider).forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { model = option.id },
                                modifier = Modifier.weight(1f),
                            ) { Text(option.id) }
                            if (option.free) {
                                FreeTag()
                            }
                        }
                    }
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

/** Single place the settings screen builds a provider client (test + format validation). */
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

/**
 * The budget for a connection test or a format check. The client's own
 * timeout is two minutes per attempt, which is a long time to stare at a
 * disabled button; a ping answers in seconds or not at all.
 */
private const val TEST_TIMEOUT_SECONDS = 20
