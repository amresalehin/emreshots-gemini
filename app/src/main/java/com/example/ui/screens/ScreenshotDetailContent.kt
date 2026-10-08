@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amresalehin.emreshots.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
import com.amresalehin.emreshots.ui.components.InAppVideoPlayer
import com.amresalehin.emreshots.ui.components.ScreenshotCard
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun ScreenshotDetailContent(
    viewModel: ScreenshotsViewModel,
    screenshot: ScreenshotItem,
    screenshotId: String,
    context: android.content.Context,
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
// Permission Banner if Location or Media Permissions are not yet fully granted
if (!hasMediaLocationPermission) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("banner_detail_permission")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.metadata_camera),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Media Location permission to access unredacted GPS & EXIF camera metadata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = {
                    permissionLauncher.launch(com.amresalehin.emreshots.service.media.DeviceMediaScanner.getRequiredPermissions())
                },
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_grant_detail_permission")
            ) {
                Text(stringResource(R.string.grant), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Main Details Content
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .navigationBarsPadding()
        .border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            RoundedCornerShape(28.dp)
        ),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.analyzeScreenshot(screenshot, autoWriteExif = false) },
            enabled = !isAnalyzing,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
            modifier = Modifier.weight(1f).testTag("btn_analyze_now")
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isAnalyzing) stringResource(R.string.analyzing) else if (screenshot.aiProcessed) stringResource(R.string.reanalyze) else stringResource(R.string.analyze), fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = { onShowOcrSheetChange(true) },
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
            modifier = Modifier.weight(1f).testTag("btn_open_ocr_sheet")
        ) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.ocr), fontSize = 12.sp)
        }

        if (!screenshot.isVideo) {
            OutlinedButton(
                onClick = {
                    if (!hasMediaLocationPermission) {
                        permissionLauncher.launch(
                            com.amresalehin.emreshots.service.media.DeviceMediaScanner.getRequiredPermissions()
                        )
                    }
                    onShowExifEditorChange(true)
                },
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
                modifier = Modifier.weight(1f).testTag("btn_open_exif_editor")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.exif), fontSize = 12.sp)
            }
        }
    }

    // Title and Description
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (isEditingDetails) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { onEditTitleChange(it) },
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { onEditDescriptionChange(it) },
                    label = { Text(stringResource(R.string.description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { onEditNotesChange(it) },
                    label = { Text(stringResource(R.string.personal_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = if (screenshot.isVideo) stringResource(R.string.video).uppercase(Locale.ROOT) else stringResource(R.string.photo).uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = screenshot.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                )
                if (screenshot.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = screenshot.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
                if (!screenshot.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notes: ${screenshot.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isEditingDetails) {
            IconButton(
                onClick = {
                    viewModel.updateScreenshot(
                        screenshot.copy(
                            title = editTitle.ifBlank { screenshot.title },
                            description = editDescription,
                            notes = editNotes.ifBlank { null }
                        )
                    )
                    onEditingDetailsChange(false)
                },
                modifier = Modifier.testTag("btn_save_edit_details")
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save_changes))
            }
            IconButton(
                onClick = {
                    onEditTitleChange(screenshot.title)
                    onEditDescriptionChange(screenshot.description)
                    onEditNotesChange(screenshot.notes.orEmpty())
                    onEditingDetailsChange(false)
                },
                modifier = Modifier.testTag("btn_cancel_edit_details")
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_editing))
            }
        }
    }

    // Tags Chip Cloud
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            screenshot.tags.forEach { tag ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(text = "#$tag", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.removeTag(screenshot, tag) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_remove_tag_" + tag.hashCode())
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_tag),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.clickable { onShowAddTagDialogChange(true) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.add_tag), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // OCR Text Transcription Section
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OCR text",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                }
                TextButton(
                    onClick = { onShowOcrSheetChange(true) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (!screenshot.ocrText.isNullOrBlank()) "View OCR" else "Extract Text",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowOcrSheetChange(true) }
                .testTag("card_ocr_preview")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!screenshot.ocrText.isNullOrBlank()) {
                    Text(
                        text = screenshot.ocrText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap to inspect the OCR result or save it to EXIF metadata",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No OCR text extracted yet. Tap to transcribe text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Detected Links
    if (screenshot.links.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "DETECTED DATA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            screenshot.links.forEach { link ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = link, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.link), link))
                                    viewModel.showMessage("Copied to clipboard!")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(14.dp))
                            }
                            if (link.startsWith("http://") || link.startsWith("https://")) {
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.open), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // Clean Specs & Technical Details
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (screenshot.isVideo) "Video specifications" else "Metadata & camera",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (screenshot.isVideo) {
                    val durSecs = (screenshot.durationMs / 1000) % 60
                    val durMins = (screenshot.durationMs / 1000) / 60
                    DetailRow("Duration", String.format("%d:%02d", durMins, durSecs))
                    if (screenshot.width > 0) DetailRow("Resolution", "${screenshot.width} x ${screenshot.height}")
                    val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                    DetailRow("File Size", String.format(Locale.US, "%.2f MB", mb))
                } else {
                    DetailRow("Camera", exifData.cameraModel ?: (exifData.cameraMake ?: "Android Device"))
                    DetailRow("Date", exifData.dateTaken ?: SimpleDateFormat("yyyy:MM:dd HH:mm", Locale.US).format(Date(screenshot.addedOn)))
                    if (exifData.latitude != null && exifData.longitude != null) {
                        DetailRow("GPS Location", String.format(Locale.US, "%.5f, %.5f", exifData.latitude, exifData.longitude))
                    }
                    if (exifData.iso != null) DetailRow("Settings", "ISO ${exifData.iso} • ƒ/${exifData.fNumber ?: "N/A"} • ${exifData.exposureTime ?: "N/A"}s")
                    if (exifData.software != null) DetailRow("Software", exifData.software)
                    if (!exifData.artist.isNullOrBlank()) DetailRow("Artist", exifData.artist)
                    if (!exifData.imageDescription.isNullOrBlank() && exifData.imageDescription != screenshot.title) {
                        DetailRow("EXIF Title", exifData.imageDescription)
                    }
                    if (!exifData.userComment.isNullOrBlank()) DetailRow("EXIF Notes", exifData.userComment)
                }
                if (screenshot.aiModelUsed != null) {
                    DetailRow("Indexed With", screenshot.aiModelUsed)
                }
            }
        }
    }
}
