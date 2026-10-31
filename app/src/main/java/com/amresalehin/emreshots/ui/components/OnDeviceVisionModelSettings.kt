package com.amresalehin.emreshots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emreshots.service.ai.OnDeviceVisionCatalog
import com.amresalehin.emreshots.service.ai.OnDeviceVisionModel
import com.amresalehin.emreshots.service.ai.OnDeviceVisionService
import kotlinx.coroutines.launch

@Composable
fun OnDeviceVisionModelSettings(
    selectedModelId: String,
    onSelect: (String) -> Unit,
    showMessage: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { OnDeviceVisionService(context) }
    var recommendation by remember { mutableStateOf<com.amresalehin.emreshots.service.ai.OnDeviceModelRecommendation?>(null) }
    var installed by remember { mutableStateOf(emptySet<String>()) }
    var storage by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        recommendation = service.recommendation()
        installed = service.installedModels().map { it.model.id }.toSet()
        storage = service.storageUsageBytes()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("On-device Vision Model", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        recommendation?.let {
            Text("Device tier: " + it.tier.name.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase) +
                " • " + (it.explanation.ifBlank { "Select a compatible model below." }))
            it.recommended?.let { m -> Text("Recommended: " + m.displayName + " • " + m.storageMb + " MB • RAM ≥ " + m.minRamMb + " MB") }
        }
        Text("Storage used by local models: " + (storage / 1024 / 1024) + " MB")
        OnDeviceVisionCatalog.all().forEach { model ->
            val isInstalled = model.id in installed
            val isRecommended = model.id == recommendation?.recommended?.id
            val blocked = recommendation?.blocked?.firstOrNull { it.first.id == model.id }?.second
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text((if (isRecommended) "★ " else "") + model.displayName)
                Text(model.family + " • " + model.parameterCount + " • " + model.quantization +
                    " • " + model.storageMb + " MB • RAM ≥ " + model.minRamMb + " MB")
                Text("Capabilities: " + model.capabilities.joinToString { it.name.lowercase().replace('_',' ') })
                Text("Runtime: " + model.runtime.name.replace('_',' ') + " • " + model.acceleratorSupport)
                if (blocked != null) Text("Not recommended: " + blocked)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isInstalled) {
                        Button(onClick = { onSelect(model.id) }) { Text(if (selectedModelId == model.id) "Selected" else "Select") }
                        OutlinedButton(onClick = {
                            scope.launch { service.delete(model); installed = service.installedModels().map { it.model.id }.toSet(); storage = service.storageUsageBytes(); if (selectedModelId == model.id) onSelect("auto") }
                        }) { Text("Remove") }
                    } else if (model.artifacts.isNotEmpty() && blocked == null) {
                        Button(enabled = busy == null, onClick = {
                            busy = model.id
                            scope.launch {
                                runCatching { service.download(model) }.onSuccess { showMessage("Installed " + model.displayName); installed = service.installedModels().map { it.model.id }.toSet(); storage = service.storageUsageBytes() }
                                    .onFailure { showMessage("Model download failed: " + (it.message ?: "unknown error")) }
                                busy = null
                            }
                        }) { Text(if (busy == model.id) "Downloading…" else "Download") }
                    }
                }
            }
        }
        Text("Local-only analysis never sends the screenshot to a cloud provider. Models are stored outside the screenshot database and can be removed independently.")
    }
}
