package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val store = remember { AiKeyStore(graph.context) }
    var provider by remember { mutableStateOf(store.provider() ?: AiProvider.OPENAI) }
    var apiKey by remember { mutableStateOf(store.apiKey().orEmpty()) }
    var model by remember { mutableStateOf(store.model() ?: provider.defaultModel) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "AI settings")
        Row {
            TextButton(onClick = { expanded = true }) { Text(provider.displayName) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AiProvider.values().forEach { p ->
                    DropdownMenuItem(text = { Text(p.displayName) }, onClick = {
                        provider = p
                        if (model.isBlank()) model = p.defaultModel
                        expanded = false
                    })
                }
            }
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            placeholder = { Text(provider.defaultModel) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            testing = true
            scope.launch {
                val client = buildClient(provider, model.ifBlank { provider.defaultModel }, apiKey)
                val res = client.complete(LlmRequest("Reply with OK", "ping"))
                status = if (res.status.name == "OK") "Connection OK" else "Failed: ${res.error}"
                testing = false
            }
        }, enabled = !testing) {
            Text(if (testing) "Testing..." else "Test connection")
        }
        status?.let { Text(it) }
        Button(onClick = {
            store.setProvider(provider)
            store.setApiKey(apiKey)
            store.setModel(model.ifBlank { provider.defaultModel })
            navController.popBackStack()
        }) { Text("Save") }
    }
}

private fun buildClient(provider: AiProvider, model: String, key: String): LlmClient = when (provider) {
    AiProvider.OPENAI, AiProvider.XAI, AiProvider.OPEN_ROUTER -> OpenAiCompatibleLlmClient(provider.defaultBaseUrl, model, key)
    AiProvider.ANTHROPIC -> AnthropicLlmClient(provider.defaultBaseUrl, model, key)
    AiProvider.GEMINI -> GeminiLlmClient(provider.defaultBaseUrl, model, key)
}
