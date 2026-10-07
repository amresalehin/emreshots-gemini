package com.amresalehin.emreshots.ui.screens

import com.amresalehin.emreshots.ui.theme.SuccessEmerald
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.service.ai.ConnectionTestResult
import com.amresalehin.emreshots.service.ai.FetchModelsResult
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProvidersScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    if (onNavigateBack != null) {
        BackHandler(onBack = onNavigateBack)
    }

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
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
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
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Custom Cloud & Local AI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Connect OpenAI, Groq, Ollama, DeepSeek, or Gemini endpoints to analyze photos and extract OCR text.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
                        color = if (provider.lastTestStatus.startsWith("Connected")) SuccessEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onFetchModels: (CustomCloudProvider, (com.amresalehin.emreshots.service.ai.FetchModelsResult) -> Unit) -> Unit,
    onTest: (CustomCloudProvider, (com.amresalehin.emreshots.service.ai.ConnectionTestResult) -> Unit) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "Google Gemini") }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl ?: "https://generativelanguage.googleapis.com") }
    var apiKey by remember(initial) { mutableStateOf(initial?.apiKey ?: "") }
    var selectedModel by remember(initial) { mutableStateOf(initial?.selectedModel ?: "gemini-2.5-flash") }
    var headersJson by remember(initial) { mutableStateOf(initial?.customHeadersJson ?: "{}") }
    var timeoutSeconds by remember(initial) { mutableStateOf((initial?.timeoutSeconds ?: 60).toString()) }

    var testStatusText by remember { mutableStateOf<String?>(null) }
    var testStatusSuccess by remember { mutableStateOf(true) }
    var isTesting by remember { mutableStateOf(false) }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchStatusText by remember { mutableStateOf<String?>(null) }
    var fetchStatusSuccess by remember { mutableStateOf(true) }
    var discoveredModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelSearchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (initial == null) "Add AI Provider" else "Edit AI Provider")
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
                Text("Quick Presets (Tap to Configure):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InputChip(
                        selected = name.contains("Gemini", ignoreCase = true) || baseUrl.contains("generativelanguage"),
                        onClick = {
                            name = "Google Gemini"
                            baseUrl = "https://generativelanguage.googleapis.com"
                            selectedModel = "gemini-2.5-flash"
                        },
                        label = { Text("Google Gemini") }
                    )
                    InputChip(
                        selected = name.contains("OpenAI", ignoreCase = true) || baseUrl.contains("openai.com"),
                        onClick = {
                            name = "OpenAI"
                            baseUrl = "https://api.openai.com/v1"
                            selectedModel = "gpt-4o-mini"
                        },
                        label = { Text("OpenAI") }
                    )
                    InputChip(
                        selected = name.contains("Groq", ignoreCase = true),
                        onClick = {
                            name = "Groq Cloud"
                            baseUrl = "https://api.groq.com/openai/v1"
                            selectedModel = "llama-3.2-11b-vision-preview"
                        },
                        label = { Text("Groq Cloud") }
                    )
                    InputChip(
                        selected = name.contains("OpenRouter", ignoreCase = true),
                        onClick = {
                            name = "OpenRouter"
                            baseUrl = "https://openrouter.ai/api/v1"
                            selectedModel = "google/gemini-2.5-flash"
                        },
                        label = { Text("OpenRouter") }
                    )
                    InputChip(
                        selected = name.contains("Ollama", ignoreCase = true) || baseUrl.contains("11434"),
                        onClick = {
                            name = "Ollama Local"
                            baseUrl = "http://10.0.2.2:11434"
                            selectedModel = "llama3.2-vision"
                        },
                        label = { Text("Ollama Local") }
                    )
                    InputChip(
                        selected = name.contains("Anthropic", ignoreCase = true),
                        onClick = {
                            name = "Anthropic Claude"
                            baseUrl = "https://api.anthropic.com"
                            selectedModel = "claude-3-5-sonnet-20241022"
                        },
                        label = { Text("Anthropic") }
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
                    label = { Text("Base URL (e.g. https://generativelanguage.googleapis.com)") },
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
                    label = { Text("Selected Model Identifier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_provider_model")
                )

                // Fetch Available Models & Test Connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isFetchingModels = true
                            fetchStatusText = null
                            val temp = CustomCloudProvider(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                selectedModel = selectedModel.trim(),
                                customHeadersJson = headersJson.trim()
                            )
                            onFetchModels(temp) { result ->
                                isFetchingModels = false
                                fetchStatusText = result.message
                                fetchStatusSuccess = result.isSuccess
                                if (result.isSuccess && result.models.isNotEmpty()) {
                                    discoveredModels = result.models
                                    suggestedModels = emptyList()
                                } else {
                                    discoveredModels = emptyList()
                                    suggestedModels = result.suggestedModels
                                }
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
                        Text("Fetch Models")
                    }

                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testStatusText = null
                            val temp = CustomCloudProvider(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                selectedModel = selectedModel.trim(),
                                customHeadersJson = headersJson.trim()
                            )
                            onTest(temp) { result ->
                                isTesting = false
                                testStatusText = result.message
                                testStatusSuccess = result.isSuccess
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
                        Text("Test Ping")
                    }
                }

                if (fetchStatusText != null) {
                    Surface(
                        color = if (fetchStatusSuccess) SuccessEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (fetchStatusSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (fetchStatusSuccess) SuccessEmerald else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fetchStatusText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fetchStatusSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (testStatusText != null) {
                    Surface(
                        color = if (testStatusSuccess) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (testStatusSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (testStatusSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = testStatusText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testStatusSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (discoveredModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Discovered ${discoveredModels.size} Models (tap to select):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (discoveredModels.size > 8) {
                            OutlinedTextField(
                                value = modelSearchQuery,
                                onValueChange = { modelSearchQuery = it },
                                placeholder = { Text("Search models...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                        }

                        val filteredModels = if (modelSearchQuery.isBlank()) discoveredModels else {
                            discoveredModels.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredModels.take(12).forEach { m ->
                                InputChip(
                                    selected = selectedModel == m,
                                    onClick = { selectedModel = m },
                                    label = { Text(m, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                } else if (suggestedModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Recommended Models (tap to select):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestedModels.forEach { m ->
                                InputChip(
                                    selected = selectedModel == m,
                                    onClick = { selectedModel = m },
                                    label = { Text(m, fontSize = 11.sp) }
                                )
                            }
                        }
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
                    val finalName = name.trim().ifBlank { "Custom AI Engine" }
                    val finalUrl = baseUrl.trim().ifBlank { "https://api.openai.com/v1" }
                    val isGemini = finalUrl.contains("generativelanguage.googleapis.com") ||
                            finalName.contains("Gemini", ignoreCase = true)

                    val saved = (initial ?: CustomCloudProvider(
                        id = UUID.randomUUID().toString(),
                        name = finalName,
                        baseUrl = finalUrl
                    )).copy(
                        name = finalName,
                        baseUrl = finalUrl,
                        apiKey = apiKey.trim(),
                        selectedModel = selectedModel.trim().ifBlank { if (isGemini) "gemini-2.5-flash" else "gpt-4o-mini" },
                        customHeadersJson = headersJson.trim().ifBlank { "{}" },
                        timeoutSeconds = timeoutSeconds.toIntOrNull() ?: 60,
                        isDefaultGemini = isGemini
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
