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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import com.amresalehin.emreshots.service.ai.LocalGgufDiscovery
import com.amresalehin.emreshots.data.local.AppPreferences
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
    val ocrLanguages by viewModel.ocrLanguages.collectAsStateWithLifecycle()
    val hasMediaPermissions by viewModel.hasMediaPermissions.collectAsStateWithLifecycle()
    val hasAllMetadataPermissions by viewModel.hasAllMetadataPermissions.collectAsStateWithLifecycle()
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsStateWithLifecycle()
    val onDeviceVisionMode by viewModel.onDeviceVisionMode.collectAsStateWithLifecycle()
    val onDeviceVisionModel by viewModel.onDeviceVisionModel.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val ocrEnrichmentProviderId by viewModel.ocrEnrichmentProviderId.collectAsStateWithLifecycle()
    val visionCaptionTagProviderId by viewModel.visionCaptionTagProviderId.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val showFileNames by viewModel.showFileNames.collectAsStateWithLifecycle()
    val showTags by viewModel.showTags.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomCloudProvider?>(null) }
    var providerDialogFunction by remember { mutableStateOf<String?>(null) }
    var showRestoreModeDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showReprocessConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    var localGgufModels by remember { mutableStateOf(emptyList<com.amresalehin.emreshots.service.ai.LocalGgufModel>()) }
    val context = LocalContext.current
    val ggufDiscovery = remember(context) { LocalGgufDiscovery(context) }
    val preferences = remember(context) { AppPreferences(context) }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) viewModel.exportBackupToUri(context, uri) }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { pendingRestoreUri = uri; showRestoreModeDialog = true } }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> viewModel.onPermissionsResult(result.values.any { it }) }
    val ggufFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val current = runCatching { kotlinx.coroutines.runBlocking { preferences.localGgufFolders.first() } }.getOrDefault(emptyList())
            val folders = (current + uri.toString()).distinct()
            kotlinx.coroutines.runBlocking { preferences.setLocalGgufFolders(folders) }
            kotlinx.coroutines.runBlocking { localGgufModels = ggufDiscovery.scan(folders.map(android.net.Uri::parse)) }
        }
    }

    val total = allScreenshots.size
    val processed = allScreenshots.count { it.aiProcessed }
    val pending = (total - processed).coerceAtLeast(0)
    val ocrEligible = allScreenshots.count { !it.isVideo }
    val ocrPending = allScreenshots.count { !it.isVideo && it.ocrText.isNullOrBlank() }
    val failed = indexingState.failureCount
    val progress: Float = if (indexingState.total > 0) indexingState.progress else if (total == 0) 0f else processed.toFloat() / total.toFloat()

    if (onNavigateBack != null) BackHandler(onBack = onNavigateBack)


    LaunchedEffect(Unit) {
        val folders = preferences.localGgufFolders.first().map(android.net.Uri::parse)
        localGgufModels = ggufDiscovery.scan(folders)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding()
        ) {
            SettingsTabRow(selectedTab) { selectedTab = it }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    SettingsTab.GENERAL -> {
                        item {
                            SettingsSection("Workspace", "Keep the gallery clean and focused") {
                                CompactToggleRow(Icons.Default.Sync, "Auto-sync device media", autoSyncDeviceMedia) { viewModel.setAutoSyncDeviceMedia(it) }
                                SettingsDivider()
                                CompactToggleRow(Icons.Default.TextFields, "Show file names", showFileNames) { viewModel.setShowFileNames(it) }
                                SettingsDivider()
                                CompactToggleRow(Icons.Default.AutoAwesome, "Show tags", showTags) { viewModel.setShowTags(it) }
                                SettingsDivider()
                                SettingsChoiceRow(
                                    icon = Icons.Default.Settings,
                                    title = "Grid density",
                                    subtitle = "Columns in the gallery",
                                    options = listOf("2", "3", "4", "5"),
                                    selected = gridColumns.toString(),
                                    onSelected = { viewModel.setGridColumns(it.toInt()) }
                                )
                            }
                        }
                        item {
                            SettingsSection("Metadata", "Choose what EmreShots adds to media") {
                                CompactToggleRow(Icons.Default.Language, "URL & link detection", linksDetectionEnabled) { viewModel.setLinksDetectionEnabled(it) }
                                SettingsDivider()
                                CompactToggleRow(Icons.Default.AutoAwesome, "Smart keyword tagging", smartTagsEnabled) { viewModel.setSmartTagsEnabled(it) }
                                SettingsDivider()
                                CompactToggleRow(Icons.Default.CameraAlt, "Write metadata to EXIF", autoWriteExifSetting) { viewModel.setAutoWriteExifSetting(it) }
                            }
                        }
                    }

                    SettingsTab.AI -> {
                        item {
                            SettingsSection("AI provider", "Choose the cloud endpoint used when local processing is not selected") {
                                SettingsNavigationRow(
                                    icon = Icons.Default.Psychology,
                                    title = "Cloud AI",
                                    subtitle = activeProvider?.name ?: "No provider configured",
                                    value = if (activeProvider != null) "Active" else "Set up",
                                    onClick = { editingProvider = activeProvider; showEditDialog = true },
                                    valueIsPrimary = activeProvider != null
                                )
                                SettingsDivider()
                                SettingsChoiceRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "Analysis quality",
                                    subtitle = "Controls speed, detail and cloud usage",
                                    options = listOf("Fast", "Balanced", "Deep"),
                                    selected = aiQualityPreset,
                                    onSelected = viewModel::setAiQualityPreset
                                )
                                SettingsDivider()
                                SettingsChoiceRow(
                                    icon = Icons.Default.Psychology,
                                    title = "On-device vision",
                                    subtitle = onDeviceVisionModel.ifBlank { "Automatic model selection" },
                                    options = listOf("Automatic", "Force local", "Disabled"),
                                    selected = onDeviceVisionMode,
                                    onSelected = viewModel::setOnDeviceVisionMode
                                )
                            }
                        }
                        item {
                            SettingsSection("Local models", "Verified downloads plus models you already have on the device") {
                                LocalVisionModelsCard(
                                    selectedModelId = onDeviceVisionModel,
                                    onSelect = { viewModel.setOnDeviceVisionModel(it) },
                                    showMessage = viewModel::showMessage
                                )
                                SettingsDivider()
                                Text("Local GGUF discovery", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Choose a folder containing .gguf files. Files remain where they are; EmreShots only reads them for discovery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { ggufFolderLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Choose GGUF folder") }
                                if (localGgufModels.isNotEmpty()) {
                                    localGgufModels.take(12).forEach { model ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.displayName, fontWeight = FontWeight.Medium)
                                                Text(
                                                    (if (model.isProjector) "Projector" else "GGUF model") + " · " + (model.sizeBytes / 1024 / 1024) + " MB",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("No local GGUF files discovered yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            SettingsSection("Function-specific models", "Use a different local or cloud model for each job") {
                                FunctionModelPicker(
                                    title = "OCR enrichment",
                                    subtitle = "Turn extracted OCR into title, description, tags and links.",
                                    selected = ocrEnrichmentProviderId,
                                    providers = providers,
                                    onSelect = viewModel::setOcrEnrichmentProviderId,
                                    onAddCustom = {
                                        providerDialogFunction = "ocr"
                                        editingProvider = null
                                        showEditDialog = true
                                    }
                                )
                                SettingsDivider()
                                FunctionModelPicker(
                                    title = "VLM captioning & tagging",
                                    subtitle = "Choose the model used for image understanding, captions and smart tags.",
                                    selected = visionCaptionTagProviderId,
                                    providers = providers,
                                    onSelect = viewModel::setVisionCaptionTagProviderId,
                                    onAddCustom = {
                                        providerDialogFunction = "vision"
                                        editingProvider = null
                                        showEditDialog = true
                                    }
                                )
                            }
                        }
                        item {
                            SettingsSection("OCR", "Language-first local OCR. Select one or more languages.") {
                                CompactToggleRow(Icons.Default.TextFields, "Local OCR extraction", ocrEnabled) { viewModel.setOcrEnabled(it) }
                                SettingsDivider()
                                Text("Supported languages", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "These are language choices, not script choices. Language packs are downloaded once and OCR then runs fully on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                com.amresalehin.emreshots.service.ocr.LocalOcrService.supportedLanguages.forEach { language ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            val next = if (ocrLanguages.contains(language.name)) ocrLanguages - language.name else ocrLanguages + language.name
                                            viewModel.setOcrLanguages(next)
                                        }.padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = ocrLanguages.contains(language.name),
                                            onCheckedChange = { checked ->
                                                val next = if (checked) ocrLanguages + language.name else ocrLanguages - language.name
                                                viewModel.setOcrLanguages(next)
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(language.name, fontWeight = FontWeight.Medium)
                                            Text(language.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (ocrStatusText != null) {
                                    Text(ocrStatusText!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.batchExtractOcr(allScreenshots, true) },
                                        enabled = !isExtractingOcr && ocrPending > 0,
                                        modifier = Modifier.weight(1f).testTag("btn_local_ocr_pending")
                                    ) {
                                        Text(if (isExtractingOcr) "Processing…" else "Process pending")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.batchExtractOcr(allScreenshots, false) },
                                        enabled = !isExtractingOcr && ocrEligible > 0,
                                        modifier = Modifier.weight(1f).testTag("btn_local_ocr_all")
                                    ) { Text("Run all") }
                                }
                            }
                        }
                    }

                    SettingsTab.DATA_BACKUP -> {
                        item {
                            SettingsSection("Data & sync", "Manage the library data EmreShots keeps locally") {
                                CompactToggleRow(Icons.Default.Sync, "Auto-sync device media", autoSyncDeviceMedia) { viewModel.setAutoSyncDeviceMedia(it) }
                                SettingsDivider()
                                SettingsActionRow(
                                    icon = Icons.Default.ContentCopy,
                                    title = if (isScanningDuplicates) "Scanning library…" else "Scan for duplicates",
                                    subtitle = if (duplicateGroups.isEmpty()) "Find visually or structurally similar media" else duplicateGroups.sumOf { it.items.size - 1 }.toString() + " duplicate items found",
                                    enabled = !isScanningDuplicates && total > 0,
                                    onClick = viewModel::scanDuplicates
                                )
                                if (duplicateGroups.isNotEmpty()) {
                                    SettingsDivider()
                                    Text("Latest duplicate groups", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    duplicateGroups.take(3).forEach { group ->
                                        Text("• " + group.kind.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + group.items.size + " items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSection("Permissions", "EmreShots needs access to read and update your media library") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PermissionChip("Media", hasMediaPermissions)
                                    PermissionChip("Metadata", hasAllMetadataPermissions)
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            mediaPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                                        } else {
                                            mediaPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (hasMediaPermissions) "Review media access" else "Grant media access") }
                            }
                        }
                        item {
                            SettingsSection("Backup & restore", "Export your library metadata and settings to a portable JSON file") {
                                Text(lastBackupInfo ?: "No backup recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { backupExportLauncher.launch("emreshots-backup.json") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Export backup") }
                                    OutlinedButton(
                                        onClick = { backupRestoreLauncher.launch(arrayOf("application/json", "text/json")) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Restore") }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("API keys are not written to exported backups.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            SettingsSection("Library processing", "Keep expensive work separate from the rest of your settings") {
                                SettingsActionRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = if (isAnalyzing) "Processing library…" else "Process pending",
                                    subtitle = if (pending > 0) pending.toString() + " items are waiting for analysis" else "Everything is analyzed",
                                    enabled = !isAnalyzing && pending > 0,
                                    onClick = { viewModel.batchAnalyzeScreenshots(allScreenshots.filter { !it.aiProcessed }, autoWriteExifSetting) }
                                )
                                SettingsDivider()
                                SettingsActionRow(
                                    icon = Icons.Default.Replay,
                                    title = "Reprocess library",
                                    subtitle = "Run AI analysis again for all " + total + " items",
                                    enabled = !isAnalyzing && total > 0,
                                    onClick = { showReprocessConfirm = true }
                                )
                                if (failed > 0) {
                                    SettingsDivider()
                                    Text(failed.toString() + " items failed during the last run", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
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
            onSave = { saved ->
                viewModel.saveProvider(saved)
                when (providerDialogFunction) {
                    "ocr" -> viewModel.setOcrEnrichmentProviderId("cloud:" + saved.id)
                    "vision" -> viewModel.setVisionCaptionTagProviderId("cloud:" + saved.id)
                }
                providerDialogFunction = null
                showEditDialog = false
            },
            onFetchModels = { prov, onResult -> viewModel.fetchProviderModels(prov, onResult) },
            onTest = { prov, onResult -> viewModel.testProviderConnection(prov, onResult) }
        )
    }
}



private enum class SettingsTab(val label: String) {
    GENERAL("General"), AI("AI"), DATA_BACKUP("Data & Backup")
}

@Composable
private fun SettingsTabRow(selected: SettingsTab, onSelected: (SettingsTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab.entries.forEach { tab ->
            Surface(
                onClick = { onSelected(tab) },
                shape = RoundedCornerShape(12.dp),
                color = if (tab == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (tab == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            ) { Text(tab.label, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 9.dp)) }
        }
    }
}

@Composable
private fun SettingsOverviewCard(
    total: Int, processed: Int, pending: Int, failed: Int, progress: Float,
    providerName: String?, isProcessing: Boolean, onPrimary: () -> Unit, onOpenProcessing: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                    BoxCenter { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Library overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isProcessing) "Processing your screenshot library…" else if (pending > 0) "${pending} items are ready to process" else "Your library is up to date",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("TOTAL", total); StatBlock("READY", processed); StatBlock("PENDING", pending)
                if (failed > 0) StatBlock("FAILED", failed)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            )
            Text(
                "${(progress * 100).toInt()}% analyzed · ${providerName ?: "No cloud AI configured"}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.weight(1f).testTag("btn_library_primary")) {
                    Text(if (isProcessing) "Cancel" else if (pending > 0) "Process ${pending}" else "Reprocess")
                }
                if (onOpenProcessing != null) {
                    OutlinedButton(onClick = onOpenProcessing, modifier = Modifier.weight(1f)) { Text("View progress") }
                }
            }
        }
    }
}

@Composable private fun StatBlock(label: String, value: Int) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsNavigationRow(icon: ImageVector, title: String, subtitle: String, value: String, onClick: () -> Unit, valueIsPrimary: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
            BoxCenter { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = if (valueIsPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
            BoxCenter {
                if (title.contains("Scanning") || title.contains("Processing")) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsChoiceRow(icon: ImageVector, title: String, subtitle: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                BoxCenter { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SegmentedChoice(options, selected, onSelected)
    }
}

@Composable private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

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

@Composable
private fun FunctionModelPicker(
    title: String,
    subtitle: String,
    selected: String,
    providers: List<CustomCloudProvider>,
    onSelect: (String) -> Unit,
    onAddCustom: () -> Unit
) {
    val context = LocalContext.current
    val service = remember(context) { OnDeviceVisionService(context) }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        installedIds = runCatching { service.installedModels().map { it.model.id }.toSet() }.getOrDefault(emptySet())
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Offline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        OnDeviceVisionCatalog.all().forEach { model ->
            val id = "local:" + model.id
            val installed = model.id in installedIds
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = installed) { onSelect(id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == id, onClick = { if (installed) onSelect(id) }, enabled = installed)
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        if (installed) model.parameterCount + " · " + model.quantization + " · " + model.storageMb + " MB · Ready"
                        else model.parameterCount + " · " + model.quantization + " · " + model.storageMb + " MB · Download in Local vision models",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text("Cloud / custom", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        providers.forEach { provider ->
            val id = "cloud:" + provider.id
            val configured = provider.apiKey.isNotBlank()
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = configured) { onSelect(id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == id, onClick = { if (configured) onSelect(id) }, enabled = configured)
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, fontWeight = FontWeight.Medium)
                    Text(provider.selectedModel + " · " + provider.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        OutlinedButton(onClick = onAddCustom, modifier = Modifier.fillMaxWidth()) {
            Text("Add custom URL & API key")
        }
    }
}

@Composable
private fun SegmentedChoice(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active = selected.equals(option, true)
            Surface(
                onClick = { onSelected(option) },
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(option, textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun CompactToggleRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp)
        ) {
            BoxCenter {
                Icon(icon, contentDescription = null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable private fun PermissionChip(label: String, granted: Boolean) { Surface(shape = RoundedCornerShape(10.dp), color = if (granted) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) { Text(if (granted) "$label · Granted" else "$label · Needed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) } }

@Composable private fun BoxCenter(content: @Composable () -> Unit) { androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
