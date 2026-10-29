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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val galleryHeight = (configuration.screenHeightDp.dp * 0.58f).coerceIn(300.dp, 560.dp)

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val previousStatusBarColor = window?.statusBarColor
        val previousNavigationBarColor = window?.navigationBarColor
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        window?.statusBarColor = android.graphics.Color.WHITE
        window?.navigationBarColor = android.graphics.Color.WHITE

        onDispose {
            previousStatusBarColor?.let { window?.statusBarColor = it }
            previousNavigationBarColor?.let { window?.navigationBarColor = it }
            previousLightStatusBars?.let { controller?.isAppearanceLightStatusBars = it }
            previousLightNavigationBars?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(detailScrollState)
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
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(galleryHeight)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        RoundedCornerShape(22.dp)
                    )
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFFF1F2F4),
                tonalElevation = 0.dp
            ) {
            if (screenshot.isVideo) {
                InAppVideoPlayer(
                    screenshot = screenshot,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { }
                )
            } else if (imageFile.exists() || !screenshot.uriString.isNullOrBlank()) {
                ZoomableDetailImage(
                    model = ImageRequest.Builder(context)
                        .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = screenshot.title,
                    onTap = { }
                )
            }


        }

        Spacer(modifier = Modifier.height(1.dp))

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

            // Emre-style details drawer. The media remains visible behind the
            // sheet; content is grouped by spacing rather than nested cards.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .navigationBarsPadding()
                    .testTag("viewer_details_drawer"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = screenshot.title.ifBlank { if (screenshot.isVideo) "Untitled video" else "Untitled image" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append(SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(exifData.dateTaken?.let {
                                        runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }.getOrNull()
                                    } ?: screenshot.addedOn)))
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
                                    isEditingDetails = false
                                } else {
                                    isEditingDetails = true
                                }
                            },
                            modifier = Modifier.testTag("btn_toggle_edit_details")
                        ) {
                            Icon(
                                imageVector = if (isEditingDetails) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditingDetails) "Save details" else "Edit details",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (isEditingDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                label = { Text("Description") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Personal notes") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (screenshot.description.isNotBlank() || !screenshot.notes.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (screenshot.description.isNotBlank()) {
                                Text(screenshot.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!screenshot.notes.isNullOrBlank()) {
                                Text("Notes · ${screenshot.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    var selectedDetailsTab by remember(screenshotId) { mutableStateOf("Overview") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        listOf("Overview", "Actions").forEach { tab ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDetailsTab = tab }
                                    .padding(vertical = 8.dp)
                                    .testTag("drawer_tab_${tab.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    tab,
                                    fontWeight = if (selectedDetailsTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedDetailsTab == tab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedDetailsTab == tab) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                                )
                            }
                        }
                    }

                    if (selectedDetailsTab == "Overview") {
                        // Albums are represented by the collection names stored on the item.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Albums", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (screenshot.collectionIds.isEmpty()) {
                                Text("Not in any album", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    screenshot.collectionIds.forEach { id ->
                                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                                            Text(id, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { showAddTagDialog = true }, contentPadding = PaddingValues(0.dp)) {
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
                                            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(tag, style = MaterialTheme.typography.bodySmall)
                                                IconButton(onClick = { viewModel.removeTag(screenshot, tag) }, modifier = Modifier.size(24.dp)) {
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

                        var showMetadata by remember(screenshotId) { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showMetadata = !showMetadata }.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Icon(if (showMetadata) Icons.Default.Close else Icons.Default.Add, contentDescription = if (showMetadata) "Collapse metadata" else "Expand metadata", modifier = Modifier.size(18.dp))
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
                                OutlinedButton(
                                    onClick = { showOcrSheet = true },
                                    modifier = Modifier.weight(1f).testTag("btn_open_ocr_sheet"),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Extract text")
                                }
                            }
                            if (!screenshot.isVideo) {
                                OutlinedButton(
                                    onClick = {
                                        if (!hasMediaLocationPermission) {
                                            permissionLauncher.launch(com.amresalehin.emreshots.service.media.DeviceMediaScanner.getRequiredPermissions())
                                        }
                                        showExifEditor = true
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("btn_open_exif_editor"),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Edit metadata")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("OCR text", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (!screenshot.ocrText.isNullOrBlank()) {
                                    Text(screenshot.ocrText, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                                    TextButton(onClick = { showOcrSheet = true }) { Text("View or edit extracted text") }
                                } else {
                                    Text("No text extracted yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { showOcrSheet = true }) { Text("Extract text") }
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
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                                            }
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
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.fillMaxWidth().testTag("btn_delete_media"),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete media")
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
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
private fun ZoomableDetailImage(
    model: ImageRequest,
    contentDescription: String?,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFF1F2F4))
            .pointerInput(model.data) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.1f) {
                            reset()
                        } else {
                            scale = 2.5f
                            offsetX = (size.width / 2f - tap.x) * 1.5f
                            offsetY = (size.height / 2f - tap.y) * 1.5f
                        }
                    }
                )
            }
            .pointerInput(model.data) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxX = size.width * (newScale - 1f) / 2f
                        val maxY = size.height * (newScale - 1f) / 2f
                        offsetX = (offsetX + pan.x * newScale).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y * newScale).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}
