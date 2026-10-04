package com.example.service.exif

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.ExifData
import com.example.data.model.ScreenshotItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExifMetadataManager {

    /**
     * Reads EXIF metadata from either a content URI or a direct file path,
     * handling Scoped Storage and unredacted media location permissions.
     */
    fun readExif(context: Context, screenshot: ScreenshotItem): ExifData {
        // 1. Try reading via content URI (supports MediaStore and Photo Picker)
        if (!screenshot.uriString.isNullOrBlank()) {
            try {
                val baseUri = Uri.parse(screenshot.uriString)
                val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    screenshot.uriString.startsWith("content://media/external/images")
                ) {
                    try {
                        MediaStore.setRequireOriginal(baseUri)
                    } catch (_: Exception) {
                        baseUri
                    }
                } else {
                    baseUri
                }

                context.contentResolver.openInputStream(targetUri)?.use { inputStream ->
                    return extractFromExifInterface(ExifInterface(inputStream))
                }
            } catch (_: Exception) {
                // Fallback to direct file path
            }
        }

        // 2. Fallback to direct file path
        val file = File(screenshot.filePath)
        return readExif(file)
    }

    /**
     * Reads EXIF metadata from a local File.
     */
    fun readExif(file: File): ExifData {
        if (!file.exists() || !file.canRead()) {
            return ExifData()
        }
        return try {
            val exifInterface = ExifInterface(file.absolutePath)
            extractFromExifInterface(exifInterface)
        } catch (_: Exception) {
            ExifData()
        }
    }

    private fun extractFromExifInterface(exifInterface: ExifInterface): ExifData {
        val latLong = FloatArray(2)
        val hasLatLong = exifInterface.getLatLong(latLong)

        return ExifData(
            dateTaken = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
            cameraMake = exifInterface.getAttribute(ExifInterface.TAG_MAKE),
            cameraModel = exifInterface.getAttribute(ExifInterface.TAG_MODEL),
            imageWidth = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 },
            imageLength = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 },
            iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            fNumber = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER),
            exposureTime = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
            orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            software = exifInterface.getAttribute(ExifInterface.TAG_SOFTWARE),
            userComment = exifInterface.getAttribute(ExifInterface.TAG_USER_COMMENT),
            imageDescription = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
            artist = exifInterface.getAttribute(ExifInterface.TAG_ARTIST),
            latitude = if (hasLatLong) latLong[0].toDouble() else null,
            longitude = if (hasLatLong) latLong[1].toDouble() else null
        )
    }

    /**
     * Writes EXIF metadata to an image.
     * If the target file is in external storage or read-only (Scoped Storage),
     * this automatically creates a writable local copy in app-specific storage,
     * writes the EXIF tags, and returns the updated File.
     */
    fun writeExifSafe(
        context: Context,
        screenshot: ScreenshotItem,
        data: ExifData
    ): Result<File> {
        val targetFile = resolveWritableFile(context, screenshot)
            ?: return Result.failure(IllegalStateException("Could not resolve a writable image file for metadata editing."))

        return try {
            val exifInterface = ExifInterface(targetFile.absolutePath)

            data.dateTaken?.let { exifInterface.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it) }
            data.cameraMake?.let { exifInterface.setAttribute(ExifInterface.TAG_MAKE, it) }
            data.cameraModel?.let { exifInterface.setAttribute(ExifInterface.TAG_MODEL, it) }
            data.software?.let { exifInterface.setAttribute(ExifInterface.TAG_SOFTWARE, it) }
            data.artist?.let { exifInterface.setAttribute(ExifInterface.TAG_ARTIST, it) }
            data.imageDescription?.let { exifInterface.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
            data.userComment?.let { exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, it) }

            if (data.latitude != null && data.longitude != null) {
                exifInterface.setLatLong(data.latitude, data.longitude)
            }

            exifInterface.saveAttributes()
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Direct file write for internal or already-writable files.
     */
    fun writeExif(file: File, data: ExifData): Result<Unit> {
        if (!file.exists() || !file.canWrite()) {
            return Result.failure(IllegalStateException("File does not exist or cannot be written: ${file.absolutePath}"))
        }
        return try {
            val exifInterface = ExifInterface(file.absolutePath)

            data.dateTaken?.let { exifInterface.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it) }
            data.cameraMake?.let { exifInterface.setAttribute(ExifInterface.TAG_MAKE, it) }
            data.cameraModel?.let { exifInterface.setAttribute(ExifInterface.TAG_MODEL, it) }
            data.software?.let { exifInterface.setAttribute(ExifInterface.TAG_SOFTWARE, it) }
            data.artist?.let { exifInterface.setAttribute(ExifInterface.TAG_ARTIST, it) }
            data.imageDescription?.let { exifInterface.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
            data.userComment?.let { exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, it) }

            if (data.latitude != null && data.longitude != null) {
                exifInterface.setLatLong(data.latitude, data.longitude)
            }

            exifInterface.saveAttributes()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun applyAiMetadataToExifSafe(
        context: Context,
        screenshot: ScreenshotItem,
        title: String,
        description: String,
        tags: List<String>,
        modelName: String
    ): Result<Pair<File, ExifData>> {
        val current = readExif(context, screenshot)
        val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val currentDate = dateFormat.format(Date())

        val tagKeywords = tags.joinToString(", ")
        val formattedComment = "Shots Studio AI [$modelName] | Tags: $tagKeywords | $description"

        val updated = current.copy(
            imageDescription = if (title.isNotBlank()) title else current.imageDescription,
            userComment = formattedComment,
            software = "Shots Studio AI ($modelName)",
            dateTaken = current.dateTaken ?: currentDate
        )

        val writeResult = writeExifSafe(context, screenshot, updated)
        return if (writeResult.isSuccess) {
            val savedFile = writeResult.getOrThrow()
            Result.success(Pair(savedFile, readExif(savedFile)))
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: RuntimeException("Failed to save EXIF attributes"))
        }
    }

    fun applyAiMetadataToExif(
        file: File,
        title: String,
        description: String,
        tags: List<String>,
        modelName: String
    ): Result<ExifData> {
        val current = readExif(file)
        val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val currentDate = dateFormat.format(Date())

        val tagKeywords = tags.joinToString(", ")
        val formattedComment = "Shots Studio AI [$modelName] | Tags: $tagKeywords | $description"

        val updated = current.copy(
            imageDescription = if (title.isNotBlank()) title else current.imageDescription,
            userComment = formattedComment,
            software = "Shots Studio AI ($modelName)",
            dateTaken = current.dateTaken ?: currentDate
        )

        val writeResult = writeExif(file, updated)
        return if (writeResult.isSuccess) {
            Result.success(readExif(file))
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: RuntimeException("Failed to save EXIF attributes"))
        }
    }

    /**
     * Ensures we have a writable file handle. If the current file is read-only
     * (e.g. Scoped Storage / external MediaStore / Photo Picker), a working copy
     * is created in the app's internal private storage directory.
     */
    private fun resolveWritableFile(context: Context, screenshot: ScreenshotItem): File? {
        val existing = File(screenshot.filePath)
        if (existing.exists() && existing.canWrite()) {
            return existing
        }

        // Create writable working copy in internal storage
        return try {
            val workingDir = File(context.filesDir, "media_metadata_working").apply { mkdirs() }
            val fileName = "edit_${screenshot.id.replace('/', '_')}_${System.currentTimeMillis()}.jpg"
            val targetFile = File(workingDir, fileName)

            var copied = false
            if (!screenshot.uriString.isNullOrBlank()) {
                val uri = Uri.parse(screenshot.uriString)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                        copied = true
                    }
                }
            }

            if (!copied && existing.exists() && existing.canRead()) {
                existing.inputStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                        copied = true
                    }
                }
            }

            if (copied && targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
