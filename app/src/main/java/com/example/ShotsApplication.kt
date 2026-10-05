package com.amresalehin.emreshots

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.amresalehin.emreshots.service.media.VideoThumbnailHelper

class ShotsApplication : Application(), ImageLoaderFactory {


    val isLowRamDevice: Boolean by lazy {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice ?: false
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Strict memory bounding: 15% of app heap on low-RAM, 25% on normal
                    .maxSizePercent(if (isLowRamDevice) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("shots_img_cache"))
                    // Bound disk cache to prevent filling up low-end storage
                    .maxSizeBytes(if (isLowRamDevice) 35L * 1024 * 1024 else 100L * 1024 * 1024)
                    .build()
            }
            // RGB_565 halves memory consumption (2 bytes/pixel vs 4 bytes/pixel in ARGB_8888)
            .bitmapConfig(if (isLowRamDevice) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .allowRgb565(true)
            .crossfade(!isLowRamDevice) // Disabling crossfade prevents GPU frame drops on low-end chipsets
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Actively evict image cache to prevent OS killing the process
            val imageLoader = coil.Coil.imageLoader(this)
            imageLoader.memoryCache?.clear()
            VideoThumbnailHelper.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        coil.Coil.imageLoader(this).memoryCache?.clear()
        VideoThumbnailHelper.clearCache()
    }
}
