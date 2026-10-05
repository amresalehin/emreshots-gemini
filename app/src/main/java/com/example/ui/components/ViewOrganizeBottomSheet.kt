package com.amresalehin.emreshots.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewOrganizeBottomSheet(
    sheetState: SheetState,
    currentViewMode: GalleryViewMode,
    currentGridCols: Int,
    currentSort: MediaSortOption,
    currentGroupBy: MediaGroupBy,
    onSelectViewMode: (GalleryViewMode, Int) -> Unit,
    onSelectSort: (MediaSortOption) -> Unit,
    onSelectGroupBy: (MediaGroupBy) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "View & Organize",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize layout, sorting, and grouping",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("btn_reset_organize")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // SECTION 1: LAYOUT & VIEW STYLE
            SheetSectionHeader(icon = Icons.Default.GridView, title = "Layout & View Style")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isGrid = currentViewMode == GalleryViewMode.GRID
                val isMasonry = currentViewMode == GalleryViewMode.MASONRY
                val isFeed = currentViewMode == GalleryViewMode.FEED
                val isList = currentViewMode == GalleryViewMode.LIST

                LayoutOptionPill(
                    icon = Icons.Default.GridView,
                    label = "Grid",
                    isSelected = isGrid,
                    onClick = { onSelectViewMode(GalleryViewMode.GRID, currentGridCols.coerceIn(2, 5)) },
                    modifier = Modifier.weight(1f)
                )

                LayoutOptionPill(
                    icon = Icons.Default.Dashboard,
                    label = "Masonry",
                    isSelected = isMasonry,
                    onClick = { onSelectViewMode(GalleryViewMode.MASONRY, currentGridCols.coerceIn(2, 5)) },
                    modifier = Modifier.weight(1f)
                )

                LayoutOptionPill(
                    icon = Icons.Default.ViewAgenda,
                    label = "Feed",
                    isSelected = isFeed,
                    onClick = { onSelectViewMode(GalleryViewMode.FEED, 1) },
                    modifier = Modifier.weight(1f)
                )

                LayoutOptionPill(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "List",
                    isSelected = isList,
                    onClick = { onSelectViewMode(GalleryViewMode.LIST, 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Columns Density (2, 3, 4, 5 Columns)
            if (currentViewMode == GalleryViewMode.GRID || currentViewMode == GalleryViewMode.MASONRY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Columns Density:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 4, 5).forEach { cols ->
                            val isSelected = currentGridCols == cols
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectViewMode(currentViewMode, cols) }
                                    .testTag("btn_grid_cols_$cols")
                            ) {
                                Text(
                                    text = "${cols}x",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: SORT BY
            SheetSectionHeader(icon = Icons.AutoMirrored.Filled.Sort, title = "Sort Order")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaSortOption.entries.forEach { option ->
                    val isSelected = currentSort == option
                    OrganizeChip(
                        text = option.displayName,
                        isSelected = isSelected,
                        onClick = { onSelectSort(option) }
                    )
                }
            }

            // SECTION 3: GROUP BY
            SheetSectionHeader(icon = Icons.Default.Category, title = "Group By")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaGroupBy.entries.forEach { group ->
                    val isSelected = currentGroupBy == group
                    OrganizeChip(
                        text = group.displayName,
                        isSelected = isSelected,
                        onClick = { onSelectGroupBy(group) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LayoutOptionPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OrganizeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
