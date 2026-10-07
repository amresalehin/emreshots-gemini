package com.amresalehin.emreshots.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.service.ai.OnDeviceVisionCatalog
import com.amresalehin.emreshots.service.ai.OnDeviceVisionModel
import com.amresalehin.emreshots.service.ai.OnDeviceVisionService
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.service.ocr.TessLanguage
import com.amresalehin.emreshots.ui.components.OcrLanguageManagerSheet
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenProcessing: (() -> Unit)? = null
) {
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val indexingState by viewModel.indexingState.collectAsStateWithLifecycle()
    val ocrEnabled by viewModel.ocrEnabled.collectAsStateWithLifecycle()
    val ocrLanguage by viewModel.ocrLanguage.collectAsStateWithLifecycle()
    val installedOcrLanguages by viewModel.installedOcrLanguages.collectAsStateWithLifecycle()
    val ocrDownloadProgress by viewModel.ocrLanguageDownloadProgress.collectAsStateWithLifecycle()
    val linksDetectionEnabled by viewModel.linksDetectionEnabled.collectAsStateWithLifecycle()
    val smartTagsEnabled by viewModel.smartTagsEnabled.collectAsStateWithLifecycle()
    val autoWriteExifSetting by viewModel.autoWriteExifSetting.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val isScanningDuplicates by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()
    val aiQualityPreset by viewModel.aiQualityPreset.collectAsStateWithLifecycle()
    val onDeviceVisionMode by viewModel.onDeviceVisionMode.collectAsStateWithLifecycle()
    val onDeviceVisionModel by viewModel.onDeviceVisionModel.collectAsStateWithLifecycle()
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomCloudProvider?>(null) }
    var showRestoreModeDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showReprocessConfirm by remember { mutableStateOf(false) }
    var showLanguageManagerSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visionService = remember(context) { OnDeviceVisionService(context) }

    val total = allScreenshots.size
    val processed = allScreenshots.count { it.aiProcessed }
    val pending = (total - processed).coerceAtLeast(0)

    val tessDataPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment ?: "custom.traineddata"
            viewModel.importCustomOcrLanguage(uri, fileName)
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackupToUri(context, uri)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreModeDialog = true
        }
    }

    val ggufPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imported = visionService.importCustomGguf(uri)
                    viewModel.setOnDeviceVisionModel(imported.id)
                    viewModel.showMessage("Imported and selected ${imported.displayName}!")
                } catch (e: Exception) {
                    viewModel.showMessage("GGUF import failed: ${e.message}")
                }
            }
        }
    }

    if (onNavigateBack != null) BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card 1: Cloud AI & Quality
            item {
                SettingsSimpleCard(title = "Cloud AI Provider") {
                    SettingsRow(
                        icon = Icons.Default.CloudUpload,
                        title = activeProvider?.name ?: "Configure Provider",
                        subtitle = activeProvider?.let { "${it.selectedModel} · ${it.baseUrl}" } ?: "Gemini, Ollama, Groq or custom endpoint",
                        trailing = {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeProvider != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (activeProvider != null) "Connected" else "Set up",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeProvider != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        },
                        onClick = {
                            editingProvider = activeProvider
                            showEditDialog = true
                        }
                    )

                    SettingsSimpleDivider()

                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("Analysis Quality", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Balances speed and depth of AI captioning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        QualitySegmentedChoice(
                            options = listOf("Fast", "Balanced", "Deep"),
                            selected = aiQualityPreset,
                            onSelected = viewModel::setAiQualityPreset
                        )
                    }
                }
            }

            // Card 2: On-Device Vision & GGUF Models
            item {
                SettingsSimpleCard(title = "On-Device Vision (GGUF)") {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("Inference Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Choose when to run local GGUF models on-device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        QualitySegmentedChoice(
                            options = listOf("Automatic", "Force local", "Disabled"),
                            selected = onDeviceVisionMode,
                            onSelected = viewModel::setOnDeviceVisionMode
                        )
                    }

                    SettingsSimpleDivider()

                    // GGUF Import Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom GGUF Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Use any .gguf vision model from your device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { ggufPickerLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .gguf")
                        }
                    }

                    SettingsSimpleDivider()

                    // Available GGUF Models List
                    GgufModelsSimpleList(
                        visionService = visionService,
                        selectedModelId = onDeviceVisionModel,
                        onSelect = { viewModel.setOnDeviceVisionModel(it) },
                        showMessage = viewModel::showMessage
                    )
                }
            }

            // Card 3: Features & Metadata Toggles
            item {
                SettingsSimpleCard(title = "Metadata Enrichment") {
                    SettingsToggleRow(
                        icon = Icons.Default.TextFields,
                        title = "OCR Text Extraction",
                        subtitle = if (ocrEnabled) {
                            val currentLangName = TessLanguage.findByCode(ocrLanguage).englishName
                            "Extract text locally with Tesseract ($currentLangName) and automatic local AI cleanup"
                        } else "Recognize on-screen text",
                        checked = ocrEnabled,
                        onCheckedChange = { viewModel.setOcrEnabled(it) }
                    )

                    if (ocrEnabled) {
                        SettingsSimpleDivider()
                        val activeLang = TessLanguage.findByCode(ocrLanguage)
                        SettingsRow(
                            icon = Icons.Default.Translate,
                            title = "OCR Language: ${activeLang.displayName}",
                            subtitle = "${installedOcrLanguages.size} language pack${if (installedOcrLanguages.size != 1) "s" else ""} installed · Tap to manage or download",
                            trailing = {
                                FilledTonalButton(
                                    onClick = { showLanguageManagerSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Manage", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            onClick = { showLanguageManagerSheet = true }
                        )

                        SettingsSimpleDivider()
                        SettingsToggleRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Fix OCR Artefacts with Small LLM",
                            subtitle = "Repairs scanning glitches, broken words, and noise",
                            checked = fixOcrArtefactsEnabled,
                            onCheckedChange = { viewModel.setFixOcrArtefactsEnabled(it) }
                        )
                    }
                    SettingsSimpleDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Language,
                        title = "Link & URL Detection",
                        subtitle = "Extract web links from text",
                        checked = linksDetectionEnabled,
                        onCheckedChange = { viewModel.setLinksDetectionEnabled(it) }
                    )
                    SettingsSimpleDivider()
                    SettingsSimpleDivider()

                    // Backup & Restore
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { backupExportLauncher.launch("emreshots-backup.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore JSON")
                        }
                    }

                    SettingsSimpleDivider()

                    // Library Reprocess
                    SettingsRow(
                        icon = Icons.Default.Replay,
                        title = "Reprocess Library",
                        subtitle = if (pending > 0) "$pending items pending analysis" else "All $total items up to date",
                        trailing = {
                            TextButton(onClick = { showReprocessConfirm = true }, enabled = !isAnalyzing && total > 0) {
                                Text("Reprocess")
                            }
                        },
                        onClick = { showReprocessConfirm = true }
                    )
                }
            }

            // Simple App Footer
            item {
                Text(
                    "EmreShots 1.0 · Private & On-Device AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }

    if (showReprocessConfirm) {
        AlertDialog(
            onDismissRequest = { showReprocessConfirm = false },
            title = { Text("Reprocess Library?") },
            text = { Text("This will run AI analysis again for all $total items in your library.") },
            confirmButton = {
                Button(onClick = {
                    showReprocessConfirm = false
                    viewModel.batchAnalyzeScreenshots(allScreenshots, autoWriteExifSetting)
                }) {
                    Text("Reprocess all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReprocessConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreModeDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreModeDialog = false; pendingRestoreUri = null },
            title = { Text("Restore Backup") },
            text = { Text("Choose how to apply this backup.") },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingRestoreUri
                    showRestoreModeDialog = false
                    pendingRestoreUri = null
                    if (uri != null) viewModel.restoreFromUri(uri, RestoreMode.MERGE)
                }) { Text("Merge") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    val uri = pendingRestoreUri
                    showRestoreModeDialog = false
                    pendingRestoreUri = null
                    if (uri != null) viewModel.restoreFromUri(uri, RestoreMode.REPLACE)
                }) { Text("Replace") }
            }
        )
    }

    if (showEditDialog) {
        ProviderEditDialog(
            initial = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { saved ->
                viewModel.saveProvider(saved)
                showEditDialog = false
            },
            onFetchModels = { prov, onResult -> viewModel.fetchProviderModels(prov, onResult) },
            onTest = { prov, onResult -> viewModel.testProviderConnection(prov, onResult) }
        )
    }

    if (showLanguageManagerSheet) {
        OcrLanguageManagerSheet(
            activeLanguageCode = ocrLanguage,
            installedLanguageCodes = installedOcrLanguages,
            downloadProgressMap = ocrDownloadProgress,
            onSelectLanguage = { code ->
                viewModel.setOcrLanguage(code)
                showLanguageManagerSheet = false
            },
            onDownloadLanguage = { code ->
                viewModel.downloadOcrLanguage(code)
            },
            onDeleteLanguage = { code ->
                viewModel.deleteOcrLanguage(code)
            },
            onImportCustom = {
                tessDataPickerLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showLanguageManagerSheet = false }
        )
    }
}

