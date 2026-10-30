package com.amresalehin.emreshots.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DrawerPosition {
    PEEK,
    MEDIUM,
    EXPANDED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewerDetailsDrawer(
    screenshot: ScreenshotItem,
    exifData: ExifData,
    collections: List<CollectionItem>,
    isAnalyzing: Boolean = false,
    isExtractingOcr: Boolean = false,
    currentPosition: DrawerPosition = DrawerPosition.PEEK,
    initialTab: Int = 0,
    onPositionChanged: (DrawerPosition) -> Unit = {},
    onAnalyze: () -> Unit = {},
    onExtractOcr: () -> Unit = {},
    onOpenExifEditor: () -> Unit = {},
    onAddTagClick: () -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onAddToCollectionClick: () -> Unit = {},
    onRemoveFromCollection: (String) -> Unit = {},
    onSetReminderClick: () -> Unit = {},
    onRemoveReminder: () -> Unit = {},
    onSaveDetails: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val contentScrollState = rememberScrollState()

    // Edit state for Title, Description, Notes
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember(screenshot.id, isEditing) { mutableStateOf(screenshot.title) }
    var editDescription by remember(screenshot.id, isEditing) { mutableStateOf(screenshot.description) }
    var editNotes by remember(screenshot.id, isEditing) { mutableStateOf(screenshot.notes ?: "") }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        val peekHeight = 100.dp
        val maxTargetHeight = (availableHeight - 68.dp).coerceAtLeast(peekHeight)
        val mediumHeight = (availableHeight * 0.52f).coerceIn(360.dp, maxTargetHeight)
        val expandedHeight = (availableHeight - 84.dp).coerceIn(mediumHeight, maxTargetHeight)

        val targetHeight = when (currentPosition) {
            DrawerPosition.PEEK -> peekHeight
            DrawerPosition.MEDIUM -> mediumHeight
            DrawerPosition.EXPANDED -> expandedHeight
        }

        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "drawer_height"
        )

        // Bottom-anchored details drawer surface (solid, opaque, calm monochrome)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(animatedHeight)
                .testTag("overlay_details_drawer"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Drag Handle & Header Area (Handles dragging and quick position toggle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(currentPosition) {
                            var verticalDragTotal = 0f
                            detectVerticalDragGestures(
                                onDragStart = { verticalDragTotal = 0f },
                                onDragEnd = {
                                    if (verticalDragTotal < -45f) {
                                        // Dragged UP
                                        when (currentPosition) {
                                            DrawerPosition.PEEK -> onPositionChanged(DrawerPosition.MEDIUM)
                                            DrawerPosition.MEDIUM -> onPositionChanged(DrawerPosition.EXPANDED)
                                            DrawerPosition.EXPANDED -> {}
                                        }
                                    } else if (verticalDragTotal > 45f) {
                                        // Dragged DOWN
                                        when (currentPosition) {
                                            DrawerPosition.EXPANDED -> onPositionChanged(DrawerPosition.MEDIUM)
                                            DrawerPosition.MEDIUM -> onPositionChanged(DrawerPosition.PEEK)
                                            DrawerPosition.PEEK -> {}
                                        }
                                    }
                                    verticalDragTotal = 0f
                                },
                                onDragCancel = { verticalDragTotal = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    verticalDragTotal += dragAmount
                                }
                            )
                        }
                        .clickable {
                            val nextPos = when (currentPosition) {
                                DrawerPosition.PEEK -> DrawerPosition.MEDIUM
                                DrawerPosition.MEDIUM -> DrawerPosition.EXPANDED
                                DrawerPosition.EXPANDED -> DrawerPosition.PEEK
                            }
                            onPositionChanged(nextPos)
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("drawer_drag_handle_area")
                ) {
                    // Tactile horizontal drag bar indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }

                    // Header Row with Title, Subtitle, and Chevron indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (screenshot.isVideo) "VIDEO" else "PHOTO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = screenshot.title.ifBlank { "Media Details" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Subtitle: Date • Dimensions • File Size
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                val dateStr = SimpleDateFormat("EEE, MMM d, yyyy  h:mm a", Locale.getDefault())
                                    .format(Date(screenshot.addedOn))
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (screenshot.width > 0 && screenshot.height > 0) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${screenshot.width} × ${screenshot.height}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (screenshot.fileSize > 0) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val sizeStr = if (screenshot.fileSize < 1024 * 1024) {
                                        "${screenshot.fileSize / 1024} KB"
                                    } else {
                                        String.format(Locale.US, "%.2f MB", screenshot.fileSize.toDouble() / (1024 * 1024))
                                    }
                                    Text(
                                        text = sizeStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Chevron indicator toggle button
                        IconButton(
                            onClick = {
                                val nextPos = when (currentPosition) {
                                    DrawerPosition.PEEK -> DrawerPosition.MEDIUM
                                    DrawerPosition.MEDIUM -> DrawerPosition.EXPANDED
                                    DrawerPosition.EXPANDED -> DrawerPosition.PEEK
                                }
                                onPositionChanged(nextPos)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("btn_toggle_drawer_position")
                        ) {
                            Icon(
                                imageVector = if (currentPosition == DrawerPosition.EXPANDED)
                                    Icons.Default.KeyboardArrowDown
                                else
                                    Icons.Default.KeyboardArrowUp,
                                contentDescription = if (currentPosition == DrawerPosition.EXPANDED)
                                    "Collapse details"
                                else
                                    "Expand details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // If not in Peek position, show Primary Actions, Tabs, and organized content
                if (currentPosition != DrawerPosition.PEEK) {
                    // 1. PRIMARY ACTION BUTTONS: Analyze, OCR, EXIF
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // AI Analyze / Reanalyze Button (soft tinted container)
                        Button(
                            onClick = onAnalyze,
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_drawer_analyze")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAnalyzing) "Analyzing" else if (screenshot.aiProcessed) "Reanalyze" else "Analyze",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // OCR Button
                        OutlinedButton(
                            onClick = onExtractOcr,
                            enabled = !isExtractingOcr,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_drawer_ocr")
                        ) {
                            if (isExtractingOcr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isExtractingOcr) "Extracting" else "OCR",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // EXIF Button (or Specs if Video)
                        OutlinedButton(
                            onClick = onOpenExifEditor,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_drawer_open_exif")
                        ) {
                            Icon(
                                imageVector = if (screenshot.isVideo) Icons.Default.Info else Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (screenshot.isVideo) "Specs" else "EXIF",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    // Horizontal divider separating primary actions and tab content
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Navigation Tabs: Overview, Actions, Technical
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Overview", style = MaterialTheme.typography.labelLarge, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_drawer_overview")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Actions", style = MaterialTheme.typography.labelLarge, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_drawer_actions")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Technical", style = MaterialTheme.typography.labelLarge, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_drawer_technical")
                        )
                    }

                    // Scrollable Drawer Body
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> OverviewSection(
                                screenshot = screenshot,
                                exifData = exifData,
                                collections = collections,
                                onAddToCollectionClick = onAddToCollectionClick,
                                onRemoveFromCollection = onRemoveFromCollection,
                                isEditing = isEditing,
                                editTitle = editTitle,
                                onEditTitleChange = { editTitle = it },
                                editDescription = editDescription,
                                onEditDescriptionChange = { editDescription = it },
                                editNotes = editNotes,
                                onEditNotesChange = { editNotes = it },
                                onStartEdit = { isEditing = true },
                                onCancelEdit = {
                                    editTitle = screenshot.title
                                    editDescription = screenshot.description
                                    editNotes = screenshot.notes ?: ""
                                    isEditing = false
                                },
                                onSaveEdit = {
                                    onSaveDetails(
                                        editTitle.ifBlank { screenshot.title },
                                        editDescription,
                                        editNotes.ifBlank { null }
                                    )
                                    isEditing = false
                                },
                                onAddTagClick = onAddTagClick,
                                onRemoveTag = onRemoveTag
                            )
                            1 -> ActionsSection(
                                screenshot = screenshot,
                                collections = collections,
                                onAddToCollectionClick = onAddToCollectionClick,
                                onRemoveFromCollection = onRemoveFromCollection,
                                onSetReminderClick = onSetReminderClick,
                                onRemoveReminder = onRemoveReminder,
                                onDeleteRequest = onDeleteRequest
                            )
                            2 -> TechnicalSpecsSection(
                                screenshot = screenshot,
                                exifData = exifData
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewSection(
    screenshot: ScreenshotItem,
    exifData: ExifData,
    collections: List<CollectionItem> = emptyList(),
    onAddToCollectionClick: () -> Unit = {},
    onRemoveFromCollection: (String) -> Unit = {},
    isEditing: Boolean,
    editTitle: String,
    onEditTitleChange: (String) -> Unit,
    editDescription: String,
    onEditDescriptionChange: (String) -> Unit,
    editNotes: String,
    onEditNotesChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTag: (String) -> Unit
) {
    val context = LocalContext.current
    var isMetadataCardExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Collections Card (Only shown if item belongs to collections, or in expanded overview)
        if (screenshot.collectionIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Collections",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = onAddToCollectionClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("btn_drawer_add_collection")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Add", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        screenshot.collectionIds.forEach { collectionId ->
                            val collection = collections.find { it.id == collectionId }
                            val collectionName = collection?.name ?: "Collection"

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = collectionName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemoveFromCollection(collectionId) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove from $collectionName",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CARD 1: Description Card (Clickable to edit title, description, and notes)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_drawer_description")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isEditing) {
                    Text(
                        text = "EDIT DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = onEditTitleChange,
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_drawer_title")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = onEditDescriptionChange,
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_drawer_description")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = onEditNotesChange,
                        label = { Text("Personal Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_drawer_notes")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.testTag("btn_drawer_cancel_edit")
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSaveEdit,
                            modifier = Modifier.testTag("btn_drawer_save_edit")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartEdit() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val descText = when {
                                    screenshot.description.isNotBlank() -> screenshot.description
                                    !screenshot.notes.isNullOrBlank() -> screenshot.notes!!
                                    else -> "No description yet. Add a note..."
                                }
                                Text(
                                    text = descText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onStartEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_drawer_edit_details")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // CARD 2: Tags Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_drawer_tags")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    screenshot.tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onRemoveTag(tag) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag $tag",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add Tag Button Pill
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onAddTagClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("btn_drawer_add_tag")
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Tag",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // CARD 3: Location Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_drawer_location")
        ) {
            val hasGps = exifData.latitude != null && exifData.longitude != null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasGps) {
                        if (hasGps) {
                            val uri = Uri.parse("geo:${exifData.latitude},${exifData.longitude}?q=${exifData.latitude},${exifData.longitude}(Media Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(mapIntent)
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (hasGps) {
                            Text(
                                text = "LOCATION",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", exifData.latitude, exifData.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Location",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "No location data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (hasGps) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open in Maps",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // CARD 4: Metadata Card (Expandable Technical Summary)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_drawer_metadata")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isMetadataCardExpanded = !isMetadataCardExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Metadata",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Metadata",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val format = when {
                                screenshot.isVideo -> "MP4"
                                screenshot.filePath.endsWith(".png", ignoreCase = true) -> "PNG"
                                screenshot.filePath.endsWith(".webp", ignoreCase = true) -> "WEBP"
                                else -> "JPEG"
                            }
                            val sizeStr = if (screenshot.fileSize < 1024 * 1024) {
                                "${screenshot.fileSize / 1024} KB"
                            } else {
                                String.format(Locale.US, "%.2f MB", screenshot.fileSize.toDouble() / (1024 * 1024))
                            }
                            val resStr = if (screenshot.width > 0 && screenshot.height > 0) "${screenshot.width} × ${screenshot.height}" else "Unknown size"
                            Text(
                                text = "$format • $resStr • $sizeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isMetadataCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isMetadataCardExpanded) "Collapse metadata" else "Expand metadata",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = isMetadataCardExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.8.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (screenshot.isVideo) {
                            val durSecs = (screenshot.durationMs / 1000) % 60
                            val durMins = (screenshot.durationMs / 1000) / 60
                            DetailRow("Duration", String.format(Locale.US, "%d:%02d", durMins, durSecs))
                            if (screenshot.width > 0) DetailRow("Resolution", "${screenshot.width} × ${screenshot.height} px")
                            val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                            DetailRow("File Size", String.format(Locale.US, "%.2f MB", mb))
                        } else {
                            val camera = exifData.cameraModel ?: (exifData.cameraMake ?: "Android Device")
                            DetailRow("Camera", camera)
                            val dateVal = exifData.dateTaken ?: SimpleDateFormat("yyyy:MM:dd HH:mm", Locale.US).format(Date(screenshot.addedOn))
                            DetailRow("Date Taken", dateVal)
                            if (screenshot.width > 0 && screenshot.height > 0) {
                                DetailRow("Resolution", "${screenshot.width} × ${screenshot.height} px")
                            }
                            val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                            if (mb > 0) {
                                DetailRow("File Size", String.format(Locale.US, "%.2f MB", mb))
                            }
                            if (exifData.iso != null) {
                                DetailRow("Camera Settings", "ISO ${exifData.iso} • ƒ/${exifData.fNumber ?: "N/A"} • ${exifData.exposureTime ?: "N/A"}s")
                            }
                            if (!exifData.software.isNullOrBlank()) {
                                DetailRow("Software", exifData.software)
                            }
                        }
                        if (!screenshot.aiModelUsed.isNullOrBlank()) {
                            DetailRow("AI Model", screenshot.aiModelUsed)
                        }
                        if (screenshot.filePath.isNotBlank()) {
                            DetailRow("File Path", screenshot.filePath)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsSection(
    screenshot: ScreenshotItem,
    collections: List<CollectionItem>,
    onAddToCollectionClick: () -> Unit,
    onRemoveFromCollection: (String) -> Unit,
    onSetReminderClick: () -> Unit,
    onRemoveReminder: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Collections Membership Section
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COLLECTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    TextButton(
                        onClick = onAddToCollectionClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_drawer_add_collection")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Collection", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (screenshot.collectionIds.isEmpty()) {
                    Text(
                        text = "Not added to any collection yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        screenshot.collectionIds.forEach { collectionId ->
                            val collection = collections.find { it.id == collectionId }
                            val collectionName = collection?.name ?: "Collection"

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = collectionName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemoveFromCollection(collectionId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove from $collectionName",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Extracted OCR Text Card
        if (!screenshot.ocrText.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRACTED TEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", screenshot.ocrText))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy extracted text",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = screenshot.ocrText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Detected Links
        if (screenshot.links.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DETECTED LINKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    screenshot.links.forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = link, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(15.dp))
                                }
                                if (link.startsWith("http://") || link.startsWith("https://")) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reminder Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (screenshot.reminderTime != null) Icons.Default.Alarm else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "REMINDER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        if (screenshot.reminderTime != null) {
                            val remStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                                .format(Date(screenshot.reminderTime))
                            Text(
                                text = remStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "No reminder scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (screenshot.reminderTime != null) {
                    IconButton(
                        onClick = onRemoveReminder,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.AlarmOff,
                            contentDescription = "Remove Reminder",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onSetReminderClick,
                        modifier = Modifier.testTag("btn_drawer_set_reminder")
                    ) {
                        Text("Set", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Destructive Action: Delete Media Button
        OutlinedButton(
            onClick = onDeleteRequest,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_drawer_delete")
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Media", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TechnicalSpecsSection(
    screenshot: ScreenshotItem,
    exifData: ExifData
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (screenshot.isVideo) "VIDEO SPECIFICATIONS" else "CAMERA & EXIF HARDWARE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (screenshot.isVideo) {
                val durSecs = (screenshot.durationMs / 1000) % 60
                val durMins = (screenshot.durationMs / 1000) / 60
                DetailRow("Duration", String.format(Locale.US, "%d:%02d", durMins, durSecs))
                if (screenshot.width > 0) DetailRow("Resolution", "${screenshot.width} × ${screenshot.height} px")
                val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                DetailRow("File Size", String.format(Locale.US, "%.2f MB", mb))
            } else {
                val camera = exifData.cameraModel ?: (exifData.cameraMake ?: "Android Device")
                DetailRow("Camera", camera)
                val dateVal = exifData.dateTaken ?: SimpleDateFormat("yyyy:MM:dd HH:mm", Locale.US).format(Date(screenshot.addedOn))
                DetailRow("Date Taken", dateVal)
                if (screenshot.width > 0 && screenshot.height > 0) {
                    DetailRow("Resolution", "${screenshot.width} × ${screenshot.height} px")
                }
                val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                if (mb > 0) {
                    DetailRow("File Size", String.format(Locale.US, "%.2f MB", mb))
                }
                if (exifData.iso != null) {
                    DetailRow("Camera Settings", "ISO ${exifData.iso} • ƒ/${exifData.fNumber ?: "N/A"} • ${exifData.exposureTime ?: "N/A"}s")
                }
                if (!exifData.software.isNullOrBlank()) {
                    DetailRow("Software", exifData.software)
                }
                if (!exifData.artist.isNullOrBlank()) {
                    DetailRow("Artist", exifData.artist)
                }
                if (!exifData.userComment.isNullOrBlank()) {
                    DetailRow("User Comment", exifData.userComment)
                }
            }

            if (!screenshot.aiModelUsed.isNullOrBlank()) {
                DetailRow("AI Model", screenshot.aiModelUsed)
            }

            if (screenshot.filePath.isNotBlank()) {
                DetailRow("File Path", screenshot.filePath)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f, fill = false)
        )
    }
}
