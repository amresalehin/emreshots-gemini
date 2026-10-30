package com.amresalehin.emreshots.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomableImageViewer(
    screenshot: ScreenshotItem,
    exifData: ExifData = ExifData(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ScreenshotItem) -> Unit = {},
    viewModel: ScreenshotsViewModel? = null,
    collections: List<CollectionItem> = emptyList(),
    allScreenshots: List<ScreenshotItem> = emptyList(),
    onNavigateToScreenshot: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageFile = File(screenshot.filePath)

    // Flow states if viewModel is available
    val vmCollections by viewModel?.collections?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(collections) }
    val isAnalyzing by viewModel?.isAnalyzing?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val isExtractingOcr by viewModel?.isExtractingOcr?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val activeCollections = if (vmCollections.isNotEmpty()) vmCollections else collections

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Drawer state
    var drawerPosition by remember { mutableStateOf(DrawerPosition.PEEK) }

    // Dialog & Sheet states
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var showAddToCollectionDialog by remember { mutableStateOf(false) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderNoteInput by remember { mutableStateOf("") }
    var showExifEditor by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // LAYER 1: Full-bleed Media Background (Photos & Videos)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            ) {
                if (screenshot.isVideo) {
                    InAppVideoPlayer(
                        screenshot = screenshot,
                        modifier = Modifier.fillMaxSize(),
                        onTap = {
                            showControls = !showControls
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                        if (drawerPosition == DrawerPosition.EXPANDED) {
                                            drawerPosition = DrawerPosition.MEDIUM
                                        }
                                    },
                                    onDoubleTap = { tapOffset ->
                                        if (scale > 1.2f) {
                                            resetZoom()
                                        } else {
                                            scale = 2.5f
                                            offsetX = (size.width / 2f - tapOffset.x) * 1.5f
                                            offsetY = (size.height / 2f - tapOffset.y) * 1.5f
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(0.9f, 6.0f)
                                    scale = newScale

                                    if (scale > 1f) {
                                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                        offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                                .crossfade(true)
                                .build(),
                            contentDescription = screenshot.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                }
            }

            // LAYER 2: Bottom-Anchored Details Drawer Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                ViewerDetailsDrawer(
                    screenshot = screenshot,
                    exifData = exifData,
                    collections = activeCollections,
                    isAnalyzing = isAnalyzing,
                    isExtractingOcr = isExtractingOcr,
                    currentPosition = drawerPosition,
                    onPositionChanged = { newPos -> drawerPosition = newPos },
                    onAnalyze = { viewModel?.analyzeScreenshot(screenshot, autoWriteExif = false) },
                    onExtractOcr = { showOcrSheet = true },
                    onOpenExifEditor = { showExifEditor = true },
                    onAddTagClick = { showAddTagDialog = true },
                    onRemoveTag = { tag -> viewModel?.removeTag(screenshot, tag) },
                    onAddToCollectionClick = { showAddToCollectionDialog = true },
                    onRemoveFromCollection = { colId ->
                        viewModel?.removeScreenshotFromCollection(screenshot.id, colId)
                    },
                    onSetReminderClick = { showReminderDialog = true },
                    onRemoveReminder = { viewModel?.removeReminder(screenshot) },
                    onSaveDetails = { title, desc, notes ->
                        viewModel?.updateScreenshot(
                            screenshot.copy(
                                title = title,
                                description = desc,
                                notes = notes
                            )
                        )
                    },
                    onDeleteRequest = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 3: Top HUD Toolbar (Always above drawer and fully clickable)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back / Close and Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_close_fullscreen_image")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close fullscreen viewer",
                                    tint = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = screenshot.title.ifBlank { "Media Viewer" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (screenshot.isVideo) {
                                        val durSecs = (screenshot.durationMs / 1000) % 60
                                        val durMins = (screenshot.durationMs / 1000) / 60
                                        String.format("Video • %d:%02d", durMins, durSecs)
                                    } else if (screenshot.width > 0 && screenshot.height > 0) {
                                        "${screenshot.width} × ${screenshot.height} px"
                                    } else {
                                        "High Resolution"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }

                        // Action buttons row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Zoom reset button if zoomed in
                            if (scale > 1.1f) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { resetZoom() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text(
                                            text = "${(scale * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            // Favorite Action
                            IconButton(
                                onClick = { onToggleFavorite(screenshot) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_fullscreen_favorite")
                            ) {
                                Icon(
                                    imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (screenshot.isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (screenshot.isFavorite) Color(0xFFF43F5E) else Color.White
                                )
                            }

                            // Share Action
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (screenshot.isVideo) "video/*" else "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, screenshot.title)
                                        putExtra(Intent.EXTRA_TEXT, "${screenshot.title}\n${screenshot.description}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_fullscreen_share")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share media",
                                    tint = Color.White
                                )
                            }

                            // More / Overflow Action
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_fullscreen_overflow")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More actions",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("View Full Details") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            drawerPosition = DrawerPosition.EXPANDED
                                            showOverflowMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add Tag") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            showAddTagDialog = true
                                            showOverflowMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add to Collection") },
                                        leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                                        onClick = {
                                            showAddToCollectionDialog = true
                                            showOverflowMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Set Reminder") },
                                        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                                        onClick = {
                                            showReminderDialog = true
                                            showOverflowMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete Media",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Delete Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Media?") },
            text = { Text("This will permanently remove the item and all its associated metadata.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.deleteScreenshot(screenshot)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_media")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("btn_cancel_delete_media")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Add Tag
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_new_tag_name")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagInput.isNotBlank()) {
                            viewModel?.addTag(screenshot, newTagInput.trim())
                            newTagInput = ""
                            showAddTagDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_tag")
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

    // DIALOG: Add to Collection
    if (showAddToCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showAddToCollectionDialog = false },
            title = { Text("Add to Collection") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeCollections.isEmpty()) {
                        Text(
                            text = "No collections created yet. Create one below:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Select a collection:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(activeCollections) { col ->
                                val isMember = screenshot.collectionIds.contains(col.id)
                                Surface(
                                    color = if (isMember) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (isMember) {
                                                viewModel?.removeScreenshotFromCollection(screenshot.id, col.id)
                                            } else {
                                                viewModel?.addScreenshotToCollection(screenshot.id, col.id)
                                            }
                                            showAddToCollectionDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = col.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (isMember) "Added ✓" else "+ Add",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showAddToCollectionDialog = false
                            showCreateCollectionDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create New Collection")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToCollectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // DIALOG: Create Collection
    if (showCreateCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCollectionDialog = false },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            viewModel?.createCollection(
                                name = newCollectionName.trim(),
                                description = "",
                                iconName = "folder",
                                colorHex = "#6366F1"
                            )
                            newCollectionName = ""
                            showCreateCollectionDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCollectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Set Reminder
    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Schedule Reminder") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Set a date and time to review this media item:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = reminderNoteInput,
                        onValueChange = { reminderNoteInput = it },
                        label = { Text("Reminder Note (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        cal.set(Calendar.YEAR, year)
                                        cal.set(Calendar.MONTH, month)
                                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                cal.set(Calendar.MINUTE, minute)
                                                viewModel?.setReminder(
                                                    screenshot = screenshot,
                                                    timeMs = cal.timeInMillis,
                                                    text = reminderNoteInput.ifBlank { "Review media" }
                                                )
                                                showReminderDialog = false
                                            },
                                            cal.get(Calendar.HOUR_OF_DAY),
                                            cal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pick Date & Time")
                        }
                    }

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val oneHourLater = System.currentTimeMillis() + (60 * 60 * 1000L)
                                viewModel?.setReminder(screenshot, oneHourLater, reminderNoteInput.ifBlank { "In 1 hour" })
                                showReminderDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("In 1 Hour", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val tomorrow = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 9)
                                    set(Calendar.MINUTE, 0)
                                }.timeInMillis
                                viewModel?.setReminder(screenshot, tomorrow, reminderNoteInput.ifBlank { "Tomorrow morning" })
                                showReminderDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tomorrow 9 AM", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // EXIF Editor Modal
    if (showExifEditor) {
        ExifEditorDialog(
            initialData = exifData,
            screenshot = screenshot,
            onDismiss = { showExifEditor = false },
            onSave = { updated ->
                viewModel?.saveExif(screenshot, updated) { success ->
                    if (success) showExifEditor = false
                } ?: run { showExifEditor = false }
            },
            onApplyAiQuick = {
                viewModel?.applyAiExif(screenshot)
                showExifEditor = false
            }
        )
    }

    // OCR Modal Sheet
    if (showOcrSheet) {
        OcrAiSheet(
            sheetState = ocrSheetState,
            screenshot = screenshot,
            isExtractingOcr = isExtractingOcr,
            isAiProcessing = isAnalyzing,
            onExtractOcr = { onText ->
                viewModel?.extractOcr(screenshot) { fixed ->
                    if (!fixed.isNullOrBlank()) onText(fixed)
                }
            },
            onWriteToMetadata = { text, title, desc, tags ->
                viewModel?.writeOcrAndAiToMetadata(screenshot, text, title, desc, tags)
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
