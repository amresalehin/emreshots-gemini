package com.amresalehin.emreshots.ui.screens

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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

@Composable
internal fun GalleryActions(
    viewModel: ScreenshotsViewModel,
    mediaPickerLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    showSortDialog: Boolean,
    onShowSortDialog: (Boolean) -> Unit,
    sortOption: MediaSortOption,
    showGroupDialog: Boolean,
    onShowGroupDialog: (Boolean) -> Unit,
    groupByOption: MediaGroupBy,
    showBatchRenameDialog: Boolean,
    onShowBatchRenameDialog: (Boolean) -> Unit,
    renameTemplate: String,
    onRenameTemplateChange: (String) -> Unit,
    groupedScreenshots: Map<String, List<ScreenshotItem>>
) {
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
    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_media))
}
    }

    if (showSortDialog) {
AlertDialog(
    onDismissRequest = { showSortDialog = false },
    title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sort_media))
        }
    },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setSortOption(option)
                            showSortDialog = false
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("sort_option_${option.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortOption == option,
                        onClick = {
                            viewModel.setSortOption(option)
                            showSortDialog = false
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (sortOption == option) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = { showSortDialog = false }) {
            Text(stringResource(R.string.close))
        }
    }
)
    }

    if (showGroupDialog) {
AlertDialog(
    onDismissRequest = { showGroupDialog = false },
    title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.group_media))
        }
    },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaGroupBy.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setGroupByOption(option)
                            showGroupDialog = false
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("group_by_option_${option.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = groupByOption == option,
                        onClick = {
                            viewModel.setGroupByOption(option)
                            showGroupDialog = false
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (groupByOption == option) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = { showGroupDialog = false }) {
            Text(stringResource(R.string.close))
        }
    }
)
    }

    if (showBatchRenameDialog) {
AlertDialog(
    onDismissRequest = { showBatchRenameDialog = false },
    title = { Text(stringResource(R.string.batch_rename_visible_items)) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = renameTemplate,
                onValueChange = { renameTemplate = it },
                singleLine = true,
                label = { Text(stringResource(R.string.template)) },
                supportingText = { Text(stringResource(R.string.rename_template_tokens)) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.rename_template_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },
    confirmButton = {
        Button(onClick = {
            viewModel.batchRename(groupedScreenshots.values.flatten(), renameTemplate)
            showBatchRenameDialog = false
        }, enabled = groupedScreenshots.values.flatten().isNotEmpty() && renameTemplate.isNotBlank()) {
            Text(stringResource(R.string.rename_count, groupedScreenshots.values.sumOf { it.size }))
        }
    },
    dismissButton = {
        androidx.compose.material3.TextButton(onClick = { showBatchRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
    }
)
    }

}
