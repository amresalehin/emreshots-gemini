package com.example.service.perf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.File

object PerformanceManager {

    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice ?: false
    }

    fun isLowEndDevice(context: Context): Boolean {
        if (isLowRamDevice(context)) return true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(memoryInfo)
            // If device total RAM is 3GB or less
            if (memoryInfo.totalMem <= 3L * 1024 * 1024 * 1024) {
                return true
            }
        }
        // If device has 4 or fewer CPU cores
        if (Runtime.getRuntime().availableProcessors() <= 4) {
            return true
        }
        return false
    }

    fun getRecommendedMaxImageDimension(context: Context): Int {
        return if (isLowEndDevice(context)) 720 else 1024
    }

    /**
     * Safely decodes a bitmap from file using sub-sampling (inSampleSize)
     * avoiding OutOfMemoryError on low-RAM devices with high-res photos.
     */
    fun decodeSampledBitmapFromFile(
        file: File,
        targetWidth: Int = 800,
        targetHeight: Int = 800,
        preferRgb565: Boolean = true
    ): Bitmap? {
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sampleSize = 1
        while ((options.outWidth / (sampleSize * 2)) >= targetWidth &&
            (options.outHeight / (sampleSize * 2)) >= targetHeight
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            if (preferRgb565) {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: OutOfMemoryError) {
            // Emergency fallback with higher sample size
            decodeOptions.inSampleSize = sampleSize * 2
            try {
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
