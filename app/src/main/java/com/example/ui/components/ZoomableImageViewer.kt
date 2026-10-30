package com.amresalehin.emreshots.ui.components

import com.example.ui.icons.AppIcons
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.ui.theme.DangerRose

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
            // Soft-focus backdrop
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (imageFile.exists()) imageFile else currentScreenshot.uriString)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f * scrimAlpha)))

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
                            detectTransformGestures { _, pan, zoom, _ ->
                                coroutineScope.launch {
                                    val newScale = (scaleAnim.value * zoom).coerceIn(0.70f, 6.0f)
                                    scaleAnim.snapTo(newScale)

                                    if (newScale > 1f) {
                                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                        offsetXAnim.snapTo((offsetXAnim.value + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX))
                                        offsetYAnim.snapTo((offsetYAnim.value + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY))
                                    } else {
                                        offsetXAnim.snapTo(0f)
                                        offsetYAnim.snapTo(0f)
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
                            contentScale = ContentScale.Fit,
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

            // Top HUD Bar
            AnimatedVisibility(
                visible = showControls && dismissOffsetY.value == 0f,
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
                                    .testTag("btn_detail_back")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = currentScreenshot.title.ifBlank {
                                        if (currentScreenshot.isVideo) stringResource(R.string.video) else stringResource(R.string.photo)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (screenshots.size > 1) {
                                        "${pagerState.currentPage + 1} / ${screenshots.size} · " +
                                            if (currentScreenshot.width > 0 && currentScreenshot.height > 0)
                                                "${currentScreenshot.width} × ${currentScreenshot.height} px"
                                            else "High Resolution"
                                    } else {
                                        if (currentScreenshot.width > 0 && currentScreenshot.height > 0)
                                            "${currentScreenshot.width} × ${currentScreenshot.height} px"
                                        else "High Resolution"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (scaleAnim.value > 1.1f) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { resetZoomAnimated() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text(
                                            text = "${(scaleAnim.value * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onToggleFavorite(currentScreenshot) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("btn_detail_favorite")
                            ) {
                                Icon(
                                    imageVector = if (currentScreenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (currentScreenshot.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                                    tint = if (currentScreenshot.isFavorite) DangerRose else Color.White
                                )
                            }

                            var showOverflowMenu by remember { mutableStateOf(false) }

                            Box {
                                IconButton(
                                    onClick = {
                                        if (overflowMenuContent != null) {
                                            showOverflowMenu = true
                                        } else {
                                            onMore(currentScreenshot)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("btn_detail_overflow")
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_actions),
                                        tint = Color.White
                                    )
                                }

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
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .pointerInput(maxHeightPx) {
                                    var totalVerticalDrag = 0f
                                    var startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                                    var dragVelocity = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            totalVerticalDrag = 0f
                                            startedWithDrawerClosed = drawerHeightFraction <= 0.04f
                                            dragVelocity = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (drawerContent != null && (drawerHeightFraction > 0.04f || dragAmount < 0f)) {
                                                change.consume()
                                                totalVerticalDrag += dragAmount
                                                dragVelocity = dragAmount
                                                coroutineScope.launch {
                                                    drawerAnim.snapTo(
                                                        (drawerAnim.value - dragAmount / maxHeightPx).coerceIn(0f, 0.86f)
                                                    )
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (startedWithDrawerClosed && totalVerticalDrag < -48.dp.toPx()) {
                                                animateDrawerTo(0.62f)
                                            } else if (drawerHeightFraction > 0.04f) {
                                                settleDrawer(dragVelocity * 10f)
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
                visible = showControls && drawerHeightFraction <= 0.04f && dismissOffsetY.value == 0f,
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
                                IconButton(onClick = { onToggleFavorite(currentScreenshot) }) {
                                    Icon(
                                        imageVector = if (currentScreenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (currentScreenshot.isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = if (currentScreenshot.isFavorite) DangerRose else Color.White
                                    )
                                }
                                Text("Favorite", color = Color.White, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, currentScreenshot.title)
                                            putExtra(Intent.EXTRA_TEXT, "${currentScreenshot.title}\n${currentScreenshot.description}")
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
                                        animateDrawerTo(0.62f)
                                        showControls = false
                                    }) {
                                        Icon(AppIcons.ExpandLess, contentDescription = "Show details", tint = Color.White)
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
                            if (currentScreenshot.aiProcessed) {
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
                                            text = currentScreenshot.aiModelUsed ?: "AI Indexed",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (currentScreenshot.fileSize > 0) {
                                val mb = currentScreenshot.fileSize.toDouble() / (1024 * 1024)
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
                                text = "Swipe down to exit · Swipe to browse",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        if (currentScreenshot.description.isNotBlank()) {
                            Text(
                                text = currentScreenshot.description,
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

// Single-item backward compatibility overload
@Composable
fun ZoomableImageViewer(
    screenshot: ScreenshotItem,
    exifData: ExifData = ExifData(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ScreenshotItem) -> Unit = {},
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
        drawerContent = if (drawerContent != null) { { _ -> drawerContent() } } else null,
        onMore = { onMore() },
        overflowMenuContent = overflowMenuContent
    )
}
