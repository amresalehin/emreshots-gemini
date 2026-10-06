package com.amresalehin.emreshots.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amresalehin.emreshots.data.model.ScreenshotItem
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun InAppVideoPlayer(
    screenshot: ScreenshotItem,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val videoUri = remember(screenshot.uriString, screenshot.filePath) {
        if (!screenshot.uriString.isNullOrBlank()) {
            Uri.parse(screenshot.uriString)
        } else {
            File(screenshot.filePath).takeIf { it.exists() }?.let(Uri::fromFile)
        }
    }

    if (videoUri == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Video source not accessible", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        return
    }

    VideoPlayerSurface(
        videoUri = videoUri,
        initialDurationMs = screenshot.durationMs,
        modifier = modifier,
        onTap = onTap
    )
}

@Composable
private fun VideoPlayerSurface(
    videoUri: Uri,
    initialDurationMs: Long,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(initialDurationMs.toInt()) }
    var showControls by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var videoAspectRatio by remember { mutableFloatStateOf(0f) }

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

    LaunchedEffect(showControls, isPlaying, isPrepared) {
        if (showControls && isPrepared) {
            delay(if (isPlaying) 2800 else 2200)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { videoViewRef?.stopPlayback() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(videoUri) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        onTap?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
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
                        if (mp.duration > 0) durationMs = mp.duration
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            videoAspectRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                        }
                        mp.setVolume(1f, 1f)
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (videoAspectRatio > 0f && size.height > 0f) {
                        val viewRatio = size.width / size.height
                        if (videoAspectRatio > viewRatio) {
                            scaleY = videoAspectRatio / viewRatio
                        } else if (videoAspectRatio < viewRatio) {
                            scaleX = viewRatio / videoAspectRatio
                        }
                    }
                }
        )

        if (!isPrepared) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
            ) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        val volume = if (isMuted) 0f else 1f
                        mediaPlayerRef?.setVolume(volume, volume)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.52f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition - 10_000).coerceAtLeast(0)
                                vv.seekTo(target)
                                currentPositionMs = target
                                isCompleted = false
                            }
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(64.dp)
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

                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10_000).coerceAtMost(durationMs)
                                vv.seekTo(target)
                                currentPositionMs = target
                                isCompleted = false
                            }
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val progress = if (durationMs > 0) {
                        if (isUserScrubbing) scrubProgress else currentPositionMs.toFloat() / durationMs.toFloat()
                    } else 0f

                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = {
                            isUserScrubbing = true
                            scrubProgress = it
                        },
                        onValueChangeFinished = {
                            val seekTarget = (scrubProgress * durationMs).toInt()
                            videoViewRef?.seekTo(seekTarget)
                            currentPositionMs = seekTarget
                            isUserScrubbing = false
                            isCompleted = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayCurrent = if (isUserScrubbing) (scrubProgress * durationMs).toInt() else currentPositionMs
                        Text(
                            text = formatTime(displayCurrent),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.88f),
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
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        val minutes = (totalSeconds % 3600) / 60
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        val minutes = totalSeconds / 60
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
