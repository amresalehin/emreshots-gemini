package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GalleryViewMode
import com.example.data.model.MediaGroupBy
import com.example.data.model.MediaSortOption
import com.example.service.media.DeviceMediaScanner
import com.example.ui.components.AiIndexerBottomSheet
import com.example.ui.components.GalleryScrollBar
import com.example.ui.components.ScreenshotCard
import com.example.ui.components.ScreenshotFeedCard
import com.example.ui.components.ScreenshotListItem
import com.example.ui.components.ScreenshotMasonryCard
import com.example.ui.components.ViewOrganizeBottomSheet
import com.example.viewmodel.ScreenshotFilter
import com.example.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val statusText by viewModel.analysisStatusText.collectAsStateWithLifecycle()
    val hasMediaPermissions by viewModel.hasMediaPermissions.collectAsStateWithLifecycle()

    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val groupByOption by viewModel.groupByOption.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val groupedScreenshots by viewModel.groupedScreenshots.collectAsStateWithLifecycle()
    val indexingState by viewModel.indexingState.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val autoWriteExifSetting by viewModel.autoWriteExifSetting.collectAsStateWithLifecycle()

    val unindexedCount = remember(allScreenshots) {
        allScreenshots.count { !it.aiProcessed }
    }
    val indexedCount = remember(allScreenshots) {
        allScreenshots.count { it.aiProcessed }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var hidePermissionBanner by remember { mutableStateOf(false) }
    var showOrganizeSheet by remember { mutableStateOf(false) }
    var showAiIndexerSheet by remember { mutableStateOf(false) }
    var pinchNotification by remember { mutableStateOf<String?>(null) }
    val organizeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiIndexerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val animatedProgress by animateFloatAsState(
        targetValue = if (indexingState.isIndexing) indexingState.progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "indexing_progress_anim"
    )

    LaunchedEffect(pinchNotification) {
        if (pinchNotification != null) {
            delay(1200)
            pinchNotification = null
        }
    }

    val gridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()

    val isCustomOrganized = sortOption != MediaSortOption.NEWEST ||
            groupByOption != MediaGroupBy.NONE ||
            viewMode != GalleryViewMode.GRID

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.any { it }
        viewModel.onPermissionsResult(isGranted)
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importMediaFromUri(uri)
        }
    }

    val videoCount = allScreenshots.count { it.isVideo }
    val photoCount = allScreenshots.size - videoCount
    val totalDisplayCount = groupedScreenshots.values.sumOf { it.size }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean, Decluttered Top App Bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EmreShots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${allScreenshots.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Search Action
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.testTag("btn_toggle_search")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // AI Indexer Action
                    IconButton(
                        onClick = { showAiIndexerSheet = true },
                        modifier = Modifier.testTag("btn_ai_indexer")
                    ) {
                        BadgedBox(
                            badge = {
                                if (indexingState.isIndexing) {
                                    Badge(
                                        modifier = Modifier.size(8.dp),
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                } else if (unindexedCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    ) {
                                        Text(
                                            text = if (unindexedCount > 99) "99+" else "$unindexedCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Indexer",
                                tint = if (indexingState.isIndexing || unindexedCount > 0)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Sync Gallery Media
                    IconButton(
                        onClick = {
                            if (hasMediaPermissions) {
                                viewModel.syncDeviceMedia()
                            } else {
                                permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions())
                            }
                        },
                        modifier = Modifier.testTag("btn_sync_media")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Gallery",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Direct View & Organize Button
                    IconButton(
                        onClick = { showOrganizeSheet = true },
                        modifier = Modifier.testTag("btn_organize_view")
                    ) {
                        BadgedBox(
                            badge = {
                                if (isCustomOrganized) {
                                    Badge(
                                        modifier = Modifier.size(6.dp),
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "View & Organize",
                                tint = if (isCustomOrganized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Direct Settings Action
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("btn_top_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Animated Expandable Clean Search Bar
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search title, tags, or links...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_gallery_search")
                    )
                }
            }

            // Sleek, Unified Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick AI Indexer Pill if unindexed items exist and not currently indexing
                if (unindexedCount > 0 && !indexingState.isIndexing) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showAiIndexerSheet = true }
                            .testTag("btn_quick_ai_index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI Index ($unindexedCount)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Quick Organize Indicator Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isCustomOrganized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showOrganizeSheet = true }
                        .testTag("btn_quick_organize_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = if (isCustomOrganized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (groupByOption != MediaGroupBy.NONE) "${sortOption.displayName} • ${groupByOption.displayName}" else sortOption.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isCustomOrganized) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCustomOrganized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ScreenshotFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            // Compact, non-intrusive permission card (only if not granted and not dismissed)
            if (!hasMediaPermissions && !hidePermissionBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("banner_permission_request")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PermMedia,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grant media access to sync all photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions())
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("btn_grant_media_permission")
                            ) {
                                Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { hidePermissionBanner = true },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic AI Indexer Progress Bar Card in Gallery
            AnimatedVisibility(
                visible = indexingState.isIndexing || isAnalyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("card_indexing_progress")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (indexingState.isIndexing) "AI Gallery Indexer Running" else "AI Analysis Active",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (indexingState.isIndexing)
                                            "${indexingState.current} of ${indexingState.total} • ${indexingState.currentItemTitle}"
                                        else statusText ?: "Analyzing media with AI...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (indexingState.isIndexing && indexingState.total > 0) {
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.cancelIndexing() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("btn_cancel_indexing")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Indexing",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Smooth Dynamic Progress Bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .testTag("bar_indexing_progress"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )

                        // Bottom status info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (indexingState.currentModel.isNotBlank())
                                    "Model: ${indexingState.currentModel}"
                                else "Model: Gemini",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            if (indexingState.isIndexing) {
                                Text(
                                    text = "${indexingState.successCount} indexed" +
                                            if (indexingState.failureCount > 0) " (${indexingState.failureCount} failed)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Gallery Grid / Feed / List with Section Grouping & Pinch-to-Change-View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(viewMode, gridColumns) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zoomFactor = 1f
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled && event.changes.size >= 2) {
                                    val p1 = event.changes[0].position
                                    val p2 = event.changes[1].position
                                    val prevP1 = event.changes[0].previousPosition
                                    val prevP2 = event.changes[1].previousPosition
                                    val currentDist = hypot(p1.x - p2.x, p1.y - p2.y)
                                    val prevDist = hypot(prevP1.x - prevP2.x, prevP1.y - prevP2.y)
                                    if (prevDist > 10f && currentDist > 10f) {
                                        val delta = currentDist / prevDist
                                        zoomFactor *= delta
                                        if (zoomFactor > 1.25f) {
                                            // Pinch Out (Expand / Zoom In -> fewer columns / larger view)
                                            when {
                                                viewMode == GalleryViewMode.GRID && gridColumns > 2 -> {
                                                    val newCols = gridColumns - 1
                                                    viewModel.setGridColumns(newCols)
                                                    pinchNotification = "$newCols Columns Grid"
                                                }
                                                viewMode == GalleryViewMode.GRID && gridColumns == 2 -> {
                                                    viewModel.setViewMode(GalleryViewMode.MASONRY)
                                                    pinchNotification = "Masonry View"
                                                }
                                                viewMode == GalleryViewMode.MASONRY -> {
                                                    viewModel.setViewMode(GalleryViewMode.FEED)
                                                    pinchNotification = "Feed View"
                                                }
                                                viewMode == GalleryViewMode.LIST -> {
                                                    viewModel.setViewMode(GalleryViewMode.MASONRY)
                                                    pinchNotification = "Masonry View"
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                            zoomFactor = 1f
                                        } else if (zoomFactor < 0.75f) {
                                            // Pinch In (Contract / Zoom Out -> more columns / denser view)
                                            when {
                                                viewMode == GalleryViewMode.FEED -> {
                                                    viewModel.setViewMode(GalleryViewMode.MASONRY)
                                                    pinchNotification = "Masonry View"
                                                }
                                                viewMode == GalleryViewMode.MASONRY -> {
                                                    viewModel.setViewMode(GalleryViewMode.GRID)
                                                    viewModel.setGridColumns(2)
                                                    pinchNotification = "2 Columns Grid"
                                                }
                                                viewMode == GalleryViewMode.GRID && gridColumns < 5 -> {
                                                    val newCols = gridColumns + 1
                                                    viewModel.setGridColumns(newCols)
                                                    pinchNotification = "$newCols Columns Grid"
                                                }
                                                viewMode == GalleryViewMode.LIST -> {
                                                    viewModel.setViewMode(GalleryViewMode.GRID)
                                                    viewModel.setGridColumns(3)
                                                    pinchNotification = "3 Columns Grid"
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                            zoomFactor = 1f
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                if (totalDisplayCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (searchQuery.isNotEmpty() || selectedFilter != ScreenshotFilter.ALL) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matches for \"$searchQuery\""
                                    else "No ${selectedFilter.displayName} found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Try adjusting your search terms or filter selection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (searchQuery.isNotEmpty()) {
                                        Button(
                                            onClick = { viewModel.setSearchQuery("") },
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Text("Clear Search")
                                        }
                                    }
                                    if (selectedFilter != ScreenshotFilter.ALL) {
                                        OutlinedButton(
                                            onClick = { viewModel.setFilter(ScreenshotFilter.ALL) },
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Text("Show All Media")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Your Visual Library Awaits",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Organize, analyze with AI, extract text with OCR, and manage EXIF metadata on all your media.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Feature Highlights Chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 24.dp)
                                ) {
                                    listOf("AI Vision", "OCR Text", "EXIF GPS", "Video Player").forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "✦ $tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            mediaPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_empty_import")
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Media", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (hasMediaPermissions) {
                                                viewModel.syncDeviceMedia()
                                            } else {
                                                permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions())
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Gallery")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (viewMode == GalleryViewMode.MASONRY) {
                        val staggeredCols = if (gridColumns >= 4) 3 else gridColumns.coerceIn(2, 3)
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(staggeredCols),
                            state = staggeredGridState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("grid_screenshots")
                        ) {
                            groupedScreenshots.forEach { (header, items) ->
                                if (header.isNotBlank()) {
                                    item(span = StaggeredGridItemSpan.FullLine, key = "staggered_header_$header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = header,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${items.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = items,
                                    key = { it.id },
                                    contentType = { it.mediaType }
                                ) { item ->
                                    ScreenshotMasonryCard(
                                        screenshot = item,
                                        onClick = { onNavigateToDetail(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                                    )
                                }
                            }
                        }
                    } else {
                        val effectiveCols = when (viewMode) {
                            GalleryViewMode.GRID -> gridColumns
                            GalleryViewMode.FEED -> 1
                            GalleryViewMode.LIST -> 1
                            GalleryViewMode.MASONRY -> 2
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(effectiveCols),
                            state = gridState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("grid_screenshots")
                        ) {
                            groupedScreenshots.forEach { (header, items) ->
                                if (header.isNotBlank()) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = header,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${items.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = items,
                                    key = { it.id },
                                    contentType = { it.mediaType }
                                ) { item ->
                                    when (viewMode) {
                                        GalleryViewMode.GRID -> ScreenshotCard(
                                            screenshot = item,
                                            onClick = { onNavigateToDetail(item.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                                        )
                                        GalleryViewMode.FEED -> ScreenshotFeedCard(
                                            screenshot = item,
                                            onClick = { onNavigateToDetail(item.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                                        )
                                        GalleryViewMode.LIST -> ScreenshotListItem(
                                            screenshot = item,
                                            onClick = { onNavigateToDetail(item.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                                        )
                                        GalleryViewMode.MASONRY -> ScreenshotMasonryCard(
                                            screenshot = item,
                                            onClick = { onNavigateToDetail(item.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sleek Visible Fast Scroll Bar positioned smoothly within gallery viewport
                GalleryScrollBar(
                    totalItemsCount = totalDisplayCount,
                    gridState = if (viewMode != GalleryViewMode.MASONRY) gridState else null,
                    staggeredGridState = if (viewMode == GalleryViewMode.MASONRY) staggeredGridState else null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 8.dp, bottom = 80.dp)
                )

                // Animated Pinch Gesture Feedback Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = pinchNotification != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = pinchNotification ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Clean, Compact Floating Action Button
        FloatingActionButton(
            onClick = {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .testTag("fab_import_screenshot")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Media")
        }
    }

    // Modal Bottom Sheet for View & Organize
    if (showOrganizeSheet) {
        ViewOrganizeBottomSheet(
            sheetState = organizeSheetState,
            currentViewMode = viewMode,
            currentGridCols = gridColumns,
            currentSort = sortOption,
            currentGroupBy = groupByOption,
            onSelectViewMode = { mode, cols ->
                viewModel.setViewMode(mode)
                if (mode == GalleryViewMode.GRID) {
                    viewModel.setGridColumns(cols)
                }
            },
            onSelectSort = { viewModel.setSortOption(it) },
            onSelectGroupBy = { viewModel.setGroupByOption(it) },
            onReset = {
                viewModel.setViewMode(GalleryViewMode.GRID)
                viewModel.setGridColumns(2)
                viewModel.setSortOption(MediaSortOption.NEWEST)
                viewModel.setGroupByOption(MediaGroupBy.NONE)
            },
            onDismiss = {
                coroutineScope.launch {
                    organizeSheetState.hide()
                    showOrganizeSheet = false
                }
            }
        )
    }

    // Modal Bottom Sheet for AI Gallery Indexer
    if (showAiIndexerSheet) {
        AiIndexerBottomSheet(
            sheetState = aiIndexerSheetState,
            totalCount = allScreenshots.size,
            unindexedCount = unindexedCount,
            indexedCount = indexedCount,
            isIndexing = indexingState.isIndexing,
            activeProvider = activeProvider,
            autoWriteExif = autoWriteExifSetting,
            onStartIndexing = { onlyUnindexed, autoWriteExif ->
                viewModel.startIndexing(onlyUnindexed = onlyUnindexed, autoWriteExif = autoWriteExif)
            },
            onCancelIndexing = { viewModel.cancelIndexing() },
            onConfigureProvider = onNavigateToSettings,
            onDismiss = {
                coroutineScope.launch {
                    aiIndexerSheetState.hide()
                    showAiIndexerSheet = false
                }
            }
        )
    }
}
