package com.amresalehin.emreshots.ui.screens

import com.amresalehin.emreshots.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.ui.components.ExifEditorDialog
import com.amresalehin.emreshots.ui.components.InAppVideoPlayer
import com.amresalehin.emreshots.ui.components.OcrAiSheet
import com.amresalehin.emreshots.ui.components.ZoomableImageViewer
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenshotDetailScreen(
    screenshotId: String,
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToScreenshot: (String) -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
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
    var showOverflowMenu by remember { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var showCollectionsDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    val collections by viewModel.collections.collectAsStateWithLifecycle()

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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.media_item_not_found), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_detail_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.media_item_not_found))
            }
        }
        return
    }

    if (showCollectionsDialog) {
        AlertDialog(
            onDismissRequest = { showCollectionsDialog = false },
            title = { Text(stringResource(R.string.collections)) },
            text = {
                if (collections.isEmpty()) {
                    Text(stringResource(R.string.no_collections_yet))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        collections.forEach { collection ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (screenshot.collectionIds.contains(collection.id)) {
                                            viewModel.removeScreenshotFromCollection(screenshot.id, collection.id)
                                        } else {
                                            viewModel.addScreenshotToCollection(screenshot.id, collection.id)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = screenshot.collectionIds.contains(collection.id),
                                    onCheckedChange = { checked ->
                                        if (checked) viewModel.addScreenshotToCollection(screenshot.id, collection.id)
                                        else viewModel.removeScreenshotFromCollection(screenshot.id, collection.id)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(collection.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionsDialog = false }) { Text(stringResource(R.string.done)) }
            }
        )
    }

    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(stringResource(R.string.reminder)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (screenshot.reminderTime != null) {
                            stringResource(
                                R.string.reminder_scheduled_for,
                                SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(screenshot.reminderTime))
                            )
                        } else {
                            stringResource(R.string.reminder_choose_time)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            viewModel.setReminder(screenshot, calendar.timeInMillis, "Review: " + screenshot.title)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            showReminderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.tomorrow_morning)) }
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 18)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            viewModel.setReminder(screenshot, calendar.timeInMillis, "Review: " + screenshot.title)
                            showReminderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.tomorrow_evening)) }
                    OutlinedButton(
                        onClick = {
                            val initial = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            val picked = Calendar.getInstance().apply {
                                                set(year, month, day, hour, minute, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            viewModel.setReminder(screenshot, picked.timeInMillis, "Review: " + screenshot.title)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            showReminderDialog = false
                                        },
                                        initial.get(Calendar.HOUR_OF_DAY),
                                        initial.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                initial.get(Calendar.YEAR),
                                initial.get(Calendar.MONTH),
                                initial.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.choose_date_time)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val imageFile = File(screenshot.filePath)

    val configuration = LocalConfiguration.current
    val galleryHeight = (configuration.screenHeightDp.dp * 0.58f).coerceIn(300.dp, 560.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (screenshot.isVideo) stringResource(R.string.video) else stringResource(R.string.photo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(screenshot) },
                        modifier = Modifier.testTag("btn_detail_favorite")
                    ) {
                        Icon(
                            imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (screenshot.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                            tint = if (screenshot.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("btn_detail_overflow")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isEditingDetails) stringResource(R.string.finish_editing) else stringResource(R.string.edit_details)) },
                                leadingIcon = {
                                    Icon(
                                        if (isEditingDetails) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
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
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val uri = screenshot.uriString?.let(Uri::parse)
                                    if (uri == null) {
                                        viewModel.showMessage(context.getString(R.string.sharing_unavailable_for_item))
                                    } else {
                                        runCatching {
                                            val type = context.contentResolver.getType(uri)
                                                ?: if (screenshot.isVideo) "video/*" else "image/*"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                this.type = type
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_media)))
                                        }.onFailure {
                                            viewModel.showMessage(context.getString(R.string.unable_to_share_item))
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("btn_detail_share")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
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
                                },
                                modifier = Modifier.testTag("btn_detail_delete")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ocr)) },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showOcrSheet = true
                                }
                            )
                            if (!screenshot.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_exif)) },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (!hasMediaLocationPermission) {
                                            permissionLauncher.launch(
                                                com.amresalehin.emreshots.service.media.DeviceMediaScanner.getRequiredPermissions()
                                            )
                                        }
                                        showExifEditor = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.collections)) },
                                leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showCollectionsDialog = true
                                },
                                modifier = Modifier.testTag("menu_detail_collections")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (screenshot.reminderTime != null) {
                                            stringResource(R.string.edit_reminder)
                                        } else {
                                            stringResource(R.string.set_reminder)
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showReminderDialog = true
                                },
                                modifier = Modifier.testTag("menu_detail_reminder")
                            )
                            if (screenshot.reminderTime != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove_reminder)) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.removeReminder(screenshot)
                                    },
                                    modifier = Modifier.testTag("menu_detail_remove_reminder")
                                )
                            }
                        }

                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(detailScrollState)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(galleryHeight)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        MaterialTheme.shapes.extraLarge
                    )
                    .clip(MaterialTheme.shapes.extraLarge)
                    .pointerInput(screenshotId) {
                        var totalDrag = 0f
                        var triggered = false
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                if (!triggered && kotlin.math.abs(totalDrag) >= 120f) {
                                    triggered = true
                                    if (totalDrag < 0) nextScreenshotId?.let(onNavigateToScreenshot)
                                    else previousScreenshotId?.let(onNavigateToScreenshot)
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
                    },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
            if (screenshot.isVideo) {
                InAppVideoPlayer(
                    screenshot = screenshot,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { }
                )
            } else if (imageFile.exists() || !screenshot.uriString.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = screenshot.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { showImageViewer = true })
                        }
                )
            }


        }

        Spacer(modifier = Modifier.height(1.dp))

            ScreenshotDetailContent(
                viewModel = viewModel,
                screenshot = screenshot,
                screenshotId = screenshotId,
                context = context,
                imageFile = imageFile,
                galleryHeight = galleryHeight,
                detailScrollState = detailScrollState,
                previousScreenshotId = previousScreenshotId,
                nextScreenshotId = nextScreenshotId,
                onNavigateToScreenshot = onNavigateToScreenshot,
                hasMediaLocationPermission = hasMediaLocationPermission,
                permissionLauncher = permissionLauncher,
                exifData = exifData,
                isAnalyzing = isAnalyzing,
                showOcrSheet = showOcrSheet,
                onShowOcrSheetChange = { showOcrSheet = it },
                showExifEditor = showExifEditor,
                onShowExifEditorChange = { showExifEditor = it },
                isEditingDetails = isEditingDetails,
                onEditingDetailsChange = { isEditingDetails = it },
                editTitle = editTitle,
                onEditTitleChange = { editTitle = it },
                editDescription = editDescription,
                onEditDescriptionChange = { editDescription = it },
                editNotes = editNotes,
                onEditNotesChange = { editNotes = it },
                showAddTagDialog = showAddTagDialog,
                onShowAddTagDialogChange = { showAddTagDialog = it }
            )

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
            title = { Text(stringResource(R.string.add_tag)) },
            text = {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    label = { Text(stringResource(R.string.tag_name)) },
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
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_media_question)) },
            text = { Text(stringResource(R.string.delete_media_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScreenshot(screenshot)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
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
            onExtractOcr = { onText -> viewModel.extractOcr(screenshot) { fixed -> if (!fixed.isNullOrBlank()) onText(fixed) } },
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
    if (showImageViewer) {
        ZoomableImageViewer(
            screenshot = screenshot,
            exifData = exifData,
            onDismiss = { showImageViewer = false },
            onToggleFavorite = { viewModel.toggleFavorite(it) }
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


