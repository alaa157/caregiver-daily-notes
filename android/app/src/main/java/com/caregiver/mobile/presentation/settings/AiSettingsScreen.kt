package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.AnthropicLlmClient
import com.caregiver.mobile.ai.GeminiLlmClient
import com.caregiver.mobile.ai.LlmClient
import com.caregiver.mobile.ai.LlmRequest
import com.caregiver.mobile.ai.LlmStatus
import com.caregiver.mobile.ai.OpenAiCompatibleLlmClient
import com.caregiver.mobile.ai.Prompts
import com.caregiver.mobile.ai.SummaryResponseFormat
import com.caregiver.mobile.ui.component.SettingsGroup
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val store = remember { AiKeyStore(graph.context) }
    var provider by remember { mutableStateOf(store.provider() ?: AiProvider.OPENAI) }
    var apiKey by remember { mutableStateOf(store.apiKey().orEmpty()) }
    var model by remember { mutableStateOf(store.model() ?: provider.defaultModel) }
    var baseUrl by remember { mutableStateOf(store.baseUrlOrDefault(provider)) }
    var systemPrompt by remember { mutableStateOf(store.systemPrompt()) }
    var summaryPrompt by remember { mutableStateOf(store.summarySystemPrompt()) }
    var planPrompt by remember { mutableStateOf(store.planSystemPrompt()) }
    // Connection status and feature-level format status are deliberately
    // separate: reachability is not the same as a usable response shape.
    var status by remember { mutableStateOf<String?>(null) }
    var formatStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }

    // Dialog visibility
    var showProviderDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showSummaryPromptDialog by remember { mutableStateOf(false) }
    var showPlanPromptDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.settings_ai))

        SettingsGroup(title = stringResource(R.string.settings_ai_provider)) {
            TextButton(onClick = { showProviderDialog = true }) {
                Text(provider.displayName)
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_ai_api_key)) {
            TextButton(onClick = { showKeyDialog = true }) {
                Text(if (apiKey.isBlank()) stringResource(R.string.settings_ai_set_api_key) else "••••••••")
            }
            if (apiKey.isNotBlank()) {
                TextButton(onClick = { showClearKeyDialog = true }) {
                    Text(stringResource(R.string.settings_ai_clear_key))
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_ai_model)) {
            TextButton(onClick = { showModelDialog = true }) {
                Text(model.ifBlank { provider.defaultModel })
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_ai_base_url)) {
            TextButton(onClick = { showUrlDialog = true }) {
                Text(baseUrl.ifBlank { provider.defaultBaseUrl })
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_ai_system_prompt)) {
            TextButton(onClick = { showPromptDialog = true }) {
                Text(if (systemPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom))
            }
            TextButton(onClick = { showSummaryPromptDialog = true }) {
                Text(stringResource(R.string.settings_ai_summary_prompt) + ": " + if (summaryPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom))
            }
            TextButton(onClick = { showPlanPromptDialog = true }) {
                Text(stringResource(R.string.settings_ai_plan_prompt) + ": " + if (planPrompt.isBlank()) stringResource(R.string.settings_ai_default) else stringResource(R.string.settings_ai_custom))
            }
        }

        Button(onClick = {
            if (apiKey.isBlank()) {
                status = graph.context.getString(R.string.settings_ai_failed, "missing_key")
                return@Button
            }
            testing = true
            scope.launch {
                try {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        apiKey,
                    )
                    val res = client.complete(LlmRequest("Reply with OK", "ping"))
                    status = if (res.status == LlmStatus.OK) {
                        graph.context.getString(R.string.settings_ai_connection_ok)
                    } else {
                        graph.context.getString(R.string.settings_ai_failed, res.error ?: "unknown")
                    }
                } catch (e: Exception) {
                    status = graph.context.getString(R.string.settings_ai_failed, e.message ?: "error")
                } finally {
                    testing = false
                }
            }
        }, enabled = !testing) {
            Text(if (testing) stringResource(R.string.settings_ai_testing) else stringResource(R.string.settings_ai_test_connection))
        }
        status?.let { Text(it) }

        // Feature-level check, kept distinct from connectivity: asks for a real
        // summary-format reply and validates its shape.
        Button(onClick = {
            if (apiKey.isBlank()) {
                formatStatus = graph.context.getString(R.string.settings_ai_failed, "missing_key")
                return@Button
            }
            validating = true
            scope.launch {
                try {
                    val client = llmClientFor(
                        graph,
                        provider,
                        baseUrl.ifBlank { provider.defaultBaseUrl },
                        model.ifBlank { provider.defaultModel },
                        apiKey,
                    )
                    val prompt = summaryPrompt.takeIf { it.isNotBlank() }
                        ?: systemPrompt.takeIf { it.isNotBlank() }
                        ?: Prompts.SUMMARY_SYSTEM
                    val probe = "[NOTE id=\"probe\" date=${LocalDate.now()}]\nFormat validation probe: no concerns reported today.\n[/NOTE]"
                    val res = client.complete(LlmRequest(prompt, probe))
                    val reason = if (res.status == LlmStatus.FALLBACK) {
                        res.error ?: "unavailable"
                    } else {
                        SummaryResponseFormat.validate(res.text)
                    }
                    formatStatus = if (reason == null) {
                        graph.context.getString(R.string.settings_ai_format_ok)
                    } else {
                        graph.context.getString(R.string.settings_ai_format_invalid, reason)
                    }
                } catch (e: Exception) {
                    formatStatus = graph.context.getString(R.string.settings_ai_format_invalid, e.message ?: "error")
                } finally {
                    validating = false
                }
            }
        }, enabled = !validating) {
            Text(if (validating) stringResource(R.string.settings_ai_validating) else stringResource(R.string.settings_ai_validate_format))
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
            store.setProvider(provider)
            store.setApiKey(apiKey.trim())
            store.setModel(AiSettingsValidation.normalizedModel(model, provider))
            store.setBaseUrl(AiSettingsValidation.normalizedBaseUrl(baseUrl, provider))
            store.setSystemPrompt(systemPrompt)
            store.setSummarySystemPrompt(summaryPrompt)
            store.setPlanSystemPrompt(planPrompt)
            navController.popBackStack()
        }, enabled = !baseUrlInvalid) { Text(stringResource(R.string.settings_ai_save)) }

        TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.common_cancel)) }

        TextButton(onClick = { store.revokeConsent() }) { Text(stringResource(R.string.settings_ai_revoke_consent)) }
    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text(stringResource(R.string.settings_ai_provider)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiProvider.values().forEach { p ->
                        TextButton(onClick = {
                            if (p != provider) {
                                val previousDefault = provider.defaultModel
                                provider = p
                                // Keep a deliberately entered custom model;
                                // otherwise adopt the new provider's default.
                                if (model.isBlank() || model == previousDefault) {
                                    model = p.defaultModel
                                }
                                baseUrl = p.defaultBaseUrl
                            }
                            showProviderDialog = false
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
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { showKeyDialog = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text(stringResource(R.string.settings_ai_clear_key)) },
            text = { Text(stringResource(R.string.settings_ai_clear_key_message)) },
            confirmButton = {
                TextButton(onClick = {
                    store.clearApiKey()
                    apiKey = ""
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
                    Text(stringResource(R.string.settings_ai_suggestions))
                    listOf(provider.defaultModel).forEach { s ->
                        TextButton(onClick = { model = s }) { Text(s) }
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

