package com.amresalehin.emreshots.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amresalehin.emreshots.data.model.ScreenshotItem
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun InAppVideoPlayer(
    screenshot: ScreenshotItem,
    modifier: Modifier = Modifier,
    onExternalPlayerRequested: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
    onSwipeNext: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    val videoUri = remember(screenshot.uriString, screenshot.filePath) {
        if (!screenshot.uriString.isNullOrBlank()) {
            Uri.parse(screenshot.uriString)
        } else {
            val file = File(screenshot.filePath)
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    if (videoUri == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Video source not accessible", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        return
    }

    Box(modifier = modifier) {
        VideoPlayerSurface(
            videoUri = videoUri,
            initialDurationMs = screenshot.durationMs,
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            isFullscreen = false,
            onExternalPlayer = onExternalPlayerRequested,
            onSwipePrevious = onSwipePrevious,
            onSwipeNext = onSwipeNext
        )
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerSurface(
                    videoUri = videoUri,
                    initialDurationMs = screenshot.durationMs,
                    onToggleFullscreen = { isFullscreen = false },
                    isFullscreen = true,
                    onExternalPlayer = onExternalPlayerRequested,
                    onSwipePrevious = onSwipePrevious,
                    onSwipeNext = onSwipeNext
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerSurface(
    videoUri: Uri,
    initialDurationMs: Long,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
    onExternalPlayer: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
    onSwipeNext: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(initialDurationMs.toInt()) }
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var playerWidthPx by remember { mutableIntStateOf(1) }

    // Auto-update playback progress
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying && !isUserScrubbing) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    if (vv.duration > 0 && vv.duration != durationMs) {
                        durationMs = vv.duration
                    }
                }
            }
            delay(250)
        }
    }

    // Keep the player clean: controls are transient whether the video is playing or paused.
    // A tap on the video always brings them back for another interaction.
    LaunchedEffect(showControls, isPlaying, isPrepared) {
        if (showControls && isPrepared) {
            delay(if (isPlaying) 3000 else 2200)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { playerWidthPx = it.width.coerceAtLeast(1) }
            .pointerInput(videoUri) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { position ->
                        videoViewRef?.let { vv ->
                            val target = if (position.x < playerWidthPx / 2f) {
                                (vv.currentPosition - 10_000).coerceAtLeast(0)
                            } else {
                                val maxPosition = if (durationMs > 0) durationMs else vv.duration
                                (vv.currentPosition + 10_000).coerceAtMost(maxPosition)
                            }
                            vv.seekTo(target)
                            currentPositionMs = target
                            isCompleted = false
                            showControls = true
                        }
                    }
                )
            }
            .pointerInput(videoUri) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalDrag) >= 140f) {
                            if (totalDrag < 0) onSwipeNext?.invoke() else onSwipePrevious?.invoke()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Video View host
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setVideoURI(videoUri)

                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        isPrepared = true
                        if (mp.duration > 0) {
                            durationMs = mp.duration
                        }
                        mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                            } catch (_: Exception) {}
                        }
                    }

                    setOnCompletionListener {
                        isPlaying = false
                        isCompleted = true
                        showControls = true
                    }

                    setOnErrorListener { _, _, _ ->
                        isPrepared = false
                        true
                    }
                }.also { videoViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading spinner before video prepares
        if (!isPrepared) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top bar: External player + Fullscreen / Exit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback speed selector pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.clickable {
                            val newSpeed = when (playbackSpeed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            playbackSpeed = newSpeed
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    mediaPlayerRef?.playbackParams = mediaPlayerRef?.playbackParams?.setSpeed(newSpeed) ?: return@clickable
                                } catch (_: Exception) {}
                            }
                        }
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Mute / Unmute
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                val vol = if (isMuted) 0f else 1f
                                mediaPlayerRef?.setVolume(vol, vol)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Open in external system player
                        if (onExternalPlayer != null) {
                            IconButton(
                                onClick = onExternalPlayer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in external player",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Fullscreen toggle
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Center Main Play / Pause Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Play/Pause Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .clickable {
                                videoViewRef?.let { vv ->
                                    if (isCompleted) {
                                        vv.seekTo(0)
                                        vv.start()
                                        isPlaying = true
                                        isCompleted = false
                                    } else if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            }
                            .testTag("btn_player_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Default.Replay
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Timeline Slider & Timers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val progress = if (durationMs > 0) {
                        if (isUserScrubbing) scrubProgress else currentPositionMs.toFloat() / durationMs.toFloat()
                    } else 0f

                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { newProgress ->
                            isUserScrubbing = true
                            scrubProgress = newProgress
                        },
                        onValueChangeFinished = {
                            val seekTarget = (scrubProgress * durationMs).toInt()
                            videoViewRef?.seekTo(seekTarget)
                            currentPositionMs = seekTarget
                            isUserScrubbing = false
                            if (isCompleted) isCompleted = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("slider_video_progress")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayCurrent = if (isUserScrubbing) (scrubProgress * durationMs).toInt() else currentPositionMs
                        Text(
                            text = formatTime(displayCurrent),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 3600).let { hours ->
        if (hours > 0) {
            val mins = (totalSeconds % 3600) / 60
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, seconds)
        } else {
            (totalSeconds / 60)
        }
    }
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
