package com.example.service.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceMediaScanner(private val context: Context) {

    companion object {
        fun getRequiredPermissions(): Array<String> {
            val list = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
                list.add(Manifest.permission.READ_MEDIA_VIDEO)
                list.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                list.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            return list.toTypedArray()
        }

        fun hasPermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val hasVideos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                hasImages || hasVideos
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    suspend fun scanDeviceMedia(limit: Int = 50): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<ScreenshotItem>()
        if (!hasPermissions(context)) {
            return@withContext mediaList
        }

        // 1. Scan Photos & Screenshots
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Photo"
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val isScreenshot = name.lowercase().contains("screenshot") ||
                            filePath.lowercase().contains("screenshot")

                    val tags = mutableListOf<String>()
                    if (isScreenshot) tags.add("Screenshot") else tags.add("Photo")

                    mediaList.add(
                        ScreenshotItem(
                            id = "device-img-$id",
                            filePath = filePath,
                            uriString = contentUri.toString(),
                            mediaType = "PHOTO",
                            title = name.substringBeforeLast('.').replace('_', ' '),
                            description = if (isScreenshot) "Device screenshot captured on ${java.util.Date(dateAdded)}" else "Device photo stored in media gallery",
                            tags = tags,
                            addedOn = dateAdded,
                            fileSize = size,
                            width = width,
                            height = height
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {}

        // 2. Scan Videos
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA
            )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video"
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val duration = cursor.getLong(durationCol)
                    val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    mediaList.add(
                        ScreenshotItem(
                            id = "device-vid-$id",
                            filePath = filePath,
                            uriString = contentUri.toString(),
                            mediaType = "VIDEO",
                            durationMs = duration,
                            title = name.substringBeforeLast('.').replace('_', ' '),
                            description = "Video recording (${formatDuration(duration)})",
                            tags = listOf("Video", "Media"),
                            addedOn = dateAdded,
                            fileSize = size,
                            width = width,
                            height = height
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {}

        mediaList.sortedByDescending { it.addedOn }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }
}
