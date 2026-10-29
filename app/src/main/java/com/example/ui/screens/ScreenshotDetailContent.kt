@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.amresalehin.emreshots.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun ScreenshotDetailContent(
    viewModel: ScreenshotsViewModel,
    screenshot: ScreenshotItem,
    screenshotId: String,
    context: Context,
    imageFile: java.io.File,
    galleryHeight: Dp,
    detailScrollState: androidx.compose.foundation.ScrollState,
    previousScreenshotId: String?,
    nextScreenshotId: String?,
    onNavigateToScreenshot: (String) -> Unit,
    hasMediaLocationPermission: Boolean,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    exifData: ExifData,
    isAnalyzing: Boolean,
    showOcrSheet: Boolean,
    onShowOcrSheetChange: (Boolean) -> Unit,
    showExifEditor: Boolean,
    onShowExifEditorChange: (Boolean) -> Unit,
    isEditingDetails: Boolean,
    onEditingDetailsChange: (Boolean) -> Unit,
    editTitle: String,
    onEditTitleChange: (String) -> Unit,
    editDescription: String,
    onEditDescriptionChange: (String) -> Unit,
    editNotes: String,
    onEditNotesChange: (String) -> Unit,
    showAddTagDialog: Boolean,
    onShowAddTagDialogChange: (Boolean) -> Unit
) {
    if (!hasMediaLocationPermission) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("banner_detail_permission")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Location metadata permission is required to read precise GPS/EXIF details.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions()) }) {
                    Text(stringResource(R.string.grant))
                }
            }
        }
    }

    var selectedDetailsTab by remember(screenshotId) { mutableStateOf("Overview") }
    var showMetadata by remember(screenshotId) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).navigationBarsPadding().testTag("viewer_details_drawer"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(1.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 9.dp).size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        screenshot.title.ifBlank { if (screenshot.isVideo) "Untitled video" else "Untitled screenshot" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append(SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(screenshot.addedOn)))
                            append(" · ")
                            append(String.format(Locale.US, "%.1f MB", screenshot.fileSize / (1024f * 1024f)))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        if (isEditingDetails) {
                            viewModel.updateScreenshot(
                                screenshot.copy(
                                    title = editTitle.ifBlank { screenshot.title },
                                    description = editDescription,
                                    notes = editNotes.ifBlank { null }
                                )
                            )
                            onEditingDetailsChange(false)
                        } else {
                            onEditingDetailsChange(true)
                        }
                    },
                    modifier = Modifier.testTag("btn_toggle_edit_details")
                ) {
                    Icon(if (isEditingDetails) Icons.Default.Check else Icons.Default.Edit, contentDescription = if (isEditingDetails) "Save details" else "Edit details")
                }
            }

            if (isEditingDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editTitle, onEditTitleChange, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editDescription, onEditDescriptionChange, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editNotes, onEditNotesChange, label = { Text("Personal notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            } else {
                if (screenshot.description.isNotBlank()) Text(screenshot.description, style = MaterialTheme.typography.bodyMedium)
                if (!screenshot.notes.isNullOrBlank()) Text("Notes · ${screenshot.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf("Overview", "Actions").forEach { tab ->
                    Column(
                        modifier = Modifier.weight(1f).clickable { selectedDetailsTab = tab }.padding(vertical = 8.dp).testTag("drawer_tab_${tab.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(tab, fontWeight = if (selectedDetailsTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedDetailsTab == tab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = if (selectedDetailsTab == tab) MaterialTheme.colorScheme.onSurface else Color.Transparent, thickness = 2.dp)
                    }
                }
            }

            if (selectedDetailsTab == "Overview") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.collections), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (screenshot.collectionIds.isEmpty()) {
                        Text("Not in any collection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            screenshot.collectionIds.forEach { collectionId ->
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                                    Text(collectionId, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onShowAddTagDialogChange(true) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                    if (screenshot.tags.isEmpty()) {
                        Text("No tags", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            screenshot.tags.forEach { tag ->
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tag, style = MaterialTheme.typography.bodySmall)
                                        IconButton(onClick = { viewModel.removeTag(screenshot, tag) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove $tag", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (exifData.latitude != null && exifData.longitude != null) {
                        DetailRow("Coordinates", String.format(Locale.US, "%.5f, %.5f", exifData.latitude, exifData.longitude))
                    } else {
                        Text("No location data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showMetadata = !showMetadata }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Icon(if (showMetadata) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (showMetadata) "Collapse metadata" else "Expand metadata", modifier = Modifier.size(18.dp))
                    }
                    if (showMetadata) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRow("Media type", if (screenshot.isVideo) "Video" else "Image")
                            DetailRow("File size", String.format(Locale.US, "%.2f MB", screenshot.fileSize / (1024.0 * 1024.0)))
                            if (screenshot.width > 0 && screenshot.height > 0) DetailRow("Resolution", "${screenshot.width} × ${screenshot.height}")
                            DetailRow("Date added", SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(screenshot.addedOn)))
                            DetailRow("Camera", exifData.cameraModel ?: exifData.cameraMake ?: "Unknown")
                            if (exifData.dateTaken != null) DetailRow("Date taken", exifData.dateTaken)
                            if (exifData.iso != null) DetailRow("ISO", exifData.iso.toString())
                            if (exifData.fNumber != null) DetailRow("Aperture", "ƒ/${exifData.fNumber}")
                            if (exifData.software != null) DetailRow("Software", exifData.software)
                            if (screenshot.aiModelUsed != null) DetailRow("Analysis model", screenshot.aiModelUsed)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Image tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.analyzeScreenshot(screenshot, autoWriteExif = false) },
                            enabled = !isAnalyzing,
                            modifier = Modifier.weight(1f).testTag("btn_analyze_now"),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (isAnalyzing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (screenshot.aiProcessed) "Re-analyze" else "AI description")
                        }
                        OutlinedButton(onClick = { onShowOcrSheetChange(true) }, modifier = Modifier.weight(1f).testTag("btn_open_ocr_sheet"), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Extract text")
                        }
                    }
                    if (!screenshot.isVideo) {
                        OutlinedButton(
                            onClick = {
                                if (!hasMediaLocationPermission) permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions())
                                onShowExifEditorChange(true)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_open_exif_editor"),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.edit_exif))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OCR text", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (!screenshot.ocrText.isNullOrBlank()) {
                            Text(screenshot.ocrText, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                            TextButton(onClick = { onShowOcrSheetChange(true) }) { Text("View or edit extracted text") }
                        } else {
                            Text("No text extracted yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onShowOcrSheetChange(true) }) { Text("Extract text") }
                        }
                    }
                    if (screenshot.links.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Detected links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            screenshot.links.forEach { link ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(link, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
                                        viewModel.showMessage("Copied to clipboard!")
                                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy link") }
                                    if (link.startsWith("http://") || link.startsWith("https://")) {
                                        IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } }) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open link")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.deleteScreenshot(screenshot) },
                        modifier = Modifier.fillMaxWidth().testTag("btn_delete_media"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
