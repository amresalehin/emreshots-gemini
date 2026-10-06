package com.amresalehin.emreshots.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.service.ai.OnDeviceVisionCatalog
import com.amresalehin.emreshots.service.ai.OnDeviceVisionService
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ScreenshotsViewModel, onNavigateBack: (() -> Unit)? = null, onOpenProcessing: (() -> Unit)? = null) {
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val statusText by viewModel.analysisStatusText.collectAsStateWithLifecycle()
    val indexingState by viewModel.indexingState.collectAsStateWithLifecycle()
    val ocrEnabled by viewModel.ocrEnabled.collectAsStateWithLifecycle()
    val linksDetectionEnabled by viewModel.linksDetectionEnabled.collectAsStateWithLifecycle()
    val smartTagsEnabled by viewModel.smartTagsEnabled.collectAsStateWithLifecycle()
    val autoSyncDeviceMedia by viewModel.autoSyncDeviceMedia.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val isScanningDuplicates by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()
    val aiQualityPreset by viewModel.aiQualityPreset.collectAsStateWithLifecycle()
    val autoWriteExifSetting by viewModel.autoWriteExifSetting.collectAsStateWithLifecycle()
    val isExtractingOcr by viewModel.isExtractingOcr.collectAsStateWithLifecycle()
    val ocrStatusText by viewModel.ocrStatusText.collectAsStateWithLifecycle()
    val hasMediaPermissions by viewModel.hasMediaPermissions.collectAsStateWithLifecycle()
    val hasAllMetadataPermissions by viewModel.hasAllMetadataPermissions.collectAsStateWithLifecycle()
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsStateWithLifecycle()
    val onDeviceVisionMode by viewModel.onDeviceVisionMode.collectAsStateWithLifecycle()
    val onDeviceVisionModel by viewModel.onDeviceVisionModel.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomCloudProvider?>(null) }
    var showRestoreModeDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showReprocessConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) viewModel.exportBackupToUri(context, uri) }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { pendingRestoreUri = uri; showRestoreModeDialog = true } }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> viewModel.onPermissionsResult(result.values.any { it }) }

    val total = allScreenshots.size
    val processed = allScreenshots.count { it.aiProcessed }
    val pending = (total - processed).coerceAtLeast(0)
    val ocrEligible = allScreenshots.count { !it.isVideo }
    val ocrPending = allScreenshots.count { !it.isVideo && it.ocrText.isNullOrBlank() }
    val failed = indexingState.failureCount
    val progress: Float = if (indexingState.total > 0) indexingState.progress else if (total == 0) 0f else processed.toFloat() / total.toFloat()

    if (onNavigateBack != null) BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { if (onNavigateBack != null) IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LibraryIntelligenceCard(
                    total = total, processed = processed, pending = pending, failed = failed, progress = progress,
                    providerName = activeProvider?.name, quality = aiQualityPreset, isProcessing = isAnalyzing || indexingState.isIndexing,
                    onPrimary = { if (isAnalyzing || indexingState.isIndexing) viewModel.cancelIndexing() else if (pending > 0) viewModel.batchAnalyzeScreenshots(allScreenshots.filter { !it.aiProcessed }, autoWriteExifSetting) else showReprocessConfirm = true },
                    onOpenProcessing = onOpenProcessing
                )
            }
            item {
                SectionTitle("QUICK ACTIONS", "Global controls for your screenshot library")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionButton(Icons.Default.AutoAwesome, "Process", enabled = !isAnalyzing && pending > 0, modifier = Modifier.weight(1f)) { viewModel.batchAnalyzeScreenshots(allScreenshots.filter { !it.aiProcessed }, autoWriteExifSetting) }
                    QuickActionButton(Icons.Default.Sync, "Sync", modifier = Modifier.weight(1f)) { viewModel.syncDeviceMedia() }
                    QuickActionButton(Icons.Default.ContentCopy, "Duplicates", enabled = !isScanningDuplicates, modifier = Modifier.weight(1f)) { viewModel.scanDuplicates() }
                    QuickActionButton(Icons.Default.Replay, "Re-index", enabled = !isAnalyzing && total > 0, modifier = Modifier.weight(1f)) { showReprocessConfirm = true }
                }
            }
            item {
                SectionTitle("LOCAL OCR", "Extract text independently without running AI, vision, tagging, links, or EXIF")
                SettingsCard {
                    Text(
                        text = ocrPending.toString() + " images have no OCR text yet · " + ocrEligible + " images are OCR-capable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.batchExtractOcr(allScreenshots, onlyMissing = true) },
                            enabled = !isExtractingOcr && ocrPending > 0,
                            modifier = Modifier.weight(1f).testTag("btn_local_ocr_pending")
                        ) {
                            if (isExtractingOcr) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("OCR pending")
                        }
                        OutlinedButton(
                            onClick = { viewModel.batchExtractOcr(allScreenshots, onlyMissing = false) },
                            enabled = !isExtractingOcr && ocrEligible > 0,
                            modifier = Modifier.weight(1f).testTag("btn_local_ocr_all")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("OCR all")
                        }
                    }
                    if (ocrStatusText != null) {
                        Text(ocrStatusText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                SectionTitle("AI & ANALYSIS", "Choose how EmreShots understands your screenshots")
                SettingsCard {
                    if (activeProvider != null) {
                        SettingsHeaderRow(Icons.Default.Psychology, "Cloud AI", activeProvider!!.name, "Active") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editingProvider = activeProvider; showEditDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Edit", fontSize = 11.sp) }
                                Button(onClick = { editingProvider = null; showEditDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.testTag("btn_add_provider_settings")) { Text("Add", fontSize = 11.sp) }
                            }
                        }
                    } else {
                        SettingsHeaderRow(Icons.Default.Psychology, "Cloud AI", "No provider configured") {
                            Button(onClick = { editingProvider = null; showEditDialog = true }, modifier = Modifier.testTag("btn_add_provider_settings")) { Text("Set up", fontSize = 11.sp) }
                        }
                    }
                    HorizontalDivider()
                    Text("Quality", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    SegmentedChoice(listOf("Fast", "Balanced", "Deep"), aiQualityPreset) { viewModel.setAiQualityPreset(it) }
                    Spacer(Modifier.height(10.dp))
                    Text("On-device vision", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    SegmentedChoice(listOf("Automatic", "Force local", "Disabled"), onDeviceVisionMode) { viewModel.setOnDeviceVisionMode(it) }
                    Text("Selected model: " + onDeviceVisionModel.ifBlank { "Automatic" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LocalVisionModelsCard(
                        selectedModelId = onDeviceVisionModel,
                        onSelect = { viewModel.setOnDeviceVisionModel(it) },
                        showMessage = viewModel::showMessage
                    )
                    Spacer(Modifier.height(6.dp))
                    CompactToggleRow(Icons.Default.TextFields, "OCR text extraction", ocrEnabled) { viewModel.setOcrEnabled(it) }
                    CompactToggleRow(Icons.Default.Language, "URL & link detection", linksDetectionEnabled) { viewModel.setLinksDetectionEnabled(it) }
                    CompactToggleRow(Icons.Default.AutoAwesome, "Smart keyword tagging", smartTagsEnabled) { viewModel.setSmartTagsEnabled(it) }
                    CompactToggleRow(Icons.Default.CameraAlt, "Write metadata to EXIF", autoWriteExifSetting) { viewModel.setAutoWriteExifSetting(it) }
                }
            }
            item {
                SectionTitle("LIBRARY", "Keep your collection fresh and easy to scan")
                SettingsCard {
                    CompactToggleRow(Icons.Default.Sync, "Background media sync", autoSyncDeviceMedia) { viewModel.setAutoSyncDeviceMedia(it) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.syncDeviceMedia() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Sync now") }
                        OutlinedButton(onClick = { viewModel.trimMemory() }, modifier = Modifier.weight(1f).testTag("btn_trim_memory")) { Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Trim RAM") }
                        OutlinedButton(onClick = { viewModel.clearThumbnailCache() }, modifier = Modifier.weight(1f).testTag("btn_clear_cache")) { Text("Cache") }
                    }
                    Text("Gallery layout is controlled from View & Organize in the gallery. This keeps grid, masonry, columns, sorting, and grouping in one place.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionTitle("DUPLICATES", "${duplicateGroups.sumOf { it.items.size - 1 }} duplicate items found")
                SettingsCard {
                    Button(onClick = { viewModel.scanDuplicates() }, enabled = !isScanningDuplicates && total > 0, modifier = Modifier.fillMaxWidth()) {
                        if (isScanningDuplicates) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (isScanningDuplicates) "Scanning…" else "Scan for duplicates")
                    }
                    if (duplicateGroups.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Latest results", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        duplicateGroups.take(3).forEach { group ->
                            Text("• ${group.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${group.items.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionTitle("ACCESS & BACKUP", "Protect your library and keep permissions clear")
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { PermissionChip("Photos", hasMediaPermissions); PermissionChip("Metadata", hasAllMetadataPermissions) }
                    OutlinedButton(
                        onClick = {
                            val permissions = when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
                                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }; mediaPermissionLauncher.launch(permissions)
                        },
                        enabled = !hasAllMetadataPermissions, modifier = Modifier.fillMaxWidth()
                    ) { Text(if (hasMediaPermissions) "Grant metadata access" else "Grant media access") }
                    HorizontalDivider()
                    Text("Backups include library metadata, collections, providers without plaintext API keys, and settings. Media files are not copied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastBackupInfo != null) Text(lastBackupInfo!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { backupExportLauncher.launch("emreshots-backup.json") }, modifier = Modifier.weight(1f)) { Text("Export") }
                        OutlinedButton(onClick = { viewModel.shareBackup(context) }, modifier = Modifier.weight(1f)) { Text("Share") }
                        Button(onClick = { backupRestoreLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("Restore") }
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("EmreShots", fontWeight = FontWeight.Bold)
                        Text("1.0 • Screenshot Intelligence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showReprocessConfirm) {
        AlertDialog(
            onDismissRequest = { showReprocessConfirm = false },
            title = { Text("Reprocess entire library?") },
            text = { Text("This will run AI analysis again for all $total media items and may take time or use cloud AI quota.") },
            confirmButton = { Button(onClick = { showReprocessConfirm = false; viewModel.batchAnalyzeScreenshots(allScreenshots, autoWriteExifSetting) }, enabled = total > 0) { Text("Reprocess all") } },
            dismissButton = { TextButton(onClick = { showReprocessConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showRestoreModeDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreModeDialog = false; pendingRestoreUri = null },
            title = { Text("Restore backup") },
            text = { Text("Choose how this backup should affect the current library.") },
            confirmButton = { Button(onClick = { val uri = pendingRestoreUri; showRestoreModeDialog = false; pendingRestoreUri = null; if (uri != null) viewModel.restoreFromUri(uri, RestoreMode.MERGE) }) { Text("Merge") } },
            dismissButton = { OutlinedButton(onClick = { val uri = pendingRestoreUri; showRestoreModeDialog = false; pendingRestoreUri = null; if (uri != null) viewModel.restoreFromUri(uri, RestoreMode.REPLACE) }) { Text("Replace") } }
        )
    }

    if (showEditDialog) {
        ProviderEditDialog(
            initial = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { saved -> viewModel.saveProvider(saved); showEditDialog = false },
            onFetchModels = { prov, onResult -> viewModel.fetchProviderModels(prov, onResult) },
            onTest = { prov, onResult -> viewModel.testProviderConnection(prov, onResult) }
        )
    }
}

@Composable private fun LibraryIntelligenceCard(total: Int, processed: Int, pending: Int, failed: Int, progress: Float, providerName: String?, quality: String, isProcessing: Boolean, onPrimary: () -> Unit, onOpenProcessing: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {
                    BoxCenter { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(10.dp))
                Column { Text("LIBRARY INTELLIGENCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("$total screenshots", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("READY", processed); StatBlock("REMAINING", pending); if (failed > 0) StatBlock("FAILED", failed)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            Text("${(progress * 100).toInt()}% analyzed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("${providerName ?: "No cloud provider"} · $quality · On-device ${if (isProcessing) "processing active" else "ready"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.weight(1f).testTag("btn_library_primary")) { Text(if (isProcessing) "Cancel Processing" else if (pending > 0) "Process $pending" else "Reprocess Library") }
                if (onOpenProcessing != null) OutlinedButton(onClick = onOpenProcessing, modifier = Modifier.weight(1f)) { Text("View progress") }
            }
        }
    }
}

@Composable private fun StatBlock(label: String, value: Int) { Column { Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun LocalVisionModelsCard(
    selectedModelId: String,
    onSelect: (String) -> Unit,
    showMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(context) { OnDeviceVisionService(context) }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var storageBytes by remember { mutableStateOf(0L) }
    var recommendedId by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }

    suspend fun refresh() {
        installedIds = service.installedModels().map { it.model.id }.toSet()
        storageBytes = service.storageUsageBytes()
        recommendedId = runCatching { service.recommendation().recommended?.id }.getOrNull()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Local vision models", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Download a verified on-device vision model. Nothing is downloaded until you choose it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Local model storage: " + (storageBytes / 1024 / 1024) + " MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OnDeviceVisionCatalog.all().forEach { model ->
            val installed = model.id in installedIds
            val recommended = model.id == recommendedId
            val canDownload = model.artifacts.isNotEmpty() && model.artifacts.none { it.sha256 == "UNVERIFIED" }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (model.id == selectedModelId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                model.family + " · " + model.storageMb + " MB · RAM ≥ " + model.minRamMb + " MB" + if (recommended) " · Recommended" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (installed) {
                            TextButton(onClick = { onSelect(model.id) }, enabled = busyId == null) {
                                Text(if (model.id == selectedModelId) "Selected" else "Use")
                            }
                        }
                    }
                    if (busyId == model.id) {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (installed) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busyId = model.id
                                        runCatching { service.delete(model) }
                                            .onSuccess {
                                                if (selectedModelId == model.id) onSelect("auto")
                                                showMessage("Removed " + model.displayName)
                                                refresh()
                                            }
                                            .onFailure { showMessage("Could not remove " + model.displayName + ": " + (it.message ?: "unknown error")) }
                                        busyId = null
                                    }
                                },
                                enabled = busyId == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("Remove") }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busyId = model.id
                                        downloadProgress = 0f
                                        runCatching {
                                            service.download(model) { done, total ->
                                                if (total > 0) downloadProgress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            }
                                        }.onSuccess {
                                            showMessage("Installed " + model.displayName)
                                            refresh()
                                        }.onFailure {
                                            showMessage("Model download failed: " + (it.message ?: "unknown error"))
                                        }
                                        busyId = null
                                    }
                                },
                                enabled = busyId == null && canDownload,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (canDownload) "Download" else "Unavailable") }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun QuickActionButton(icon: ImageVector, label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 11.sp) }
}

@Composable private fun SectionTitle(title: String, subtitle: String) { Column(modifier = Modifier.padding(horizontal = 2.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.7.sp); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun SettingsCard(content: @Composable () -> Unit) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth(), content = { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }) }

@Composable private fun SettingsHeaderRow(icon: ImageVector, title: String, subtitle: String, badge: String? = null, trailing: @Composable () -> Unit) { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) { BoxCenter { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, fontWeight = FontWeight.SemiBold); if (badge != null) { Spacer(Modifier.width(6.dp)); Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }; Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }; trailing() } }

@Composable private fun SegmentedChoice(options: List<String>, selected: String, onSelected: (String) -> Unit) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { option -> val active = selected.equals(option, true); Surface(onClick = { onSelected(option) }, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(option, textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(vertical = 9.dp)) } } } }

@Composable private fun CompactToggleRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange) } }

@Composable private fun PermissionChip(label: String, granted: Boolean) { Surface(shape = RoundedCornerShape(10.dp), color = if (granted) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) { Text(if (granted) "$label · Granted" else "$label · Needed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) } }

@Composable private fun BoxCenter(content: @Composable () -> Unit) { androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
