package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.caregiver.mobile.ai.OpenAiCompatibleLlmClient
import com.caregiver.mobile.ui.component.SettingsGroup
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val store = remember { AiKeyStore(graph.context) }
    var provider by remember { mutableStateOf(store.provider() ?: AiProvider.OPENAI) }
    var apiKey by remember { mutableStateOf(store.apiKey().orEmpty()) }
    var model by remember { mutableStateOf(store.model() ?: provider.defaultModel) }
    var baseUrl by remember { mutableStateOf(provider.defaultBaseUrl) }
    var systemPrompt by remember { mutableStateOf(store.systemPrompt()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    // Dialog visibility
    var showProviderDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
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
        }

        Button(onClick = {
            testing = true
            scope.launch {
                val m = model.ifBlank { provider.defaultModel }
                val bu = baseUrl.ifBlank { provider.defaultBaseUrl }
                val client = when (provider) {
                    AiProvider.OPENAI, AiProvider.XAI, AiProvider.OPEN_ROUTER -> graph.llmClientFactory.createClient(bu, m, apiKey)
                    AiProvider.ANTHROPIC -> graph.llmClientFactory.createAnthropicClient(bu, m, apiKey)
                    AiProvider.GEMINI -> graph.llmClientFactory.createGeminiClient(bu, m, apiKey)
                }
                val res = client.complete(LlmRequest("Reply with OK", "ping"))
                status = if (res.status.name == "OK") graph.context.getString(R.string.settings_ai_connection_ok) else graph.context.getString(R.string.settings_ai_failed, res.error ?: "")
                testing = false
            }
        }, enabled = !testing) {
            Text(if (testing) stringResource(R.string.settings_ai_testing) else stringResource(R.string.settings_ai_test_connection))
        }
        status?.let { Text(it) }

        Button(onClick = {
            store.setProvider(provider)
            store.setApiKey(apiKey)
            store.setModel(model.ifBlank { provider.defaultModel })
            store.setSystemPrompt(systemPrompt)
            navController.popBackStack()
        }) { Text(stringResource(R.string.settings_ai_save)) }

        TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.common_cancel)) }
    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text(stringResource(R.string.settings_ai_provider)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiProvider.values().forEach { p ->
                        TextButton(onClick = {
                            provider = p
                            if (model.isBlank()) model = p.defaultModel
                            baseUrl = p.defaultBaseUrl
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
}

