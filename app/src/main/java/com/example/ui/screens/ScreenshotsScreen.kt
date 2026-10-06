package com.amresalehin.emreshots.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
import com.amresalehin.emreshots.ui.components.GalleryScrollBar
import com.amresalehin.emreshots.ui.components.ScreenshotCard
import com.amresalehin.emreshots.ui.components.ScreenshotListItem
import com.amresalehin.emreshots.ui.components.ScreenshotMasonryCard
import com.amresalehin.emreshots.viewmodel.ScreenshotFilter
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
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
    val autoSyncDeviceMedia by viewModel.autoSyncDeviceMedia.collectAsStateWithLifecycle()
    val isExtractingOcr by viewModel.isExtractingOcr.collectAsStateWithLifecycle()
    val ocrStatusText by viewModel.ocrStatusText.collectAsStateWithLifecycle()
    val ocrEnabled by viewModel.ocrEnabled.collectAsStateWithLifecycle()
    val showFileNames by viewModel.showFileNames.collectAsStateWithLifecycle()
    val showTags by viewModel.showTags.collectAsStateWithLifecycle()
    val aiOcrModelOptions by viewModel.aiOcrModelOptions.collectAsStateWithLifecycle()
    val ocrAiProviderId by viewModel.ocrAiProviderId.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var hidePermissionBanner by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupByMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showProcessingMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var showAiOcrDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf("Screenshot_{date}_{index}") }
    var pinchNotification by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoSyncDeviceMedia, hasMediaPermissions) {
        if (autoSyncDeviceMedia && hasMediaPermissions) {
            viewModel.syncDeviceMedia()
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocusRequester.requestFocus()
    }

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
            viewMode != GalleryViewMode.GRID ||
            ((viewMode == GalleryViewMode.GRID || viewMode == GalleryViewMode.MASONRY) && gridColumns != 2)

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
    val aiVisionPendingCount = allScreenshots.count { !it.isVideo && !it.aiProcessed }
    val ocrPendingCount = allScreenshots.count { !it.isVideo && it.ocrText.orEmpty().isBlank() }
    val aiOcrEligibleCount = allScreenshots.count { !it.isVideo && it.ocrText.orEmpty().isNotBlank() && !it.aiProcessed }
    val availableFileTypes = remember(allScreenshots) {
        allScreenshots.mapNotNull { item ->
            item.filePath.substringAfterLast(".").takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
        }.distinct().sorted()
    }
    val availableFolders = remember(allScreenshots) {
        allScreenshots.mapNotNull { item ->
            item.filePath.substringBeforeLast("/", "").substringAfterLast("/", "").takeIf { it.isNotBlank() }
        }.distinct().sorted()
    }
    val displayGroups = groupedScreenshots.mapValues { (_, items) ->
        items.filter { item ->
            val typeMatch = selectedMediaType == null || item.filePath.substringAfterLast(".").equals(selectedMediaType, ignoreCase = true)
            val folderMatch = selectedFolder == null || item.filePath.substringBeforeLast("/", "").substringAfterLast("/", "").equals(selectedFolder, ignoreCase = true)
            typeMatch && folderMatch
        }
    }.filterValues { it.isNotEmpty() }
    val totalDisplayCount = displayGroups.values.sumOf { it.size }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Professional gallery header: search, view, sort, and compact overflow.
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search screenshots…", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { isSearchExpanded = false }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).testTag("input_gallery_search")
                        )
                    } else {
                        Box {
                            Surface(
                                onClick = { showFolderMenu = true },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(selectedFolder ?: "All media", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(totalDisplayCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
                                DropdownMenuItem(text = { Text("All media") }, trailingIcon = { if (selectedFolder == null) Text("✓") }, onClick = { selectedFolder = null; showFolderMenu = false })
                                if (availableFolders.isNotEmpty()) {
                                    androidx.compose.material3.HorizontalDivider()
                                    availableFolders.take(24).forEach { folder ->
                                        DropdownMenuItem(text = { Text(folder, maxLines = 1) }, trailingIcon = { if (selectedFolder == folder) Text("✓") }, onClick = { selectedFolder = folder; showFolderMenu = false })
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }, modifier = Modifier.testTag("btn_toggle_search")) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.testTag("btn_more_menu")) {
                            BadgedBox(
                                badge = { if (!showFileNames || !showTags) Badge(modifier = Modifier.size(6.dp), containerColor = MaterialTheme.colorScheme.primary) }
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            
                            DropdownMenuItem(
                                text = { Text("Sort by · ${sortOption.displayName}") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                onClick = { showMoreMenu = false; showSortMenu = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Group by · ${groupByOption.displayName}") },
                                leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                                onClick = { showMoreMenu = false; showGroupByMenu = true }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Batch rename visible") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showMoreMenu = false; showBatchRenameDialog = true },
                                modifier = Modifier.testTag("btn_batch_rename")
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { showMoreMenu = false; onNavigateToSettings() }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )

            // Sleek, Unified Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenshotFilter.entries.filter { it != ScreenshotFilter.ALL }.forEach { filter ->
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
                 availableFileTypes.forEach { type ->
                     val isSelected = selectedMediaType == type
                     FilterChip(
                         selected = isSelected,
                         onClick = { selectedMediaType = if (isSelected) null else type },
                         label = { Text(type.uppercase(Locale.ROOT), fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                         shape = RoundedCornerShape(20.dp),
                         colors = FilterChipDefaults.filterChipColors(
                             selectedContainerColor = MaterialTheme.colorScheme.primary,
                             selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                             containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                             labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                         ),
                         border = null,
                         modifier = Modifier.testTag("filter_chip_type_${type}")
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

            // Unified background processing indicator: sync, local OCR, and AI enrichment/indexing.
            val galleryProcessing = isAnalyzing || isExtractingOcr || statusText != null
            if (galleryProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = ocrStatusText ?: statusText ?: "Processing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            // Gallery overview: pinch in for detail-rich feed, pinch out for three-column masonry.
            PullToRefreshBox(
                isRefreshing = isExtractingOcr || isAnalyzing || statusText != null,
                onRefresh = {
                    if (hasMediaPermissions) viewModel.syncDeviceMedia()
                    else permissionLauncher.launch(DeviceMediaScanner.getRequiredPermissions())
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var scale = 1f
                            var changed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val a = event.changes[0]
                                    val b = event.changes[1]
                                    val previousDistance = hypot(a.previousPosition.x - b.previousPosition.x, a.previousPosition.y - b.previousPosition.y)
                                    val currentDistance = hypot(a.position.x - b.position.x, a.position.y - b.position.y)
                                    if (previousDistance > 12f && currentDistance > 12f) {
                                        scale *= currentDistance / previousDistance
                                        if (!changed && scale > 1.14f) {
                                            viewModel.setViewMode(GalleryViewMode.GRID)
                                            viewModel.setGridColumns(2)
                                            pinchNotification = "2-column grid"
                                            changed = true
                                        } else if (!changed && scale < 0.86f) {
                                            viewModel.setViewMode(GalleryViewMode.MASONRY)
                                            viewModel.setGridColumns(3)
                                            pinchNotification = "3-column masonry"
                                            changed = true
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
            ) {
                if (totalDisplayCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) "No media matching \"$searchQuery\""
                                else if (selectedFilter != ScreenshotFilter.ALL) "No ${selectedFilter.displayName} found"
                                else "No photos or videos yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Import photos or videos, or sync device media to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("btn_empty_import")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Photos or Videos")
                            }
                        }
                    }
                } else {
                    if (viewMode == GalleryViewMode.MASONRY) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(3),
                            state = staggeredGridState,
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp + navBarBottom),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalItemSpacing = 4.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("grid_screenshots")
                        ) {
                            displayGroups.forEach { (header, items) ->
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
                                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                                        showFileName = false,
                                             showTags = false
                                    )
                                }
                            }
                        }
                    } else {
                        val effectiveCols = when (viewMode) {
                            GalleryViewMode.GRID -> gridColumns
                            GalleryViewMode.LIST -> 1
                            GalleryViewMode.MASONRY -> 2
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(effectiveCols),
                            state = gridState,
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp + navBarBottom),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("grid_screenshots")
                        ) {
                            displayGroups.forEach { (header, items) ->
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
                                        GalleryViewMode.LIST -> ScreenshotListItem(
                                            screenshot = item,
                                            onClick = { onNavigateToDetail(item.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                                            showFileName = false,
                                             showTags = false
                                        )
                                        GalleryViewMode.MASONRY -> ScreenshotMasonryCard(
                                        screenshot = item,
                                        onClick = { onNavigateToDetail(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                                        showFileName = false,
                                             showTags = false
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
                        .navigationBarsPadding()
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

        // Quick processing launcher: keep OCR / AI vision discoverable without taking over the gallery.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp)
        ) {
            DropdownMenu(
                expanded = showProcessingMenu,
                onDismissRequest = { showProcessingMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("AI Vision", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (aiVisionPendingCount > 0) "$aiVisionPendingCount images waiting"
                                else "Everything is analyzed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    trailingIcon = { if (aiVisionPendingCount > 0) Text("$aiVisionPendingCount") },
                    enabled = !isAnalyzing && aiVisionPendingCount > 0,
                    onClick = {
                        showProcessingMenu = false
                        viewModel.batchAnalyzeScreenshots(allScreenshots.filter { !it.isVideo && !it.aiProcessed })
                    }
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("OCR", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !ocrEnabled -> "OCR is disabled in Settings"
                                    ocrPendingCount > 0 -> "$ocrPendingCount images waiting"
                                    else -> "All images have OCR text"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (ocrPendingCount > 0) Text("$ocrPendingCount") },
                    enabled = ocrEnabled && !isExtractingOcr && ocrPendingCount > 0,
                    onClick = {
                        showProcessingMenu = false
                        viewModel.batchExtractOcr(allScreenshots, onlyMissing = true)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("AI OCR", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (aiOcrEligibleCount > 0) "$aiOcrEligibleCount images ready"
                                else "Run OCR first",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    trailingIcon = { if (aiOcrEligibleCount > 0) Text("$aiOcrEligibleCount") },
                    enabled = !isAnalyzing && aiOcrEligibleCount > 0,
                    onClick = {
                        showProcessingMenu = false
                        showAiOcrDialog = true
                    }
                )
            }

            FloatingActionButton(
                onClick = { showProcessingMenu = !showProcessingMenu },
                containerColor = if (showProcessingMenu) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (showProcessingMenu) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shape = CircleShape,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("fab_processing_menu")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI and OCR tools"
                )
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
                .navigationBarsPadding()
                .padding(16.dp)
                .size(56.dp)
                .testTag("fab_import_screenshot")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Media")
        }
    }

    if (showBatchRenameDialog) {
        AlertDialog(
            onDismissRequest = { showBatchRenameDialog = false },
            title = { Text("Batch rename visible items") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameTemplate,
                        onValueChange = { renameTemplate = it },
                        singleLine = true,
                        label = { Text("Template") },
                        supportingText = { Text("{index} {date} {time} {title} {collection} {type}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Renames the current filtered/sorted set. Device media uses MediaStore when permitted; imported files are renamed directly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.batchRename(groupedScreenshots.values.flatten(), renameTemplate)
                    showBatchRenameDialog = false
                }, enabled = groupedScreenshots.values.flatten().isNotEmpty() && renameTemplate.isNotBlank()) {
                    Text("Rename ${groupedScreenshots.values.sumOf { it.size }}")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBatchRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAiOcrDialog) {
        val eligibleCount = allScreenshots.count { !it.isVideo && it.ocrText.orEmpty().isNotBlank() && !it.aiProcessed }
        AiOcrEnrichmentDialog(
            options = aiOcrModelOptions,
            eligibleCount = eligibleCount,
            selectedProviderId = ocrAiProviderId,
            onDismiss = { showAiOcrDialog = false },
            onStart = { providerId ->
                viewModel.setOcrAiProviderId(providerId)
                showAiOcrDialog = false
                viewModel.batchAiOcrEnrichment(allScreenshots, providerId)
            },
        )
    }
}
@Composable
private fun GalleryMenuToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GalleryMenuChoiceRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    twoRows: Boolean = false,
) {
    val rows = if (twoRows) options.chunked(3) else listOf(options)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { option ->
                    Surface(
                        onClick = { onSelected(option) },
                        color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (option == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(option, textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AiOcrEnrichmentDialog(
    options: List<com.amresalehin.emreshots.viewmodel.AiOcrModelOption>,
    eligibleCount: Int,
    selectedProviderId: String,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var selectedProviderId by remember(options, selectedProviderId) { mutableStateOf(selectedProviderId.ifBlank { options.firstOrNull()?.providerId.orEmpty() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI OCR enrichment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$eligibleCount OCR-ready images can be enriched. OCR text only is sent to the selected AI model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (options.isEmpty()) {
                    Text("No configured AI model is available. Add an API key in Settings.", color = MaterialTheme.colorScheme.error)
                } else {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedProviderId = option.providerId }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedProviderId == option.providerId, onClick = { selectedProviderId = option.providerId })
                            Column {
                                Text(option.modelName, fontWeight = FontWeight.SemiBold)
                                Text(option.providerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(selectedProviderId) }, enabled = selectedProviderId.isNotBlank() && eligibleCount > 0) { Text("Enrich") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
