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
import com.amresalehin.emreshots.data.model.CollectionItem
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
internal fun GalleryFilters(
    viewModel: ScreenshotsViewModel,
    selectedFilter: ScreenshotFilter,
    collections: List<CollectionItem>,
    selectedCollectionId: String?,
    onSelectedCollectionIdChange: (String?) -> Unit,
    showCollectionMenu: Boolean,
    onShowCollectionMenuChange: (Boolean) -> Unit,
    availableFileTypes: List<String>,
    selectedMediaType: String?,
    onSelectedMediaTypeChange: (String?) -> Unit
) {
// Wrapping filter pills keep both filter and file-type chips accessible without a horizontal-only row.
FlowRow(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
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
            shape = MaterialTheme.shapes.large,
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
    Box {
        val selectedCollectionName = collections.firstOrNull { it.id == selectedCollectionId }?.name
        FilterChip(
            selected = selectedCollectionId != null,
            onClick = { showCollectionMenu = true },
            label = {
                Text(
                    selectedCollectionName ?: stringResource(R.string.collection),
                    fontSize = 12.sp,
                    fontWeight = if (selectedCollectionId != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            trailingIcon = {
                Text("▾", fontSize = 12.sp)
            },
            shape = MaterialTheme.shapes.large,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = null,
            modifier = Modifier.testTag("filter_chip_collection")
        )
        DropdownMenu(
            expanded = showCollectionMenu,
            onDismissRequest = { showCollectionMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_collections)) },
                trailingIcon = { if (selectedCollectionId == null) Text("✓") },
                onClick = {
                    selectedCollectionId = null
                    showCollectionMenu = false
                }
            )
            if (collections.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                collections.take(24).forEach { collection ->
                    DropdownMenuItem(
                        text = { Text(collection.name, maxLines = 1) },
                        trailingIcon = { if (selectedCollectionId == collection.id) Text("✓") },
                        onClick = {
                            selectedCollectionId = collection.id
                            showCollectionMenu = false
                        }
                    )
                }
            }
        }
    }

    availableFileTypes.forEach { type ->
        val isSelected = selectedMediaType == type
        FilterChip(
            selected = isSelected,
            onClick = { selectedMediaType = if (isSelected) null else type },
            label = {
                Text(
                    type.uppercase(Locale.ROOT),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            shape = MaterialTheme.shapes.large,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = null,
            modifier = Modifier.testTag("filter_chip_type_$type")
        )
    }
}


}
