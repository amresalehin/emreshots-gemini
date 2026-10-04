package com.example.service.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailHelper {

    // Cache up to 30MB of video thumbnail bitmaps in memory
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(10240, 40960)
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    suspend fun getVideoThumbnail(context: Context, screenshot: ScreenshotItem): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = screenshot.id
        memoryCache.get(cacheKey)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. If content URI on Android 10+ (API 29+), try ContentResolver.loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !screenshot.uriString.isNullOrBlank() && screenshot.uriString.startsWith("content://")) {
            try {
                val uri = Uri.parse(screenshot.uriString)
                bitmap = context.contentResolver.loadThumbnail(uri, Size(480, 480), null)
            } catch (_: Exception) {}
        }

        // 2. Fallback or primary: MediaMetadataRetriever
        if (bitmap == null) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                val file = File(screenshot.filePath)
                if (file.exists() && file.length() > 0) {
                    retriever.setDataSource(file.absolutePath)
                } else if (!screenshot.uriString.isNullOrBlank()) {
                    retriever.setDataSource(context, Uri.parse(screenshot.uriString))
                } else if (screenshot.filePath.isNotBlank()) {
                    retriever.setDataSource(screenshot.filePath)
                }

                // Prefer frame at 1 second (1,000,000 micros) to avoid initial black frames,
                // or halfway through short clips
                val targetMicros = if (screenshot.durationMs > 1000L) {
                    1_000_000L
                } else if (screenshot.durationMs > 200L) {
                    (screenshot.durationMs * 500L)
                } else {
                    0L
                }

                bitmap = retriever.getFrameAtTime(targetMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    fun getCachedThumbnail(id: String): Bitmap? = memoryCache.get(id)

    fun clearCache() {
        memoryCache.evictAll()
    }
}
