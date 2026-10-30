package com.amresalehin.emreshots.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenshotFeedCard(
    screenshot: ScreenshotItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    showFileName: Boolean = true,
    showTags: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            .size(720, 450)

        if (screenshot.isVideo) {
            builder.decoderFactory(VideoFrameDecoder.Factory())
            val frameMs = if (screenshot.durationMs > 1000L) 1000L else if (screenshot.durationMs > 200L) screenshot.durationMs / 2 else 0L
            builder.videoFrameMillis(frameMs)
        }
        builder.build()
    }
    val dateStr = com.amresalehin.emreshots.ui.util.DateUtils.formatFullDate(screenshot.addedOn)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(EmrehShapes.Card)
            .clickable(onClick = onClick)
            .testTag("feed_card_${screenshot.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = tactileBorder(),
        shape = EmrehShapes.Card,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Main media canvas (Preserves content aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
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

                // Video Duration Pill
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = durStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Favorite Toggle Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(EmrehSpacing.xs)
                        .size(40.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (screenshot.isFavorite) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Editorial Metadata Content Area
            Column(modifier = Modifier.padding(EmrehSpacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (screenshot.title.isNotBlank()) screenshot.title else "Untitled Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (screenshot.aiProcessed) {
                        Spacer(modifier = Modifier.width(EmrehSpacing.sm))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = EmrehShapes.ExtraSmall,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "AI",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EmrehSpacing.xs))

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (screenshot.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(EmrehSpacing.xs))
                    Text(
                        text = screenshot.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (showTags && screenshot.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(EmrehSpacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        screenshot.tags.take(4).forEach { tag ->
                            Surface(
                                shape = EmrehShapes.ExtraSmall,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = tactileBorder(width = 0.5.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
