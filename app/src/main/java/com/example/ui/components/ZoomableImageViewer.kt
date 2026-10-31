package com.amresalehin.emreshots.ui.components

import com.example.ui.icons.AppIcons
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.ui.theme.DangerRose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ZoomableImageViewer(
    screenshots: List<ScreenshotItem>,
    initialIndex: Int = 0,
    onIndexChanged: (Int) -> Unit = {},
    exifDataMap: Map<String, ExifData> = emptyMap(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ScreenshotItem) -> Unit = {},
    onEdit: (ScreenshotItem) -> Unit = {},
    onDelete: (ScreenshotItem) -> Unit = {},
    onShare: ((ScreenshotItem) -> Unit)? = null,
    showDetailsButton: Boolean = false,
    drawerContent: (@Composable (ScreenshotItem) -> Unit)? = null,
    onMore: (ScreenshotItem) -> Unit = {},
    overflowMenuContent: (@Composable (ScreenshotItem, onDismissMenu: () -> Unit) -> Unit)? = null
) {
    if (screenshots.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.size - 1)
    ) { screenshots.size }

    val currentScreenshot = screenshots.getOrNull(pagerState.currentPage) ?: screenshots[0]
    val exifData = exifDataMap[currentScreenshot.id] ?: ExifData()
    val imageFile = File(currentScreenshot.filePath)

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val dismissOffsetY = remember { Animatable(0f) }

    var showControls by remember { mutableStateOf(true) }
    var isFillMode by remember { mutableStateOf(false) }
    val drawerAnim = remember(currentScreenshot.id) { Animatable(0f) }
    val drawerHeightFraction = drawerAnim.value

    fun animateDrawerTo(targetFraction: Float) {
        coroutineScope.launch {
            drawerAnim.animateTo(
                targetValue = targetFraction.coerceIn(0f, 0.86f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun settleDrawer(velocity: Float = 0f) {
        val current = drawerAnim.value
        val target = when {
            velocity > 350f -> 0f
            velocity < -350f -> if (current > 0.65f) 0.86f else 0.62f
            else -> listOf(0f, 0.62f, 0.86f).minBy { kotlin.math.abs(it - current) }
        }
        animateDrawerTo(target)
    }

    // Reset zoom and offsets whenever user switches pages
    LaunchedEffect(pagerState.currentPage) {
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
        dismissOffsetY.snapTo(0f)
        drawerAnim.snapTo(0f)
        onIndexChanged(pagerState.currentPage)
    }

    // BackHandler 1: Drawer collapse to return to fullscreen photo/video viewer
    BackHandler(enabled = drawerHeightFraction > 0.04f) {
        animateDrawerTo(0f)
    }

    // BackHandler 2: Animated zoom-out if user is zoomed into an image
    BackHandler(enabled = drawerHeightFraction <= 0.04f && scaleAnim.value > 1.05f) {
        coroutineScope.launch {
            scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
            offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun resetZoomAnimated() {
        coroutineScope.launch {
            scaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            offsetXAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            offsetYAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Dialog(
        onDismissRequest = {
            if (drawerHeightFraction > 0.04f) {
                animateDrawerTo(0f)
            } else if (scaleAnim.value > 1.05f) {
                resetZoomAnimated()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

        DisposableEffect(dialogWindow) {
            if (dialogWindow == null) {
                onDispose { }
            } else {
                val insetsController = WindowCompat.getInsetsController(
                    dialogWindow,
                    dialogWindow.decorView
                )
                val statusBarWasVisible = ViewCompat.getRootWindowInsets(dialogWindow.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
                val previousSystemBarsBehavior = insetsController.systemBarsBehavior

                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.statusBars())

                dialogWindow.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )

                onDispose {
                    if (statusBarWasVisible) {
                        insetsController.show(WindowInsetsCompat.Type.statusBars())
                    }
                    insetsController.systemBarsBehavior = previousSystemBarsBehavior
                }
            }
        }

        val dismissProgress = (dismissOffsetY.value / 600f).coerceIn(0f, 1f)
        val scrimAlpha = (1f - dismissProgress * 0.75f).coerceIn(0.15f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        ) {
            // Horizontal Pager with native two-way swipe when unzoomed
            val canPage = scaleAnim.value <= 1.05f && drawerHeightFraction <= 0.04f && dismissOffsetY.value == 0f

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = canPage,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = dismissOffsetY.value.roundToInt()) }
            ) { page ->
                val pageItem = screenshots.getOrNull(page) ?: return@HorizontalPager
                val isCurrent = page == pagerState.currentPage
                val pageFile = File(pageItem.filePath)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isCurrent, pageItem.isVideo) {
                            if (!isCurrent) return@pointerInput
                            detectTapGestures(
                                onTap = {
                                    if (!pageItem.isVideo) showControls = !showControls
                                },
                                onDoubleTap = { tapOffset ->
                                    if (!pageItem.isVideo) {
                                        if (scaleAnim.value > 1.2f) {
                                            resetZoomAnimated()
                                        } else {
                                            coroutineScope.launch {
                                                val targetScale = 2.5f
                                                val targetOffsetX = (size.width / 2f - tapOffset.x) * 1.5f
                                                val targetOffsetY = (size.height / 2f - tapOffset.y) * 1.5f
                                                val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                                                val maxOffsetY = (size.height * (targetScale - 1f)) / 2f
                                                scaleAnim.animateTo(targetScale, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                                offsetXAnim.animateTo(targetOffsetX.coerceIn(-maxOffsetX, maxOffsetX), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                                offsetYAnim.animateTo(targetOffsetY.coerceIn(-maxOffsetY, maxOffsetY), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(isCurrent, pageItem.isVideo) {
                            if (!isCurrent || pageItem.isVideo) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    val isZoomed = scaleAnim.value > 1.05f

                                    if (pointerCount >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            coroutineScope.launch {
                                                val newScale = (scaleAnim.value * zoomChange).coerceIn(0.70f, 6.0f)
                                                scaleAnim.snapTo(newScale)

                                                if (newScale > 1f) {
                                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                                    offsetXAnim.snapTo((offsetXAnim.value + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX))
                                                    offsetYAnim.snapTo((offsetYAnim.value + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY))
                                                } else {
                                                    offsetXAnim.snapTo(0f)
                                                    offsetYAnim.snapTo(0f)
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } else if (pointerCount == 1 && isZoomed) {
                                        val change = event.changes.firstOrNull { it.pressed }
                                        if (change != null) {
                                            val dragAmount = change.positionChange()
                                            val currentScale = scaleAnim.value
                                            val maxOffsetX = (size.width * (currentScale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (currentScale - 1f)) / 2f
                                            coroutineScope.launch {
                                                offsetXAnim.snapTo((offsetXAnim.value + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX))
                                                offsetYAnim.snapTo((offsetYAnim.value + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY))
                                            }
                                            change.consume()
                                        }
                                    }
                                    // When pointerCount == 1 and !isZoomed:
                                    // Do NOT consume pointer events! HorizontalPager handles sideways gestures cleanly.
                                } while (event.changes.any { it.pressed })

                                if (scaleAnim.value < 1.0f) {
                                    coroutineScope.launch {
                                        scaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        offsetXAnim.animateTo(0f)
                                        offsetYAnim.animateTo(0f)
                                    }
                                }
                            }
                        }
                        .pointerInput(isCurrent, drawerContent) {
                            if (!isCurrent) return@pointerInput
                            var totalVerticalDrag = 0f
                            var startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                            var lastDragAmount = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    totalVerticalDrag = 0f
                                    startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                                    lastDragAmount = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (scaleAnim.value <= 1.05f) {
                                        change.consume()
                                        totalVerticalDrag += dragAmount
                                        lastDragAmount = dragAmount

                                        if (startedWithDrawerClosed && dragAmount > 0f && drawerHeightFraction <= 0.04f) {
                                            // Swipe down to dismiss gesture tracking
                                            coroutineScope.launch {
                                                dismissOffsetY.snapTo((dismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                                            }
                                        } else if (drawerContent != null && (drawerHeightFraction > 0.04f || dragAmount < 0f)) {
                                            // Swipe up to expand drawer
                                            coroutineScope.launch {
                                                drawerAnim.snapTo(
                                                    (drawerAnim.value - dragAmount / size.height.coerceAtLeast(1).toFloat())
                                                        .coerceIn(0f, 0.86f)
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dismissOffsetY.value > 120.dp.toPx()) {
                                        // Dismiss threshold reached
                                        coroutineScope.launch {
                                            dismissOffsetY.animateTo(1200f, spring(stiffness = Spring.StiffnessMedium))
                                            onDismiss()
                                        }
                                    } else if (dismissOffsetY.value > 0f) {
                                        // Snap dismiss offset back
                                        coroutineScope.launch {
                                            dismissOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    }

                                    // Settle under-zoomed pinch release
                                    if (scaleAnim.value < 1.0f) {
                                        coroutineScope.launch {
                                            scaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            offsetXAnim.animateTo(0f)
                                            offsetYAnim.animateTo(0f)
                                        }
                                    }

                                    if (drawerContent != null && scaleAnim.value <= 1.05f) {
                                        if (startedWithDrawerClosed && totalVerticalDrag < -48.dp.toPx()) {
                                            animateDrawerTo(0.62f)
                                        } else if (drawerHeightFraction > 0.04f) {
                                            settleDrawer(lastDragAmount * 20f)
                                        }
                                    }
                                    totalVerticalDrag = 0f
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        dismissOffsetY.animateTo(0f)
                                    }
                                    totalVerticalDrag = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (pageItem.isVideo) {
                        InAppVideoPlayer(
                            screenshot = pageItem,
                            modifier = Modifier.fillMaxSize(),
                            onTap = { showControls = !showControls }
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (pageFile.exists()) pageFile else pageItem.uriString)
                                .crossfade(true)
                                .build(),
                            contentDescription = pageItem.title,
                            contentScale = if (isFillMode) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = if (isCurrent) scaleAnim.value else 1f,
                                    scaleY = if (isCurrent) scaleAnim.value else 1f,
                                    translationX = if (isCurrent) offsetXAnim.value else 0f,
                                    translationY = if (isCurrent) offsetYAnim.value else 0f
                                )
                        )
                    }
                }
            }

            // Top Floating Controls
            AnimatedVisibility(
                visible = showControls && dismissOffsetY.value == 0f,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingCircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        testTag = "btn_detail_back",
                        onClick = onDismiss
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (scaleAnim.value > 1.1f) {
                            Surface(
                                onClick = { resetZoomAnimated() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "${(scaleAnim.value * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        FloatingCircleIconButton(
                            icon = AppIcons.FitScreen,
                            contentDescription = if (isFillMode) stringResource(R.string.fit_to_screen) else stringResource(R.string.fill_screen),
                            testTag = "btn_detail_aspect_toggle",
                            tint = if (isFillMode) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = { isFillMode = !isFillMode }
                        )

                        FloatingCircleIconButton(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            testTag = "btn_detail_top_share",
                            onClick = {
                                if (onShare != null) {
                                    onShare(currentScreenshot)
                                } else {
                                    shareScreenshot(currentScreenshot, context)
                                }
                            }
                        )

                        FloatingCircleIconButton(
                            icon = if (currentScreenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (currentScreenshot.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                            tint = if (currentScreenshot.isFavorite) DangerRose else Color.White,
                            testTag = "btn_detail_favorite",
                            onClick = { onToggleFavorite(currentScreenshot) }
                        )

                        var showOverflowMenu by remember { mutableStateOf(false) }

                        Box {
                            FloatingCircleIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_actions),
                                testTag = "btn_detail_overflow",
                                onClick = {
                                    if (overflowMenuContent != null) {
                                        showOverflowMenu = true
                                    } else {
                                        onMore(currentScreenshot)
                                    }
                                }
                            )

                            if (overflowMenuContent != null) {
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    overflowMenuContent(currentScreenshot) {
                                        showOverflowMenu = false
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Drawer
            if (drawerContent != null) {
                // Dimming scrim when drawer is partially or fully visible according to native Android
                AnimatedVisibility(
                    visible = drawerHeightFraction > 0.04f,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (drawerHeightFraction * 0.65f).coerceIn(0f, 0.55f)))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                animateDrawerTo(0f)
                            }
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val maxHeightPx = with(density) { maxHeight.toPx() }

                    if (drawerHeightFraction > 0.04f) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(drawerHeightFraction),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .pointerInput(maxHeightPx) {
                                            var dragVelocity = 0f
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragVelocity = dragAmount
                                                    coroutineScope.launch {
                                                        drawerAnim.snapTo(
                                                            (drawerAnim.value - dragAmount / maxHeightPx).coerceIn(0f, 0.86f)
                                                        )
                                                    }
                                                },
                                                onDragEnd = { settleDrawer(dragVelocity * 10f) },
                                                onDragCancel = { settleDrawer() }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(width = 38.dp, height = 4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    )
                                }
                                if (drawerHeightFraction > 0.18f) {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            drawerContent(currentScreenshot)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Floating Action Buttons
            AnimatedVisibility(
                visible = showControls && drawerHeightFraction <= 0.04f && dismissOffsetY.value == 0f,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp)
                        .testTag("viewer_bottom_toolbar"),
                    horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButtonWithLabel(
                        icon = AppIcons.Tune,
                        label = stringResource(R.string.edit),
                        contentDescription = stringResource(R.string.edit),
                        testTag = "btn_detail_floating_edit",
                        onClick = { onEdit(currentScreenshot) }
                    )

                    if (showDetailsButton && drawerContent != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = {
                                    animateDrawerTo(0.62f)
                                    showControls = false
                                },
                                shape = CircleShape,
                                color = Color(0xFF262628).copy(alpha = 0.85f),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("btn_detail_floating_details")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = AppIcons.ExpandLess,
                                        contentDescription = "Details",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Text("Details", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    FloatingActionButtonWithLabel(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.delete),
                        contentDescription = stringResource(R.string.delete),
                        testTag = "btn_detail_floating_delete",
                        onClick = { onDelete(currentScreenshot) }
                    )
                }
            }
        }
    }
}

// Single-item backward compatibility overload
@Composable
fun ZoomableImageViewer(
    screenshot: ScreenshotItem,
    exifData: ExifData = ExifData(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ScreenshotItem) -> Unit = {},
    onEdit: (ScreenshotItem) -> Unit = {},
    onDelete: (ScreenshotItem) -> Unit = {},
    onShare: ((ScreenshotItem) -> Unit)? = null,
    showDetailsButton: Boolean = false,
    drawerContent: (@Composable () -> Unit)? = null,
    onMore: () -> Unit = {},
    overflowMenuContent: (@Composable (ScreenshotItem, onDismissMenu: () -> Unit) -> Unit)? = null
) {
    ZoomableImageViewer(
        screenshots = listOf(screenshot),
        initialIndex = 0,
        exifDataMap = mapOf(screenshot.id to exifData),
        onDismiss = onDismiss,
        onToggleFavorite = onToggleFavorite,
        onEdit = onEdit,
        onDelete = onDelete,
        onShare = onShare,
        showDetailsButton = showDetailsButton,
        drawerContent = if (drawerContent != null) { { _ -> drawerContent() } } else null,
        onMore = { onMore() },
        overflowMenuContent = overflowMenuContent
    )
}

@Composable
private fun FloatingCircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF262628).copy(alpha = 0.85f),
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(46.dp)
            .testTag(testTag)
            .testTag("btn_close_fullscreen_image")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun FloatingActionButtonWithLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color(0xFF262628).copy(alpha = 0.85f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(56.dp)
                .testTag(testTag)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun shareScreenshot(item: ScreenshotItem, context: Context) {
    val uri = item.uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
    val intent = if (uri != null) {
        val type = context.contentResolver.getType(uri) ?: if (item.isVideo) "video/*" else "image/*"
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            putExtra(Intent.EXTRA_TEXT, item.description.ifBlank { item.title })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.description}")
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share media"))
    }
}
