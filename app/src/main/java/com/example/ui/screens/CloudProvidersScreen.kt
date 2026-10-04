package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CustomCloudProvider
import com.example.viewmodel.ScreenshotsViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProvidersScreen(
    viewModel: ScreenshotsViewModel
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomCloudProvider?>(null) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Cloud Providers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    val isActive = provider.id == activeProvider?.id
                    val isTesting = testingProviderId == provider.id

                    ProviderCard(
                        provider = provider,
                        isActive = isActive,
                        isTesting = isTesting,
                        onActivate = { viewModel.setActiveProvider(provider.id) },
                        onEdit = {
                            editingProvider = provider
                            showEditDialog = true
                        },
                        onDelete = { viewModel.deleteProvider(provider) },
                        onTestConnection = {
                            testingProviderId = provider.id
                            viewModel.testProviderConnection(provider) {
                                testingProviderId = null
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingProvider = null
                showEditDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .testTag("fab_add_provider")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Provider")
        }
    }

    if (showEditDialog) {
        ProviderEditDialog(
            initial = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { saved ->
                viewModel.saveProvider(saved)
                showEditDialog = false
            },
            onFetchModels = { prov, onResult ->
                viewModel.fetchProviderModels(prov, onResult)
            },
            onTest = { prov, onResult ->
                viewModel.testProviderConnection(prov, onResult)
            }
        )
    }
}

@Composable
fun ProviderCard(
    provider: CustomCloudProvider,
    isActive: Boolean,
    isTesting: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestConnection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onActivate)
            .testTag("card_provider_${provider.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isActive,
                        onClick = onActivate,
                        modifier = Modifier.testTag("radio_provider_${provider.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Text(
                                text = "Active Engine",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("btn_edit_prov_${provider.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (!provider.isDefaultGemini) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Endpoint: ${provider.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Model: ${provider.selectedModel}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (provider.apiKey.isNotBlank()) {
                        Text(
                            text = "Auth: Bearer ***${provider.apiKey.takeLast(4)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (provider.lastTestStatus != null) {
                    Text(
                        text = provider.lastTestStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (provider.lastTestStatus.startsWith("Connected")) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isTesting,
                    modifier = Modifier.testTag("btn_test_prov_${provider.id}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...")
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Ping")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderEditDialog(
    initial: CustomCloudProvider?,
    onDismiss: () -> Unit,
    onSave: (CustomCloudProvider) -> Unit,
    onFetchModels: (CustomCloudProvider, (List<String>) -> Unit) -> Unit,
    onTest: (CustomCloudProvider, (com.example.service.ai.ConnectionTestResult) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "Custom Cloud API") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var selectedModel by remember { mutableStateOf(initial?.selectedModel ?: "gpt-4o-mini") }
    var headersJson by remember { mutableStateOf(initial?.customHeadersJson ?: "{}") }
    var timeoutSeconds by remember { mutableStateOf((initial?.timeoutSeconds ?: 60).toString()) }

    var testStatusText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var discoveredModels by remember { mutableStateOf<List<String>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (initial == null) "Add Cloud API Provider" else "Edit Cloud Provider")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Preset Quick Fill Chips
                Text("Quick Presets:", style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InputChip(
                        selected = false,
                        onClick = {
                            name = "Ollama Local (vLLM)"
                            baseUrl = "http://10.0.2.2:11434/v1"
                            selectedModel = "llama3.2-vision"
                        },
                        label = { Text("Ollama Local") }
                    )
                    InputChip(
                        selected = false,
                        onClick = {
                            name = "Groq Cloud"
                            baseUrl = "https://api.groq.com/openai/v1"
                            selectedModel = "llama-3.2-11b-vision-preview"
                        },
                        label = { Text("Groq Cloud") }
                    )
                    InputChip(
                        selected = false,
                        onClick = {
                            name = "OpenRouter"
                            baseUrl = "https://openrouter.ai/api/v1"
                            selectedModel = "google/gemini-2.5-flash"
                        },
                        label = { Text("OpenRouter") }
                    )
                    InputChip(
                        selected = false,
                        onClick = {
                            name = "Custom Gateway"
                            baseUrl = "https://your-custom-endpoint.com/v1"
                            selectedModel = "custom-vision-model"
                        },
                        label = { Text("Custom Gateway") }
                    )
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_provider_name")
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (e.g. https://api.openai.com/v1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_provider_url")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Bearer Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_provider_key")
                )

                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("Model Identifier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_provider_model")
                )

                // Fetch Available Models from Server
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isFetchingModels = true
                            val temp = CustomCloudProvider(
                                name = name,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                selectedModel = selectedModel,
                                customHeadersJson = headersJson
                            )
                            onFetchModels(temp) { models ->
                                isFetchingModels = false
                                discoveredModels = models
                            }
                        },
                        enabled = !isFetchingModels && baseUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("btn_fetch_models")
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discover Models")
                    }

                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            val temp = CustomCloudProvider(
                                name = name,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                selectedModel = selectedModel,
                                customHeadersJson = headersJson
                            )
                            onTest(temp) { result ->
                                isTesting = false
                                testStatusText = result.message
                            }
                        },
                        enabled = !isTesting && baseUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("btn_test_dialog_conn")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Connection")
                    }
                }

                if (discoveredModels.isNotEmpty()) {
                    Text("Select Discovered Model:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        discoveredModels.take(8).forEach { m ->
                            InputChip(
                                selected = selectedModel == m,
                                onClick = { selectedModel = m },
                                label = { Text(m) }
                            )
                        }
                    }
                }

                if (testStatusText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = testStatusText ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = headersJson,
                    onValueChange = { headersJson = it },
                    label = { Text("Custom HTTP Headers (JSON)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = (initial ?: CustomCloudProvider(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        baseUrl = baseUrl
                    )).copy(
                        name = name.trim(),
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        selectedModel = selectedModel.trim(),
                        customHeadersJson = headersJson.trim(),
                        timeoutSeconds = timeoutSeconds.toIntOrNull() ?: 60
                    )
                    onSave(saved)
                },
                modifier = Modifier.testTag("btn_save_provider")
            ) {
                Text("Save Provider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
