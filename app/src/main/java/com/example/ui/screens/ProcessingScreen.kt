package com.amresalehin.emreshots.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val screenshots = viewModel.allScreenshots.collectAsStateWithLifecycle().value
    val state = viewModel.indexingState.collectAsStateWithLifecycle().value
    val isAnalyzing = viewModel.isAnalyzing.collectAsStateWithLifecycle().value
    val status = viewModel.analysisStatusText.collectAsStateWithLifecycle().value
    val smartTagsEnabled = viewModel.smartTagsEnabled.collectAsStateWithLifecycle().value
    val autoSyncDeviceMedia = viewModel.autoSyncDeviceMedia.collectAsStateWithLifecycle().value

    val remaining = if (state.total > 0) (state.total - state.current).coerceAtLeast(0) else screenshots.count { !it.aiProcessed }
    val failedIds = viewModel.lastFailedScreenshotIds.collectAsStateWithLifecycle().value
    val failed = failedIds.size
    val completed = state.successCount
    val progress = if (state.total > 0) state.progress else if (screenshots.isEmpty()) 0f else screenshots.count { it.aiProcessed }.toFloat() / screenshots.size.toFloat()
    var showRetryInfo by remember { mutableStateOf(false) }

    if (onNavigateBack != null) BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.processing_library), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.processing_ai_analysis), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(if (state.isIndexing) stringResource(R.string.analyzing_library) else stringResource(R.string.library_ready_for_analysis), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (state.total > 0) stringResource(R.string.processing_progress, completed, failed, remaining) else stringResource(R.string.analysis_progress, screenshots.count { it.aiProcessed }, remaining), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                        if (!status.isNullOrBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (state.isIndexing || isAnalyzing) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.ai_analysis), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Text(state.currentModel.ifBlank { stringResource(R.string.ai_label) }, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(state.currentItemTitle.ifBlank { stringResource(R.string.preparing) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.processing_item, state.current.coerceAtLeast(0), state.total), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.cancelIndexing() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.isIndexing) {
                        val unprocessed = screenshots.filter { !it.aiProcessed }
                        Button(onClick = { viewModel.batchAnalyzeScreenshots(unprocessed) }, enabled = unprocessed.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.process_pending))
                            Text(stringResource(R.string.process_pending))
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.cancelIndexing() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Text(stringResource(R.string.stop))
                        }
                    }
                    OutlinedButton(onClick = { showRetryInfo = true }, enabled = failed > 0 && !state.isIndexing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry_failed))
                        Text(stringResource(R.string.retry_failed))
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.enrichment_and_sync), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.smart_tags), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.smart_tags_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = smartTagsEnabled,
                                onCheckedChange = viewModel::setSmartTagsEnabled
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.automatic_media_sync), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.automatic_media_sync_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSyncDeviceMedia,
                                onCheckedChange = viewModel::setAutoSyncDeviceMedia
                            )
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.queue_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.completed_count, completed))
                        Text(stringResource(R.string.failed_count, failed))
                        Text(stringResource(R.string.remaining_count, remaining))
                        if (!state.isIndexing && state.total > 0 && state.progress >= 1f) {
                            Text(stringResource(R.string.last_run_finished), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.processing_complete), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.processing_complete), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showRetryInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRetryInfo = false },
            title = { Text(stringResource(R.string.retry_failed)) },
            text = { Text(stringResource(R.string.retry_failed_description)) },
            confirmButton = {
                Button(onClick = {
                    showRetryInfo = false
                    viewModel.retryFailedItems()
                }) { Text(stringResource(R.string.retry_failed)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRetryInfo = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
