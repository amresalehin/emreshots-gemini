package com.amresalehin.emreshots.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val remaining = if (state.total > 0) (state.total - state.current).coerceAtLeast(0) else screenshots.count { !it.aiProcessed }
    val failed = state.failureCount
    val completed = state.successCount
    val progress = if (state.total > 0) state.progress else if (screenshots.isEmpty()) 0f else screenshots.count { it.aiProcessed }.toFloat() / screenshots.size.toFloat()
    var showRetryInfo by remember { mutableStateOf(false) }

    if (onNavigateBack != null) BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Processing Library", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Text("SCREENSHOT INTELLIGENCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(if (state.isIndexing) "Analyzing your library…" else "Your library is ready for analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (state.total > 0) "$completed completed · $failed failed · $remaining remaining" else "${screenshots.count { it.aiProcessed }} analyzed · $remaining remaining", style = MaterialTheme.typography.bodyMedium)
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
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Text(state.currentModel.ifBlank { "AI" }, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(state.currentItemTitle.ifBlank { "Preparing…" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Item ${state.current.coerceAtLeast(0)} of ${state.total}", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.cancelIndexing() }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Text("Cancel")
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
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Process pending")
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.cancelIndexing() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Text("Stop")
                        }
                    }
                    OutlinedButton(onClick = { showRetryInfo = true }, enabled = failed > 0 && !state.isIndexing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Retry failed")
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Queue summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Completed: $completed")
                        Text("Failed: $failed")
                        Text("Remaining: $remaining")
                        if (!state.isIndexing && state.total > 0 && state.progress >= 1f) {
                            Text("Last run finished. Successful results are already stored in the library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(1.dp).weight(0f))
                                Text("Processing complete", fontWeight = FontWeight.SemiBold)
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
            title = { Text("Retry failed") },
            text = { Text("Retrying failed items uses the screenshots that remain unprocessed.") },
            confirmButton = {
                Button(onClick = {
                    showRetryInfo = false
                    val pendingItems = screenshots.filter { !it.aiProcessed }
                    viewModel.batchAnalyzeScreenshots(pendingItems)
                }) { Text("Retry pending") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRetryInfo = false }) { Text("Close") }
            }
        )
    }
}
