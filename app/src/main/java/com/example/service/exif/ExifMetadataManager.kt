package com.amresalehin.emreshots.service.exif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExifMetadataManager {

    /**
     * Reads EXIF metadata from either a local file or a content URI,
     * handling Scoped Storage and unredacted media location permissions.
     */
    fun readExif(context: Context, screenshot: ScreenshotItem): ExifData {
        // 1. If filePath is a readable existing local file (e.g. app storage or working copy),
        // read directly from it so recent metadata edits are immediately reflected.
        val localFile = File(screenshot.filePath)
        if (localFile.exists() && localFile.canRead() && localFile.length() > 0) {
            val fileData = readExif(localFile)
            if (fileData.hasDetails() || screenshot.uriString.isNullOrBlank()) {
                return fileData
            }
        }

        // 2. Try reading via content URI (supports MediaStore and Photo Picker)
        if (!screenshot.uriString.isNullOrBlank()) {
            try {
                val baseUri = Uri.parse(screenshot.uriString)
                val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    screenshot.uriString.startsWith("content://media/")
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
                    val uriData = extractFromExifInterface(ExifInterface(inputStream))
                    if (uriData.hasDetails()) {
                        return uriData
                    }
                }
            } catch (_: Exception) {
                // Fallback to direct file path
            }
        }

        // 3. Fallback to direct file path
        return readExif(localFile)
    }

    /**
     * Reads EXIF metadata from a local File.
     */
    fun readExif(file: File): ExifData {
        if (!file.exists() || !file.canRead() || file.length() <= 0L) {
            return ExifData()
        }
        return try {
            val exifInterface = ExifInterface(file.absolutePath)
            extractFromExifInterface(exifInterface)
        } catch (_: Exception) {
            ExifData()
        }
    }

    fun extractFromExifInterface(exifInterface: ExifInterface): ExifData {
        val latLong = exifInterface.latLong

        val rawDate = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)

        return ExifData(
            dateTaken = rawDate,
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
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1)
        )
    }

    /**
     * Applies metadata fields to an ExifInterface instance.
     * Properly sets or clears tags, formats dates, and manages GPS coordinates.
     */
    fun applyDataToExifInterface(exifInterface: ExifInterface, data: ExifData) {
        val normalizedDate = normalizeExifDate(data.dateTaken)

        if (normalizedDate != null) {
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, normalizedDate)
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME, normalizedDate)
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, normalizedDate)
        } else {
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME, null)
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
        }

        exifInterface.setAttribute(ExifInterface.TAG_MAKE, data.cameraMake?.takeIf { it.isNotBlank() })
        exifInterface.setAttribute(ExifInterface.TAG_MODEL, data.cameraModel?.takeIf { it.isNotBlank() })
        exifInterface.setAttribute(ExifInterface.TAG_SOFTWARE, data.software?.takeIf { it.isNotBlank() })
        exifInterface.setAttribute(ExifInterface.TAG_ARTIST, data.artist?.takeIf { it.isNotBlank() })
        exifInterface.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, data.imageDescription?.takeIf { it.isNotBlank() })
        exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, data.userComment?.takeIf { it.isNotBlank() })

        if (data.orientation > 0) {
            exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, data.orientation.toString())
        }

        // Handle GPS Coordinates: set when present, remove when null
        if (data.latitude != null && data.longitude != null) {
            exifInterface.setLatLong(data.latitude, data.longitude)
        } else {
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
        }
    }

    /**
     * Direct file write for internal or writable files.
     */
    fun writeExif(file: File, data: ExifData): Result<Unit> {
        if (!file.exists() || !file.canWrite()) {
            return Result.failure(IllegalStateException("File does not exist or cannot be written: ${file.absolutePath}"))
        }
        return try {
            val exifInterface = ExifInterface(file.absolutePath)
            applyDataToExifInterface(exifInterface, data)
            exifInterface.saveAttributes()
            Result.success(Unit)
        } catch (e: Exception) {
            // If direct saveAttributes failed (e.g. incompatible PNG chunk), fallback to recompression
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    val exifInterface = ExifInterface(file.absolutePath)
                    applyDataToExifInterface(exifInterface, data)
                    exifInterface.saveAttributes()
                    return Result.success(Unit)
                }
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    /**
     * Direct write to a content URI (e.g. MediaStore) via FileDescriptor.
     */
    fun writeExifToUri(context: Context, uri: Uri, data: ExifData): Result<Unit> {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: return Result.failure(IllegalStateException("Cannot open FileDescriptor for $uri"))
            pfd.use {
                val exifInterface = ExifInterface(it.fileDescriptor)
                applyDataToExifInterface(exifInterface, data)
                exifInterface.saveAttributes()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Writes EXIF metadata to an image.
     * If the target is in external storage or read-only (Scoped Storage),
     * this automatically creates a writable local copy in app-specific storage,
     * writes the EXIF tags, and returns the updated File.
     */
    fun writeExifSafe(
        context: Context,
        screenshot: ScreenshotItem,
        data: ExifData
    ): Result<File> {
        // 1. If uriString is a writable content URI, attempt in-place edit
        if (!screenshot.uriString.isNullOrBlank() && screenshot.uriString.startsWith("content://media/")) {
            val uri = Uri.parse(screenshot.uriString)
            val uriResult = writeExifToUri(context, uri, data)
            if (uriResult.isSuccess) {
                val existing = File(screenshot.filePath)
                if (existing.exists() && existing.canWrite()) {
                    writeExif(existing, data)
                    return Result.success(existing)
                }
            }
        }

        // 2. Resolve a writable file handle (direct or working copy in app-specific storage)
        val targetFile = resolveWritableFile(context, screenshot)
            ?: return Result.failure(IllegalStateException("Could not resolve a writable image file for metadata editing."))

        val writeResult = writeExif(targetFile, data)
        return if (writeResult.isSuccess) {
            Result.success(targetFile)
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: IllegalStateException("Failed to write EXIF attributes"))
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
        val formattedComment = "EmreShots AI [$modelName] | Tags: $tagKeywords | $description"

        val updated = current.copy(
            imageDescription = if (title.isNotBlank()) title else current.imageDescription,
            userComment = formattedComment,
            software = "EmreShots AI ($modelName)",
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
        val formattedComment = "EmreShots AI [$modelName] | Tags: $tagKeywords | $description"

        val updated = current.copy(
            imageDescription = if (title.isNotBlank()) title else current.imageDescription,
            userComment = formattedComment,
            software = "EmreShots AI ($modelName)",
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
     * is created in the app's internal private storage directory with the correct format extension.
     */
    private fun resolveWritableFile(context: Context, screenshot: ScreenshotItem): File? {
        val existing = File(screenshot.filePath)
        if (existing.exists() && existing.canWrite()) {
            return existing
        }

        return try {
            val workingDir = File(context.filesDir, "media_metadata_working").apply { mkdirs() }
            val extension = determineImageExtension(context, screenshot, existing)
            val safeId = screenshot.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "edit_${safeId}_${System.currentTimeMillis()}.$extension"
            val targetFile = File(workingDir, fileName)

            var copied = false
            if (!screenshot.uriString.isNullOrBlank()) {
                val uri = Uri.parse(screenshot.uriString)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                            copied = true
                        }
                    }
                } catch (_: Exception) {
                    copied = false
                }
            }

            if (!copied && existing.exists() && existing.canRead()) {
                try {
                    existing.inputStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                            copied = true
                        }
                    }
                } catch (_: Exception) {
                    copied = false
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

    /**
     * Determines proper extension (png, jpg, webp) to avoid EXIF format mismatch.
     */
    fun determineImageExtension(context: Context, screenshot: ScreenshotItem, existing: File): String {
        // 1. ContentResolver MIME type
        if (!screenshot.uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(screenshot.uriString)
                val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US)
                if (mime != null) {
                    when {
                        mime.contains("png") -> return "png"
                        mime.contains("webp") -> return "webp"
                        mime.contains("jpeg") || mime.contains("jpg") -> return "jpg"
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. File path extension
        val path = screenshot.filePath.lowercase(Locale.US)
        when {
            path.endsWith(".png") -> return "png"
            path.endsWith(".webp") -> return "webp"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> return "jpg"
        }

        // 3. Inspect magic bytes if file exists
        if (existing.exists() && existing.canRead() && existing.length() >= 8) {
            try {
                existing.inputStream().use { stream ->
                    val header = ByteArray(8)
                    val read = stream.read(header)
                    if (read >= 4) {
                        if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
                        ) {
                            return "png"
                        }
                        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
                            header[2] == 0xFF.toByte()
                        ) {
                            return "jpg"
                        }
                        if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
                        ) {
                            return "webp"
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return "jpg"
    }

    /**
     * Normalizes date strings like '2026-10-03 12:00:00' to EXIF standard '2026:10:03 12:00:00'.
     */
    fun normalizeExifDate(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        val regex = Regex("^(\\d{4})[-/](\\d{2})[-/](\\d{2})")
        return if (regex.containsMatchIn(trimmed)) {
            trimmed.replace(regex, "$1:$2:$3")
        } else {
            trimmed
        }
    }
}