@Composable
private fun SettingsSimpleCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun QualitySegmentedChoice(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isActive = selected.equals(option, ignoreCase = true)
            Surface(
                onClick = { onSelected(option) },
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = option,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSimpleDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun GgufModelsSimpleList(
    visionService: OnDeviceVisionService,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    showMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val allModels = remember { OnDeviceVisionCatalog.all() }

    suspend fun refresh() {
        installedIds = visionService.installedModels().map { it.model.id }.toSet()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Available GGUF Models", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        allModels.take(4).forEach { model ->
            val isInstalled = model.id in installedIds
            val isSelected = model.id == selectedModelId

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${model.family} · ${model.storageMb} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isInstalled) {
                            TextButton(
                                onClick = { onSelect(model.id) },
                                enabled = busyId == null
                            ) {
                                Text(if (isSelected) "Active" else "Select")
                            }
                        } else if (model.artifacts.isNotEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busyId = model.id
                                        downloadProgress = 0f
                                        runCatching {
                                            visionService.download(model) { done, tot ->
                                                if (tot > 0) downloadProgress = (done.toFloat() / tot.toFloat()).coerceIn(0f, 1f)
                                            }
                                        }.onSuccess {
                                            showMessage("Ready: ${model.displayName}")
                                            refresh()
                                        }.onFailure {
                                            showMessage("Download issue: ${it.message}")
                                        }
                                        busyId = null
                                    }
                                },
                                enabled = busyId == null,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Download", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (busyId == model.id) {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                    }
                }
            }
        }
    }
}
