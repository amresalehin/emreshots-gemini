package com.amresalehin.emreshots.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
import com.amresalehin.emreshots.ui.components.GalleryScrollBar
import com.amresalehin.emreshots.ui.components.ScreenshotCard
import com.amresalehin.emreshots.ui.components.ScreenshotFeedCard
import com.amresalehin.emreshots.ui.components.ScreenshotListItem
import com.amresalehin.emreshots.ui.components.ScreenshotMasonryCard
import com.amresalehin.emreshots.viewmodel.ScreenshotFilter
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenshotsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    initialCollectionId: String? = null
) {
    val context = LocalContext.current
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
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
    val isSyncingDeviceMedia by viewModel.isSyncingDeviceMedia.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var hidePermissionBanner by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by remember(initialCollectionId) { mutableStateOf(initialCollectionId) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf("Media_{date}_{index}") }
    LaunchedEffect(autoSyncDeviceMedia, hasMediaPermissions) {
        if (autoSyncDeviceMedia && hasMediaPermissions) {
            viewModel.syncDeviceMedia()
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocusRequester.requestFocus()
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
            val collectionMatch = selectedCollectionId == null || item.collectionIds.contains(selectedCollectionId)
            typeMatch && folderMatch && collectionMatch
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
            GalleryHeader(
                viewModel = viewModel,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchQuery = searchQuery,
                searchFocusRequester = searchFocusRequester,
                showFolderMenu = showFolderMenu,
                onShowFolderMenuChange = { showFolderMenu = it },
                selectedFolder = selectedFolder,
                onSelectedFolderChange = { selectedFolder = it },
                totalDisplayCount = totalDisplayCount,
                availableFolders = availableFolders,
                showMoreMenu = showMoreMenu,
                onShowMoreMenuChange = { showMoreMenu = it },
                sortOption = sortOption,
                onShowSortDialog = { showSortDialog = it },
                groupByOption = groupByOption,
                onShowGroupDialog = { showGroupDialog = it },
                viewMode = viewMode,
                showFileNames = showFileNames,
                showTags = showTags,
                aiVisionPendingCount = aiVisionPendingCount,
                isAnalyzing = isAnalyzing,
                allScreenshots = allScreenshots,
                ocrEnabled = ocrEnabled,
                ocrPendingCount = ocrPendingCount,
                isExtractingOcr = isExtractingOcr,
                onShowBatchRenameDialog = { showBatchRenameDialog = it },
                onNavigateToSettings = onNavigateToSettings,
                scrollBehavior = scrollBehavior
            )

            GalleryFilters(
                viewModel = viewModel,
                selectedFilter = selectedFilter,
                collections = collections,
                selectedCollectionId = selectedCollectionId,
                onSelectedCollectionIdChange = { selectedCollectionId = it },
                showCollectionMenu = showCollectionMenu,
                onShowCollectionMenuChange = { showCollectionMenu = it },
                availableFileTypes = availableFileTypes,
                selectedMediaType = selectedMediaType,
                onSelectedMediaTypeChange = { selectedMediaType = it }
            )

            GalleryStatusSection(
                hasMediaPermissions = hasMediaPermissions,
                hidePermissionBanner = hidePermissionBanner,
                onHidePermissionBanner = { hidePermissionBanner = it },
                permissionLauncher = permissionLauncher,
                isAnalyzing = isAnalyzing,
                isExtractingOcr = isExtractingOcr,
                statusText = statusText,
                ocrStatusText = ocrStatusText
            )

            GalleryContent(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                displayGroups = displayGroups,
                totalDisplayCount = totalDisplayCount,
                searchQuery = searchQuery,
                selectedFilter = selectedFilter,
                viewMode = viewMode,
                gridColumns = gridColumns,
                showFileNames = showFileNames,
                showTags = showTags,
                gridState = gridState,
                staggeredGridState = staggeredGridState,
                navBarBottom = navBarBottom,
                isSyncingDeviceMedia = isSyncingDeviceMedia,
                hasMediaPermissions = hasMediaPermissions,
                permissionLauncher = permissionLauncher,
                mediaPickerLauncher = mediaPickerLauncher,
                onNavigateToDetail = onNavigateToDetail
            )

        }

        GalleryActions(
            viewModel = viewModel,
            mediaPickerLauncher = mediaPickerLauncher,
            showSortDialog = showSortDialog,
            onShowSortDialog = { showSortDialog = it },
            sortOption = sortOption,
            showGroupDialog = showGroupDialog,
            onShowGroupDialog = { showGroupDialog = it },
            groupByOption = groupByOption,
            showBatchRenameDialog = showBatchRenameDialog,
            onShowBatchRenameDialog = { showBatchRenameDialog = it },
            renameTemplate = renameTemplate,
            onRenameTemplateChange = { renameTemplate = it },
            groupedScreenshots = groupedScreenshots
        )
    }
}
