package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CustomCloudProvider
import com.example.viewmodel.ScreenshotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val statusText by viewModel.analysisStatusText.collectAsStateWithLifecycle()
    val hasMediaPermissions by viewModel.hasMediaPermissions.collectAsStateWithLifecycle()

    val ocrEnabled by viewModel.ocrEnabled.collectAsStateWithLifecycle()
    val linksDetectionEnabled by viewModel.linksDetectionEnabled.collectAsStateWithLifecycle()
    val smartTagsEnabled by viewModel.smartTagsEnabled.collectAsStateWithLifecycle()
    val remindersDetectionEnabled by viewModel.remindersDetectionEnabled.collectAsStateWithLifecycle()
    val autoSyncDeviceMedia by viewModel.autoSyncDeviceMedia.collectAsStateWithLifecycle()
    val aiQualityPreset by viewModel.aiQualityPreset.collectAsStateWithLifecycle()
    val autoWriteExifSetting by viewModel.autoWriteExifSetting.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomCloudProvider?>(null) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }

    val untaggedCount = allScreenshots.count { !it.aiProcessed }
    val processedCount = allScreenshots.count { it.aiProcessed }
    val videoCount = allScreenshots.count { it.isVideo }
    val photoCount = allScreenshots.size - videoCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // SECTION 1: AI STUDIO & INTELLIGENCE ENGINE
            SectionHeader(icon = Icons.Default.AutoAwesome, title = "AI Studio & Intelligence")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Active Engine Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeProvider?.name ?: "Google Gemini (Official)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Model: ${activeProvider?.selectedModel ?: "gemini-2.5-flash"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Quality / Speed Preset
                    Column {
                        Text(
                            text = "Analysis Speed & Reasoning",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Fast", "Balanced", "Deep").forEach { preset ->
                                val isSelected = aiQualityPreset == preset
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setAiQualityPreset(preset) },
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // AI Vision Capabilities Toggles
                    Text(
                        text = "Vision Analysis Capabilities",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingToggleRow(
                        icon = Icons.Default.TextFields,
                        title = "OCR Text Extraction",
                        subtitle = "Transcribe on-screen text and documents",
                        checked = ocrEnabled,
                        onCheckedChange = { viewModel.setOcrEnabled(it) }
                    )

                    SettingToggleRow(
                        icon = Icons.Default.Language,
                        title = "URL & Link Detection",
                        subtitle = "Extract web links, QR codes, and domains",
                        checked = linksDetectionEnabled,
                        onCheckedChange = { viewModel.setLinksDetectionEnabled(it) }
                    )

                    SettingToggleRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Smart Keyword Tagging",
                        subtitle = "Generate categorized tags for rapid search",
                        checked = smartTagsEnabled,
                        onCheckedChange = { viewModel.setSmartTagsEnabled(it) }
                    )

                    SettingToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Actionable Reminders",
                        subtitle = "Detect dates, events, and suggest reminders",
                        checked = remindersDetectionEnabled,
                        onCheckedChange = { viewModel.setRemindersDetectionEnabled(it) }
                    )

                    SettingToggleRow(
                        icon = Icons.Default.CameraAlt,
                        title = "Write EXIF Directly",
                        subtitle = "Save AI tags directly to image metadata headers",
                        checked = autoWriteExifSetting,
                        onCheckedChange = { viewModel.setAutoWriteExifSetting(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Batch Indexing Actions
                    Text(
                        text = "Batch Library Indexing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isAnalyzing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                        Text(
                            text = statusText ?: "Processing AI request...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val untagged = allScreenshots.filter { !it.aiProcessed }
                                viewModel.batchAnalyzeScreenshots(untagged, autoWriteExifSetting)
                            },
                            enabled = !isAnalyzing && untaggedCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_settings_index_untagged")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Index ($untaggedCount)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.batchAnalyzeScreenshots(allScreenshots, autoWriteExifSetting)
                            },
                            enabled = !isAnalyzing && allScreenshots.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_settings_index_all")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-index All", fontSize = 12.sp)
                        }
                    }
                }
            }

            // SECTION 2: CLOUD API & CUSTOM PROVIDERS
            SectionHeader(icon = Icons.Default.CloudQueue, title = "Cloud API Providers")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Endpoints & Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${providers.size} configured provider(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                editingProvider = null
                                showEditDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("btn_add_provider_settings")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Provider", fontSize = 12.sp)
                        }
                    }

                    // Providers List
                    providers.forEach { provider ->
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
                }
            }

            // SECTION 3: MEDIA SCANNING & CACHE CONTROLS
            SectionHeader(icon = Icons.Default.Storage, title = "Storage & Scanning")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        icon = Icons.Default.Sync,
                        title = "Auto-sync Device Media",
                        subtitle = "Automatically index new photos and videos",
                        checked = autoSyncDeviceMedia,
                        onCheckedChange = { viewModel.setAutoSyncDeviceMedia(it) }
                    )

                    // Low-End Device Optimization Profile
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (viewModel.isLowRamDevice || viewModel.isLowEndDevice)
                                        "⚡ Low-RAM Optimization Active"
                                    else
                                        "⚡ Hardware Acceleration Active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "RGB_565 bitmap decoding (50% RAM savings), bounded cache & zero GC jank enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.syncDeviceMedia() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Media", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.trimMemory() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("btn_trim_memory")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trim RAM", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearThumbnailCache() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("btn_clear_cache")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Cache", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Grid Columns layout control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Gallery Grid Columns", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Adjust card size in media library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2, 3).forEach { cols ->
                                val isSelected = gridColumns == cols
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { viewModel.setGridColumns(cols) }
                                ) {
                                    Text(
                                        text = "${cols}x",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: DATABASE & STATS SUMMARY
            SectionHeader(icon = Icons.Default.Info, title = "Library Overview")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard("Photos", "$photoCount", Modifier.weight(1f))
                StatCard("Videos", "$videoCount", Modifier.weight(1f))
                StatCard("Indexed", "$processedCount", Modifier.weight(1f))
                StatCard("Collections", "${collections.size}", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(80.dp))
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
fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
