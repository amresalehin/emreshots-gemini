package com.amresalehin.emreshots.ui.components

import com.amresalehin.emreshots.ui.theme.DangerRose
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenshotListItem(
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
            .size(160, 160) // Small bounded size for list thumbnails

        if (screenshot.isVideo) {
            builder.decoderFactory(VideoFrameDecoder.Factory())
            val frameMs = if (screenshot.durationMs > 1000L) 1000L else if (screenshot.durationMs > 200L) screenshot.durationMs / 2 else 0L
            builder.videoFrameMillis(frameMs)
        }
        builder.build()
    }
    val dateStr = com.amresalehin.emreshots.ui.util.DateUtils.formatShortDate(screenshot.addedOn)
    val sizeStr = if (screenshot.fileSize > 0) "${screenshot.fileSize / 1024} KB" else ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("list_item_${screenshot.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
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

                if (screenshot.isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                if (showFileName) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = screenshot.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (screenshot.aiProcessed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = listOfNotNull(
                        dateStr,
                        sizeStr.takeIf { it.isNotBlank() },
                        if (screenshot.isVideo) "Video" else "Photo"
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showTags && screenshot.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = screenshot.tags.take(3).joinToString("  ") { "#$it" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Favorite toggle
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("list_fav_${screenshot.id}")
            ) {
                Icon(
                    imageVector = if (screenshot.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (screenshot.isFavorite) DangerRose else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
