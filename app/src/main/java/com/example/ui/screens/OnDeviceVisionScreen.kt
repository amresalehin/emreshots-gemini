package com.amresalehin.emreshots.ui.screens

import com.amresalehin.emreshots.R

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.service.ai.OnDeviceVisionCatalog
import com.amresalehin.emreshots.service.ai.OnDeviceVisionService
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDeviceVisionScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by viewModel.onDeviceVisionMode.collectAsStateWithLifecycle()
    val selectedModel by viewModel.onDeviceVisionModel.collectAsStateWithLifecycle()
    val service = remember(context) { OnDeviceVisionService(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { service.importCustomGguf(uri) }
                    .onSuccess {
                        viewModel.setOnDeviceVisionModel(it.id)
                        viewModel.showMessage("Imported and selected " + it.displayName + "!")
                    }
                    .onFailure {
                        viewModel.showMessage("GGUF import failed: " + it.message)
                    }
            }
        }
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.on_device_vision), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.inference_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.inference_mode_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "Automatic" to R.string.on_device_mode_automatic,
                                "FORCE_LOCAL" to R.string.on_device_mode_force_local,
                                "Disabled" to R.string.on_device_mode_disabled
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = mode.replace(" ", "_").equals(value, ignoreCase = true),
                                    onClick = { viewModel.setOnDeviceVisionMode(value) },
                                    label = { Text(stringResource(labelRes)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.custom_gguf_model), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.custom_gguf_model_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import))
                        }
                    }
                }
            }
            item {
                OnDeviceModelList(service, selectedModel, viewModel::setOnDeviceVisionModel, viewModel::showMessage)
            }
        }
    }
}

@Composable
private fun OnDeviceModelList(
    service: OnDeviceVisionService,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    showMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val models = remember { OnDeviceVisionCatalog.all() }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    suspend fun refresh() {
        installedIds = service.installedModels().map { it.model.id }.toSet()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.available_gguf_models), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        models.forEach { model ->
            val installed = model.id in installedIds
            val selected = model.id == selectedModelId
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                model.family + " · " + model.storageMb + " MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (installed) {
                            OutlinedButton(onClick = { onSelect(model.id) }, enabled = busyId == null) {
                                Text(if (selected) stringResource(R.string.active) else stringResource(R.string.select))
                            }
                        } else if (model.artifacts.isNotEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busyId = model.id
                                        runCatching {
                                            service.download(model) { done, total ->
                                                if (total > 0) progress = done.toFloat() / total.toFloat()
                                            }
                                        }.onSuccess {
                                            showMessage("Ready: " + model.displayName)
                                            refresh()
                                        }.onFailure {
                                            showMessage("Download issue: " + it.message)
                                        }
                                        busyId = null
                                        progress = 0f
                                    }
                                },
                                enabled = busyId == null,
                                shape = MaterialTheme.shapes.small
                            ) { Text(stringResource(R.string.download)) }
                        }
                    }
                    if (busyId == model.id) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
