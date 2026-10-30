package com.amresalehin.emreshots.ui.components

import com.example.ui.icons.AppIcons
import com.amresalehin.emreshots.R

import com.amresalehin.emreshots.ui.theme.DangerRose
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun ZoomableImageViewer(
    screenshot: ScreenshotItem,
    exifData: ExifData = ExifData(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ScreenshotItem) -> Unit = {},
    drawerContent: (@Composable () -> Unit)? = null,
    onMore: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageFile = File(screenshot.filePath)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Keep viewer chrome hidden until the user taps the image.
    var showControls by remember { mutableStateOf(false) }
    // One shared sheet state drives both image-swipe and handle gestures.
    var drawerHeightFraction by remember(screenshot.id) { mutableFloatStateOf(0f) }

    fun settleDrawer() {
        drawerHeightFraction = listOf(0f, 0.62f, 0.86f)
            .minBy { kotlin.math.abs(it - drawerHeightFraction) }
    }

    BackHandler(enabled = drawerHeightFraction > 0.04f) {
        drawerHeightFraction = 0f
    }

    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Soft-focus backdrop gives the drawer a blurred scrim without blurring
            // the actual media being viewed.
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))

            // Interactive Zoom & Pan Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!screenshot.isVideo) showControls = !showControls
                            },
                            onDoubleTap = { tapOffset ->
                                if (!screenshot.isVideo) {
                                    if (scale > 1.2f) {
                                        resetZoom()
                                    } else {
                                        scale = 2.5f
                                        // Focus zoom towards double-tap location
                                        offsetX = (size.width / 2f - tapOffset.x) * 1.5f
                                        offsetY = (size.height / 2f - tapOffset.y) * 1.5f
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(screenshot.isVideo) {
                        if (!screenshot.isVideo) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.9f, 6.0f)
                                scale = newScale

                                if (scale > 1f) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                    }
                    .pointerInput(drawerContent) {
                        var totalVerticalDrag = 0f
                        var startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalVerticalDrag = 0f
                                startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (drawerContent != null && scale <= 1.05f &&
                                    (drawerHeightFraction > 0.04f || dragAmount < 0f)
                                ) {
                                    change.consume()
                                    totalVerticalDrag += dragAmount
                                    drawerHeightFraction = (drawerHeightFraction - dragAmount / size.height.coerceAtLeast(1).toFloat())
                                        .coerceIn(0f, 0.86f)
                                }
                            },
                            onDragEnd = {
                                if (drawerContent != null && scale <= 1.05f) {
                                    if (startedWithDrawerClosed && totalVerticalDrag < -48.dp.toPx()) {
                                        drawerHeightFraction = 0.62f
                                    } else if (drawerHeightFraction > 0.04f) {
                                        settleDrawer()
                                    }
                                }
                                totalVerticalDrag = 0f
                            },
                            onDragCancel = { totalVerticalDrag = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (screenshot.isVideo) {
                    InAppVideoPlayer(
                        screenshot = screenshot,
                        modifier = Modifier.fillMaxSize(),
                        onTap = { showControls = !showControls }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (imageFile.exists()) imageFile else screenshot.uriString)
                            .crossfade(true)
                            .build(),
                        contentDescription = screenshot.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            }

            // Top HUD Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("btn_close_fullscreen_image")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = screenshot.title.ifBlank { stringResource(R.string.media_viewer) },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (screenshot.width > 0 && screenshot.height > 0)
                                        "${screenshot.width} × ${screenshot.height} px"
                                    else "High Resolution",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Zoom reset button if zoomed in
                            if (scale > 1.1f) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { resetZoom() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text(
                                            text = "${(scale * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = Color.White)
                            }
                        }
                    }
                }
            }


            // The drawer is completely hidden while closed. A transparent edge
            // gesture target remains at the bottom so an upward swipe can reveal it.
            if (drawerContent != null) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val maxHeightPx = with(density) { maxHeight.toPx() }

                    if (drawerHeightFraction > 0.04f) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(drawerHeightFraction)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .pointerInput(maxHeightPx) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                drawerHeightFraction = (drawerHeightFraction - dragAmount / maxHeightPx)
                                                    .coerceIn(0f, 0.86f)
                                            },
                                            onDragEnd = { settleDrawer() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.size(width = 38.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                                )
                            }
                            if (drawerHeightFraction > 0.18f) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        drawerContent()
                                    }
                                }
                            }
                        }
                    } else {
                        // Larger bottom edge target is a reliable fallback, positioned
                        // above the system navigation/gesture area.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .pointerInput(maxHeightPx) {
                                    var totalVerticalDrag = 0f
                                    var startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            totalVerticalDrag = 0f
                                            startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (drawerContent != null && (drawerHeightFraction > 0.04f || dragAmount < 0f)) {
                                                change.consume()
                                                totalVerticalDrag += dragAmount
                                                drawerHeightFraction = (drawerHeightFraction - dragAmount / maxHeightPx)
                                                    .coerceIn(0f, 0.86f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (startedWithDrawerClosed && totalVerticalDrag < -48.dp.toPx()) {
                                                drawerHeightFraction = 0.62f
                                            } else if (drawerHeightFraction > 0.04f) {
                                                settleDrawer()
                                            }
                                            totalVerticalDrag = 0f
                                        },
                                        onDragCancel = { totalVerticalDrag = 0f }
                                    )
                                }
                        )
                    }
                }
            }

            // Bottom HUD Bar
            AnimatedVisibility(
                visible = showControls && drawerHeightFraction <= 0.04f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("viewer_bottom_toolbar"),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onToggleFavorite(screenshot) }) {
                                    Icon(
                                        imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (screenshot.isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = if (screenshot.isFavorite) DangerRose else Color.White
                                    )
                                }
                                Text("Favorite", color = Color.White, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, screenshot.title)
                                            putExtra(Intent.EXTRA_TEXT, "${screenshot.title}\n${screenshot.description}")
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(shareIntent, "Share media"))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = Color.White)
                                }
                                Text("Share", color = Color.White, fontSize = 12.sp)
                            }
                            if (drawerContent != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        drawerHeightFraction = 0.62f
                                        showControls = false
                                    }) {
                                        Icon(Icons.Default.ExpandLess, contentDescription = "Show details", tint = Color.White)
                                    }
                                    Text("Details", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        // Quick metadata chips row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (screenshot.aiProcessed) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            AppIcons.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = screenshot.aiModelUsed ?: "AI Indexed",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (screenshot.fileSize > 0) {
                                val mb = screenshot.fileSize.toDouble() / (1024 * 1024)
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f MB", mb),
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (exifData.cameraModel != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = exifData.cameraModel,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = "Double-tap or pinch to zoom",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Description if present
                        if (screenshot.description.isNotBlank()) {
                            Text(
                                text = screenshot.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
