package com.amresalehin.emreshots.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun GalleryScrollBar(
    totalItemsCount: Int,
    modifier: Modifier = Modifier,
    gridState: LazyGridState? = null,
    staggeredGridState: LazyStaggeredGridState? = null
) {
    if (totalItemsCount <= 4) return // Don't show scroll bar if very few items

    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val isScrollInProgress by remember(gridState, staggeredGridState) {
        derivedStateOf {
            gridState?.isScrollInProgress == true || staggeredGridState?.isScrollInProgress == true
        }
    }

    val currentVisibleIndex by remember(gridState, staggeredGridState) {
        derivedStateOf {
            when {
                gridState != null -> gridState.firstVisibleItemIndex
                staggeredGridState != null -> staggeredGridState.firstVisibleItemIndex
                else -> 0
            }
        }
    }

    val scrollFraction by remember(currentVisibleIndex, totalItemsCount) {
        derivedStateOf {
            if (totalItemsCount > 1) {
                (currentVisibleIndex.toFloat() / (totalItemsCount - 1).toFloat()).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val effectiveFraction = if (isDragging) dragProgress else scrollFraction

    val isActive = isDragging || isScrollInProgress
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.55f,
        animationSpec = tween(durationMillis = 200),
        label = "thumb_alpha"
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isActive) 8.dp else 5.dp,
        animationSpec = tween(durationMillis = 200),
        label = "thumb_width"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp) // Accessible touch target
            .testTag("gallery_scrollbar")
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightDp = 52.dp
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val availableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

        val thumbOffsetY = (effectiveFraction * availableTrackPx).roundToInt()

        // Only the thumb is interactive. The track never intercepts taps,
        // preventing accidental jumps while browsing the gallery.
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle track line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .alpha(if (isActive) 0.3f else 0.15f)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp))
            )

            // Draggable Thumb & Quick Position Badge
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetY) }
                    .fillMaxSize()
            ) {
                // Floating bubble indicator when dragging
                if (isDragging) {
                    val currentItemNumber = ((totalItemsCount - 1) * effectiveFraction).roundToInt() + 1
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 18.dp)
                            .offset(y = 8.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            text = "$currentItemNumber / $totalItemsCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Small, dedicated drag target; surrounding gallery remains touch-transparent.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp)
                        .size(width = 20.dp, height = thumbHeightDp)
                        .pointerInput(totalItemsCount, availableTrackPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragProgress = effectiveFraction
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragProgress = (
                                        dragProgress + dragAmount / availableTrackPx.coerceAtLeast(1f)
                                    ).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = thumbWidth, height = thumbHeightDp)
                            .alpha(thumbAlpha)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                    )
                }
            }
        }
    }
}
