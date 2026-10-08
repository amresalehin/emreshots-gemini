@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.TopAppBarScrollBehavior
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
import com.amresalehin.emreshots.data.model.ScreenshotItem
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

@Composable
internal fun GalleryHeader(
    viewModel: ScreenshotsViewModel,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchQuery: String,
    searchFocusRequester: FocusRequester,
    showFolderMenu: Boolean,
    onShowFolderMenuChange: (Boolean) -> Unit,
    selectedFolder: String?,
    totalDisplayCount: Int,
    availableFolders: List<String>,
    showMoreMenu: Boolean,
    onShowMoreMenuChange: (Boolean) -> Unit,
    sortOption: MediaSortOption,
    onShowSortDialog: (Boolean) -> Unit,
    groupByOption: MediaGroupBy,
    onShowGroupDialog: (Boolean) -> Unit,
    viewMode: GalleryViewMode,
    showFileNames: Boolean,
    showTags: Boolean,
    aiVisionPendingCount: Int,
    isAnalyzing: Boolean,
    allScreenshots: List<ScreenshotItem>,
    ocrEnabled: Boolean,
    ocrPendingCount: Int,
    isExtractingOcr: Boolean,
    onShowBatchRenameDialog: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
// Professional gallery header: search, view, sort, and compact overflow.
TopAppBar(
    title = {
        if (isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.gallery_search_placeholder), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search), modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { isSearchExpanded = false }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
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
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(selectedFolder ?: stringResource(R.string.all_media), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(totalDisplayCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.all_media)) }, trailingIcon = { if (selectedFolder == null) Text("✓") }, onClick = { selectedFolder = null; showFolderMenu = false })
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
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
            }
        }

        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.testTag("btn_more_menu")
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.sort_by))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = sortOption.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                    onClick = { showMoreMenu = false; showSortDialog = true },
                    modifier = Modifier.testTag("btn_sort_option")
                )
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.group_by))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = groupByOption.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                    onClick = { showMoreMenu = false; showGroupDialog = true },
                    modifier = Modifier.testTag("btn_group_by_option")
                )
                androidx.compose.material3.HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.view),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    GalleryMenuChoiceRow(
                        options = GalleryViewMode.entries.map { it.displayName },
                        selected = viewMode.displayName,
                        onSelected = { selected ->
                            GalleryViewMode.entries
                                .firstOrNull { it.displayName == selected }
                                ?.let(viewModel::setViewMode)
                        },
                        twoRows = true
                    )
                }
                GalleryMenuToggle(
                    label = stringResource(R.string.show_file_names),
                    checked = showFileNames,
                    onCheckedChange = viewModel::setShowFileNames
                )
                GalleryMenuToggle(
                    label = stringResource(R.string.show_tags),
                    checked = showTags,
                    onCheckedChange = viewModel::setShowTags
                )
                androidx.compose.material3.HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.ai_vision), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (aiVisionPendingCount > 0) stringResource(R.string.media_waiting_for_analysis, aiVisionPendingCount) else stringResource(R.string.everything_is_analyzed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    enabled = !isAnalyzing && aiVisionPendingCount > 0,
                    onClick = {
                        showMoreMenu = false
                        viewModel.batchAnalyzeScreenshots(allScreenshots.filter { !it.isVideo && !it.aiProcessed })
                    },
                    modifier = Modifier.testTag("menu_ai_vision")
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.ocr), fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !ocrEnabled -> stringResource(R.string.ocr_disabled_in_settings)
                                    ocrPendingCount > 0 -> "stringResource(R.string.media_waiting_for_ocr, ocrPendingCount)"
                                    else -> "All media has OCR text"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    enabled = ocrEnabled && !isExtractingOcr && ocrPendingCount > 0,
                    onClick = {
                        showMoreMenu = false
                        viewModel.batchExtractOcr(allScreenshots, onlyMissing = true)
                    },
                    modifier = Modifier.testTag("menu_ocr")
                )
                androidx.compose.material3.HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_rename_visible)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { showMoreMenu = false; showBatchRenameDialog = true },
                    modifier = Modifier.testTag("btn_batch_rename")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
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


}

@Composable
internal fun GalleryMenuToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun GalleryMenuChoiceRow(
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("gallery_view_option_" + option.lowercase())
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option,
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
