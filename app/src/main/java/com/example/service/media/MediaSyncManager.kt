package com.example.service.media

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val total: Int
)

class MediaSyncManager(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val scanner = DeviceMediaScanner(context)
    private val dao = database.screenshotDao()

    suspend fun synchronize(): MediaSyncResult = withContext(Dispatchers.IO) {
        if (!DeviceMediaScanner.hasAnyMediaPermission(context)) {
            return@withContext MediaSyncResult(0, 0, 0, dao.getAllScreenshotsSync().size)
        }

        val scanned = scanner.scanDeviceMedia(Int.MAX_VALUE)
        val existing = dao.getAllScreenshotsSync()
        val existingById = existing.associateBy { it.id }
        val scannedById = scanned.associateBy { it.id }

        var added = 0
        var updated = 0
        scanned.forEach { media ->
            val old = existingById[media.id]
            if (old == null) {
                dao.insert(media)
                added++
            } else {
                val merged = old.copy(
                    filePath = media.filePath,
                    uriString = media.uriString,
                    mediaType = media.mediaType,
                    durationMs = media.durationMs,
                    title = if (old.aiProcessed) old.title else media.title,
                    addedOn = media.addedOn,
                    fileSize = media.fileSize,
                    width = media.width,
                    height = media.height,
                    tags = (old.tags + media.tags).distinct()
                )
                if (merged != old) {
                    dao.update(merged)
                    updated++
                }
            }
        }

        var removed = 0
        existing.filter { it.id.startsWith("device-img-") || it.id.startsWith("device-vid-") }
            .filter { item ->
                val typeReconciled = when {
                    item.id.startsWith("device-img-") -> scanner.canReconcileImages()
                    item.id.startsWith("device-vid-") -> scanner.canReconcileVideos()
                    else -> false
                }
                typeReconciled && !scannedById.containsKey(item.id)
            }
            .forEach {
                dao.deleteById(it.id)
                removed++
            }

        MediaSyncResult(added, updated, removed, scanned.size)
    }
}
