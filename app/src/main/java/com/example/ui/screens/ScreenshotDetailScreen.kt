package com.amresalehin.emreshots.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.ui.components.ExifEditorDialog
import com.amresalehin.emreshots.ui.components.InAppVideoPlayer
import com.amresalehin.emreshots.ui.components.OcrAiSheet
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotDetailScreen(
    screenshotId: String,
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToScreenshot: (String) -> Unit
) {
    val context = LocalContext.current
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val exifDataMap by viewModel.exifDataState.collectAsStateWithLifecycle()
    val hasMediaLocationPermission by viewModel.hasMediaLocationPermission.collectAsStateWithLifecycle()
    val pendingIntentSender by viewModel.pendingWriteIntentSender.collectAsStateWithLifecycle()

    val screenshot = allScreenshots.find { it.id == screenshotId }
    val currentIndex = allScreenshots.indexOfFirst { it.id == screenshotId }
    val previousScreenshotId = allScreenshots.getOrNull(currentIndex - 1)?.id
    val nextScreenshotId = allScreenshots.getOrNull(currentIndex + 1)?.id
    val exifData = exifDataMap[screenshotId] ?: ExifData()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.any { it }
        viewModel.onPermissionsResult(isGranted)
        if (screenshot != null) {
            viewModel.loadExif(screenshot)
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWriteConsentGranted()
        } else {
            viewModel.onWriteConsentDenied()
        }
    }

    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            val request = IntentSenderRequest.Builder(sender).build()
            writePermissionLauncher.launch(request)
        }
    }

    BackHandler(onBack = onNavigateBack)

    var showExifEditor by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val isExtractingOcr by viewModel.isExtractingOcr.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showGalleryControls by remember { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    var isEditingDetails by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }

    LaunchedEffect(screenshot) {
        if (screenshot != null) {
            viewModel.loadExif(screenshot)
            editTitle = screenshot.title
            editDescription = screenshot.description
            editNotes = screenshot.notes ?: ""
        }
    }

    if (screenshot == null) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Media item not found")
            }
        }
        return
    }

    val imageFile = File(screenshot.filePath)

    val configuration = LocalConfiguration.current
    val galleryHeight = (configuration.screenHeightDp.dp * 0.80f).coerceAtLeast(520.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(detailScrollState)
            .navigationBarsPadding()
            .pointerInput(screenshotId) {
                var totalDrag = 0f
                var triggered = false
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                        if (!triggered && kotlin.math.abs(totalDrag) >= 120f) {
                            triggered = true
                            if (totalDrag < 0) {
                                nextScreenshotId?.let(onNavigateToScreenshot)
                            } else {
                                previousScreenshotId?.let(onNavigateToScreenshot)
                            }
                        }
                    },
                    onDragEnd = {
                        totalDrag = 0f
                        triggered = false
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        triggered = false
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(galleryHeight)
                .background(Color.Black)
        ) {
            if (screenshot.isVideo) {
                InAppVideoPlayer(
                    screenshot = screenshot,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { showGalleryControls = !showGalleryControls }
                )
            } else if (imageFile.exists() || !screenshot.uriString.isNullOrBlank()) {
                GestureImage(
                    model = ImageRequest.Builder(context)
                        .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = screenshot.title,
                    onTap = { showGalleryControls = !showGalleryControls }
                )
            }

            AnimatedVisibility(
                visible = showGalleryControls,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                                .testTag("btn_detail_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.toggleFavorite(screenshot) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                                    .testTag("btn_detail_fav")
                            ) {
                                Icon(
                                    imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (screenshot.isFavorite) Color(0xFFFF5C7A) else Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, screenshot.title)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${screenshot.title}\\n\\n${screenshot.description}\\nTags: ${screenshot.tags.joinToString(", ")}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share details"))
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                                    .testTag("btn_detail_delete")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Text(
                            text = screenshot.title.ifBlank { "Untitled" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        Text(
                            text = "${currentIndex + 1} / ${allScreenshots.size}  •  Swipe up for details",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

            // Permission Banner if Location or Media Permissions are not yet fully granted
            if (!hasMediaLocationPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
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
                                contentDescription = null,
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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_grant_detail_permission")
                        ) {
                            Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Main Details Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.analyzeLocalVision(screenshot, autoWriteExif = false) },
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
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
                        Text(if (isAnalyzing) "Analyzing" else "Analyze", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showOcrSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                        modifier = Modifier.weight(1f).testTag("btn_open_ocr_sheet")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OCR", fontSize = 12.sp)
                    }

                    if (!screenshot.isVideo) {
                        OutlinedButton(
                            onClick = {
                                if (!hasMediaLocationPermission) {
                                    permissionLauncher.launch(
                                        com.amresalehin.emreshots.service.media.DeviceMediaScanner.getRequiredPermissions()
                                    )
                                }
                                showExifEditor = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            modifier = Modifier.weight(1f).testTag("btn_open_exif_editor")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXIF", fontSize = 12.sp)
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
                                onValueChange = { editTitle = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                label = { Text("Description") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Personal Notes") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = if (screenshot.isVideo) "VIDEO" else "SCREENSHOT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = screenshot.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp
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
                                isEditingDetails = false
                            } else {
                                isEditingDetails = true
                            }
                        },
                        modifier = Modifier.testTag("btn_toggle_edit_details")
                    ) {
                        Icon(
                            imageVector = if (isEditingDetails) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Reminder Info if set
                if (screenshot.reminderTime != null) {
                    val dateFormatted = SimpleDateFormat("EEE, MMM d @ h:mm a", Locale.getDefault()).format(Date(screenshot.reminderTime))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reminder: $dateFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = { viewModel.removeReminder(screenshot) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Tags Chip Cloud
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TAGS",
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(text = "#$tag", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove Tag",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.removeTag(screenshot, tag) }
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { showAddTagDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Add Tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                            text = "OCR TEXT TRANSCRIPTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        TextButton(
                            onClick = { showOcrSheet = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (!screenshot.ocrText.isNullOrBlank()) "Inspect & Send to AI" else "Extract Text",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showOcrSheet = true }
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
                                    text = "Tap to inspect, send to AI, or save to EXIF metadata",
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
                                shape = RoundedCornerShape(10.dp),
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
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
                                                viewModel.showMessage("Copied to clipboard!")
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
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
                                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", modifier = Modifier.size(14.dp))
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
                        text = if (screenshot.isVideo) "VIDEO SPECIFICATIONS" else "METADATA & HARDWARE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
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
        }

    // EXIF Editor Modal
    if (showExifEditor) {
        ExifEditorDialog(
            initialData = exifData,
            screenshot = screenshot,
            onDismiss = { showExifEditor = false },
            onSave = { updated ->
                viewModel.saveExif(screenshot, updated) { success ->
                    if (success) showExifEditor = false
                }
            },
            onApplyAiQuick = {
                viewModel.applyAiExif(screenshot)
                showExifEditor = false
            }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagInput.isNotBlank()) {
                            viewModel.addTag(screenshot, newTagInput)
                            newTagInput = ""
                            showAddTagDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Media?") },
            text = { Text("This will permanently remove the item and its metadata.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScreenshot(screenshot)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOcrSheet) {
        OcrAiSheet(
            sheetState = ocrSheetState,
            screenshot = screenshot,
            isExtractingOcr = isExtractingOcr,
            isAiProcessing = isAnalyzing,
            onExtractOcr = { viewModel.extractOcr(screenshot) },
            onSendOcrToAi = { text, writeMetadata ->
                viewModel.sendOcrToAi(screenshot, text, writeMetadata)
            },
            onWriteToMetadata = { text, title, desc, tags ->
                viewModel.writeOcrAndAiToMetadata(screenshot, text, title, desc, tags)
            },
            onDismiss = {
                coroutineScope.launch {
                    ocrSheetState.hide()
                    showOcrSheet = false
                }
            }
        )
    }


}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun GestureImage(
    model: ImageRequest,
    contentDescription: String?,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(model.data) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
