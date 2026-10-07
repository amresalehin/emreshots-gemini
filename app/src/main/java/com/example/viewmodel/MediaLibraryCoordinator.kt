package com.amresalehin.emreshots.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.CollectionRepository
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import com.amresalehin.emreshots.service.exif.ExifMetadataManager
import com.amresalehin.emreshots.service.reminder.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaLibraryCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val screenshotRepository: ScreenshotRepository,
    private val collectionRepository: CollectionRepository,
    private val collections: StateFlow<List<CollectionItem>>,
    private val activeProvider: StateFlow<CustomCloudProvider?>,
    private val exifManager: ExifMetadataManager,
    private val onMessage: (String) -> Unit
) {
    private val _pendingWriteIntentSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingWriteIntentSender: StateFlow<android.content.IntentSender?> = _pendingWriteIntentSender.asStateFlow()
    private val _exifDataState = MutableStateFlow<Map<String, ExifData>>(emptyMap())
    val exifDataState: StateFlow<Map<String, ExifData>> = _exifDataState.asStateFlow()
    private var pendingWriteScreenshot: ScreenshotItem? = null
    private var pendingWriteExifData: ExifData? = null

    fun batchRename(items: List<ScreenshotItem>, template: String) {
        scope.launch(Dispatchers.IO) {
            val cleanTemplate = template.trim()
            if (cleanTemplate.isBlank()) { onMessage = "Enter a rename template."; return@launch }
            var renamed = 0; var failed = 0
            items.forEachIndexed { index, item ->
                val extension = item.filePath.substringAfterLast(".", "").takeIf { it.isNotBlank() } ?: if (item.isVideo) "mp4" else "jpg"
                val collectionName = item.collectionIds.firstNotNullOfOrNull { id -> collections.value.find { it.id == id }?.name }.orEmpty()
                val baseName = cleanTemplate.replace("{index}", (index + 1).toString())
                    .replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.addedOn)))
                    .replace("{time}", SimpleDateFormat("HH-mm-ss", Locale.US).format(Date(item.addedOn)))
                    .replace("{title}", item.title.ifBlank { "Screenshot" }.sanitizeFileName())
                    .replace("{collection}", collectionName.ifBlank { "Unsorted" }.sanitizeFileName())
                    .replace("{type}", if (item.isVideo) "video" else "image").sanitizeFileName().take(180)
                if (baseName.isBlank()) { failed++; return@forEachIndexed }
                val newDisplayName = "$baseName.$extension"
                if (renameMediaItem(item, newDisplayName)) {
                    val newPath = if (item.filePath.isBlank()) item.filePath else File(item.filePath).let { File(it.parentFile, newDisplayName).absolutePath }
                    screenshotRepository.update(item.copy(title = baseName, filePath = newPath)); renamed++
                } else failed++
            }
            onMessage = "Batch rename complete: $renamed renamed${if (failed > 0) ", $failed failed" else ""}."
        }
    }

    private fun String.sanitizeFileName(): String = replace(Regex("""[\\/:*?""<>|]"""), "_").replace(Regex("""\s+"""), " ").trim(' ', '.')

    private fun renameMediaItem(item: ScreenshotItem, newDisplayName: String): Boolean {
        val context = application
        val uri = item.uriString?.let(Uri::parse)
        if (uri != null && uri.toString().startsWith("content://media/")) return runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName) }, null, null) > 0
        }.getOrDefault(false)
        val file = File(item.filePath)
        return file.exists() && runCatching { file.renameTo(File(file.parentFile, newDisplayName)) }.getOrDefault(false)
    }


    fun loadExif(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            val data = exifManager.readExif(application, screenshot)
            val current = _exifDataState.value.toMutableMap()
            current[screenshot.id] = data
            _exifDataState.value = current
        }
    }

    fun clearPendingWriteIntent() {
        _pendingWriteIntentSender.value = null
        pendingWriteScreenshot = null
        pendingWriteExifData = null
    }

    fun onWriteConsentGranted() {
        val shot = pendingWriteScreenshot ?: return
        val data = pendingWriteExifData ?: return
        pendingWriteScreenshot = null
        pendingWriteExifData = null
        _pendingWriteIntentSender.value = null

        scope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(shot.uriString)
                application.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exifInterface = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                    exifManager.applyDataToExifInterface(exifInterface, data)
                    exifInterface.saveAttributes()
                }
                val localFile = File(shot.filePath)
                if (localFile.exists() && localFile.canWrite()) {
                    exifManager.writeExif(localFile, data)
                }
                val updatedScreenshot = shot.copy(
                    title = data.imageDescription?.takeIf { it.isNotBlank() } ?: shot.title,
                    description = data.userComment?.takeIf { it.isNotBlank() } ?: shot.description
                )
                screenshotRepository.update(updatedScreenshot)
                val updatedExif = exifManager.readExif(application, updatedScreenshot)
                val current = _exifDataState.value.toMutableMap()
                current[shot.id] = updatedExif
                _exifDataState.value = current
                onMessage = "Write consent granted: EXIF saved to original media!"
            } catch (e: Exception) {
                // Fallback to safe file write
                saveExif(shot, data)
            }
        }
    }

    fun onWriteConsentDenied() {
        val shot = pendingWriteScreenshot
        val data = pendingWriteExifData
        pendingWriteScreenshot = null
        pendingWriteExifData = null
        _pendingWriteIntentSender.value = null
        if (shot != null && data != null) {
            // Save safe working copy instead
            saveExif(shot, data)
        }
    }

    fun saveExif(screenshot: ScreenshotItem, exifData: ExifData, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            // If it's a MediaStore item, test if direct write needs Scoped Storage user permission
            if (!screenshot.uriString.isNullOrBlank() && screenshot.uriString.startsWith("content://media/")) {
                val uri = Uri.parse(screenshot.uriString)
                try {
                    val pfd = application.contentResolver.openFileDescriptor(uri, "rw")
                    if (pfd != null) {
                        pfd.use {
                            val exifInterface = androidx.exifinterface.media.ExifInterface(it.fileDescriptor)
                            exifManager.applyDataToExifInterface(exifInterface, exifData)
                            exifInterface.saveAttributes()
                        }
                        val localFile = File(screenshot.filePath)
                        if (localFile.exists() && localFile.canWrite()) {
                            exifManager.writeExif(localFile, exifData)
                        }
                        val updatedScreenshot = screenshot.copy(
                            title = exifData.imageDescription?.takeIf { it.isNotBlank() } ?: screenshot.title,
                            description = exifData.userComment?.takeIf { it.isNotBlank() } ?: screenshot.description
                        )
                        screenshotRepository.update(updatedScreenshot)
                        val updatedExif = exifManager.readExif(application, updatedScreenshot)
                        val current = _exifDataState.value.toMutableMap()
                        current[screenshot.id] = updatedExif
                        _exifDataState.value = current
                        onMessage = "EXIF metadata saved directly to device gallery image!"
                        onComplete?.invoke(true)
                        return@launch
                    }
                } catch (e: Exception) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && e is SecurityException) {
                        try {
                            val pi = android.provider.MediaStore.createWriteRequest(application.contentResolver, listOf(uri))
                            pendingWriteScreenshot = screenshot
                            pendingWriteExifData = exifData
                            _pendingWriteIntentSender.value = pi.intentSender
                            return@launch
                        } catch (_: Exception) {}
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                        val pi = e.userAction.actionIntent.intentSender
                        pendingWriteScreenshot = screenshot
                        pendingWriteExifData = exifData
                        _pendingWriteIntentSender.value = pi
                        return@launch
                    }
                }
            }

            val result = exifManager.writeExifSafe(application, screenshot, exifData)
            if (result.isSuccess) {
                val savedFile = result.getOrThrow()
                val updatedScreenshot = screenshot.copy(
                    filePath = savedFile.absolutePath,
                    title = exifData.imageDescription?.takeIf { it.isNotBlank() } ?: screenshot.title,
                    description = exifData.userComment?.takeIf { it.isNotBlank() } ?: screenshot.description
                )
                screenshotRepository.update(updatedScreenshot)

                val updatedExif = exifManager.readExif(application, updatedScreenshot)
                val current = _exifDataState.value.toMutableMap()
                current[screenshot.id] = updatedExif
                _exifDataState.value = current

                onMessage = "EXIF metadata saved directly to image!"
                onComplete?.invoke(true)
            } else {
                onMessage = "Failed to save EXIF: ${result.exceptionOrNull()?.message}"
                onComplete?.invoke(false)
            }
        }
    }

    fun applyAiExif(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            val provider = activeProvider.value
            val modelName = provider?.selectedModel ?: "Shots AI"
            val result = exifManager.applyAiMetadataToExifSafe(
                context = application,
                screenshot = screenshot,
                title = screenshot.title,
                description = screenshot.description,
                tags = screenshot.tags,
                modelName = modelName
            )
            if (result.isSuccess) {
                val (savedFile, updatedExif) = result.getOrThrow()
                val updatedScreenshot = screenshot.copy(filePath = savedFile.absolutePath)
                screenshotRepository.update(updatedScreenshot)

                val current = _exifDataState.value.toMutableMap()
                current[screenshot.id] = updatedExif
                _exifDataState.value = current
                onMessage = "AI metadata written directly into image EXIF headers!"
            } else {
                onMessage = "Failed to write AI to EXIF: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun toggleFavorite(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            val updated = screenshot.copy(isFavorite = !screenshot.isFavorite)
            screenshotRepository.update(updated)
        }
    }

    fun updateScreenshot(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            screenshotRepository.update(screenshot)
        }
    }

    fun deleteScreenshot(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(screenshot.filePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            screenshotRepository.delete(screenshot)
            onMessage = "Screenshot deleted"
        }
    }

    fun setReminder(screenshot: ScreenshotItem, timeMs: Long, text: String) {
        scope.launch(Dispatchers.IO) {
            val updated = screenshot.copy(reminderTime = timeMs, reminderText = text)
            screenshotRepository.update(updated)
            ReminderReceiver.schedule(
                application,
                screenshot.id,
                timeMs,
                application.getString(R.string.reminder_notification_title),
                text
            )
            onMessage = "Reminder scheduled!"
        }
    }

    fun removeReminder(screenshot: ScreenshotItem) {
        scope.launch(Dispatchers.IO) {
            ReminderReceiver.cancel(application, screenshot.id)
            val updated = screenshot.copy(reminderTime = null, reminderText = null)
            screenshotRepository.update(updated)
            onMessage = "Reminder removed"
        }
    }

    fun addTag(screenshot: ScreenshotItem, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !screenshot.tags.contains(trimmed)) {
            val updated = screenshot.copy(tags = screenshot.tags + trimmed)
            updateScreenshot(updated)
        }
    }

    fun removeTag(screenshot: ScreenshotItem, tag: String) {
        val updated = screenshot.copy(tags = screenshot.tags.filter { it != tag })
        updateScreenshot(updated)
    }

    fun addScreenshotToCollection(screenshotId: String, collectionId: String) {
        scope.launch(Dispatchers.IO) {
            val screenshot = screenshotRepository.getScreenshotSync(screenshotId) ?: return@launch
            if (!screenshot.collectionIds.contains(collectionId)) {
                screenshotRepository.update(screenshot.copy(collectionIds = screenshot.collectionIds + collectionId))
            }
        }
    }

    fun removeScreenshotFromCollection(screenshotId: String, collectionId: String) {
        scope.launch(Dispatchers.IO) {
            val screenshot = screenshotRepository.getScreenshotSync(screenshotId) ?: return@launch
            screenshotRepository.update(screenshot.copy(collectionIds = screenshot.collectionIds - collectionId))
        }
    }

    fun createCollection(name: String, description: String, iconName: String, colorHex: String) {
        scope.launch(Dispatchers.IO) {
            val newCol = CollectionItem(
                name = name.trim(),
                description = description.trim(),
                iconName = iconName,
                colorHex = colorHex
            )
            collectionRepository.insert(newCol)
            onMessage = "Collection '$name' created!"
        }
    }

    fun deleteCollection(collectionId: String) {
        scope.launch(Dispatchers.IO) {
            collectionRepository.deleteById(collectionId)
            // Remove collection ID from all screenshots
            val all = allScreenshots.value
            all.filter { it.collectionIds.contains(collectionId) }.forEach { item ->
                screenshotRepository.update(item.copy(collectionIds = item.collectionIds - collectionId))
            }
            onMessage = "Collection removed"
        }
    }

    fun importImageFromUri(uri: Uri) {
        importMediaFromUri(uri)
    }

    fun importMediaFromUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val context = application
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isVideo = mimeType.startsWith("video") || uri.toString().lowercase().endsWith(".mp4")

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val dir = File(context.filesDir, "imported_media").apply { mkdirs() }
                val extension = if (isVideo) "mp4" else "jpg"
                val prefix = if (isVideo) "vid" else "img"
                val targetFile = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")

                inputStream.use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                }

                var width = 0
                var height = 0
                var duration = 0L

                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(targetFile.absolutePath)
                        width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(targetFile.absolutePath, options)
                    width = options.outWidth
                    height = options.outHeight
                }

                val now = System.currentTimeMillis()
                val newItem = ScreenshotItem(
                    id = UUID.randomUUID().toString(),
                    filePath = targetFile.absolutePath,
                    uriString = uri.toString(),
                    mediaType = if (isVideo) "VIDEO" else "PHOTO",
                    durationMs = duration,
                    title = targetFile.nameWithoutExtension.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    description = if (isVideo) "Imported video ($duration ms)" else "Imported photo awaiting AI indexing.",
                    tags = if (isVideo) listOf("Imported", "Video") else listOf("Imported", "Photo"),
                    addedOn = now,
                    fileSize = targetFile.length(),
                    width = width,
                    height = height
                )
                screenshotRepository.insert(newItem)
                if (!isVideo) {
                    loadExif(newItem)
                }
                onMessage = "Imported 1 ${if (isVideo) "video" else "photo"} successfully!"
            } catch (e: Exception) {
                onMessage = "Failed to import media: ${e.message}"
            }
        }
    }



}
