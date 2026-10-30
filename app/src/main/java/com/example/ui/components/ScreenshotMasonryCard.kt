package com.amresalehin.emreshots.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.media.VideoThumbnailHelper
import com.amresalehin.emreshots.ui.theme.EmrehShapes
import com.amresalehin.emreshots.ui.theme.EmrehSpacing
import com.amresalehin.emreshots.ui.theme.tactileBorder
import java.io.File
import kotlin.math.abs

@Composable
fun ScreenshotMasonryCard(
    screenshot: ScreenshotItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    showFileName: Boolean = true,
    showTags: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dynamic, organic masonry aspect ratio
    val dynamicAspectRatio = remember(screenshot.id, screenshot.width, screenshot.height) {
        if (screenshot.width > 0 && screenshot.height > 0) {
            (screenshot.width.toFloat() / screenshot.height.toFloat()).coerceIn(0.45f, 2.2f)
        } else {
            val variants = floatArrayOf(0.75f, 0.9f, 1.15f, 1.3f, 1.0f)
            variants[abs(screenshot.id.hashCode()) % variants.size]
        }
    }

    var videoThumbnailBitmap by remember(screenshot.id) {
        mutableStateOf<Bitmap?>(VideoThumbnailHelper.getCachedThumbnail(screenshot.id))
    }

    LaunchedEffect(screenshot.id, screenshot.isVideo) {
        if (screenshot.isVideo && videoThumbnailBitmap == null) {
            val bmp = VideoThumbnailHelper.getVideoThumbnail(context, screenshot)
            if (bmp != null) {
                videoThumbnailBitmap = bmp
            }
        }
    }

    val imageModel: Any = remember(screenshot.filePath, screenshot.uriString) {
        val file = File(screenshot.filePath)
        if (file.exists() && file.length() > 0) {
            file
        } else if (!screenshot.uriString.isNullOrBlank()) {
            Uri.parse(screenshot.uriString)
        } else {
            screenshot.filePath
        }
    }

    val imageRequest = remember(imageModel, screenshot.isVideo) {
        val builder = ImageRequest.Builder(context)
            .data(imageModel)
            .crossfade(true)
            .size(480, 480)

        if (screenshot.isVideo) {
            builder.decoderFactory(VideoFrameDecoder.Factory())
            val frameMs = if (screenshot.durationMs > 1000L) 1000L else if (screenshot.durationMs > 200L) screenshot.durationMs / 2 else 0L
            builder.videoFrameMillis(frameMs)
        }
        builder.build()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(EmrehShapes.Card)
            .clickable(onClick = onClick)
            .testTag("screenshot_masonry_card_${screenshot.id}"),
        shape = EmrehShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = tactileBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(dynamicAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            // Media
            if (screenshot.isVideo && videoThumbnailBitmap != null) {
                Image(
                    bitmap = videoThumbnailBitmap!!.asImageBitmap(),
                    contentDescription = screenshot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = screenshot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(EmrehSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (screenshot.aiProcessed) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = EmrehShapes.ExtraSmall,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Analyzed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "AI",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (screenshot.reminderTime != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Reminder",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            // Top Right: Tactile Favorite
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(36.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (screenshot.isFavorite) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Bottom Badges
            if (screenshot.isVideo) {
                val secs = (screenshot.durationMs / 1000) % 60
                val mins = (screenshot.durationMs / 1000) / 60
                val durStr = if (screenshot.durationMs > 0) String.format("%d:%02d", mins, secs) else "Video"

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = EmrehShapes.ExtraSmall,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(EmrehSpacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = durStr,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (screenshot.links.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = EmrehShapes.ExtraSmall,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(EmrehSpacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Links",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${screenshot.links.size}",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
