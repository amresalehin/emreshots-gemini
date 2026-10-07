package com.amresalehin.emreshots.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.service.ocr.TessLanguage
import com.amresalehin.emreshots.ui.components.OcrLanguageManagerSheet
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun OcrSettingsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val enabled by viewModel.ocrEnabled.collectAsStateWithLifecycle()
    val language by viewModel.ocrLanguage.collectAsStateWithLifecycle()
    val installed by viewModel.installedOcrLanguages.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.ocrLanguageDownloadProgress.collectAsStateWithLifecycle()
    val linksEnabled by viewModel.linksDetectionEnabled.collectAsStateWithLifecycle()
    var showLanguageSheet by remember { mutableStateOf(false) }

    val customPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: uri.lastPathSegment ?: "custom.traineddata"
            viewModel.importCustomOcrLanguage(uri, fileName)
        }
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR & Text", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OCR Text Extraction", fontWeight = FontWeight.SemiBold)
                        Text("Extract text locally with Tesseract.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = viewModel::setOcrEnabled)
                }
            }
            if (enabled) {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val active = TessLanguage.findByCode(language)
                                Text("Language: " + active.displayName, fontWeight = FontWeight.SemiBold)
                                Text(installed.size.toString() + " language packs installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { showLanguageSheet = true }, modifier = Modifier.height(48.dp)) {
                                Text("Manage")
                            }
                        }
                        FilledTonalButton(
                            onClick = { customPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Import custom language pack")
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Link & URL Detection", fontWeight = FontWeight.SemiBold)
                        Text("Extract web links from recognized text.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = linksEnabled, onCheckedChange = viewModel::setLinksDetectionEnabled)
                }
            }
        }
    }

    if (showLanguageSheet) {
        OcrLanguageManagerSheet(
            activeLanguageCode = language,
            installedLanguageCodes = installed,
            downloadProgressMap = downloadProgress,
            onSelectLanguage = {
                viewModel.setOcrLanguage(it)
                showLanguageSheet = false
            },
            onDownloadLanguage = viewModel::downloadOcrLanguage,
            onDeleteLanguage = viewModel::deleteOcrLanguage,
            onImportCustom = { customPicker.launch(arrayOf("*/*")) },
            onDismiss = { showLanguageSheet = false }
        )
    }
}
