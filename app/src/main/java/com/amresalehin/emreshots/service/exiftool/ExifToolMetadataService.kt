package com.amresalehin.emreshots.service.exiftool

import android.content.Context
import android.net.Uri
import com.amresalehin.emreshots.data.model.ExifData
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Real ExifTool-backed metadata writer.
 *
 * The source image is never interpreted by a shell. Arguments are passed directly
 * to the bundled Perl/ExifTool process. Content URIs are materialized to a private
 * cache file, preserving the original filename/extension, then copied back.
 */
class ExifToolMetadataService(private val context: Context) {

    fun writeFile(file: File, data: ExifData): Result<Unit> = runBlocking {
        if (!file.exists() || !file.canRead() || !file.canWrite()) {
            return@runBlocking Result.failure(IllegalStateException("File is not readable/writable: ${file.absolutePath}"))
        }
        runCatching {
            AssetExtractor.ensureInstalled(context)
            val command = ExifToolRunner.buildCommand(context, buildWriteArgs(data), listOf(file.absolutePath))
            val output = ExifToolRunner.run(command)
            if (output.exitCode != 0) error("ExifTool failed (${output.exitCode}): ${output.output.take(1000)}")
            Unit
        }
    }

    fun writeUri(context: Context, uri: Uri, data: ExifData): Result<Unit> = runBlocking {
        val workDir = File(context.cacheDir, "exiftool-runs/${System.nanoTime()}").apply { mkdirs() }
        try {
            val name = queryDisplayName(context, uri) ?: "image.jpg"
            val working = File(workDir, sanitizeFileName(name))
            context.contentResolver.openInputStream(uri)?.use { input ->
                working.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runBlocking Result.failure(IllegalStateException("Unable to read URI"))

            val result = writeFile(working, data)
            if (result.isFailure) return@runBlocking result

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                working.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runBlocking Result.failure(IllegalStateException("Unable to open URI for writing"))
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildWriteArgs(data: ExifData): List<String> = buildList {
        add("-overwrite_original")
        add("-P")
        add("-charset"); add("filename=UTF8")
        put("EXIF:DateTimeOriginal", data.dateTaken)
        put("EXIF:DateTimeDigitized", data.dateTaken)
        put("EXIF:DateTime", data.dateTaken)
        put("EXIF:Make", data.cameraMake)
        put("EXIF:Model", data.cameraModel)
        put("EXIF:Software", data.software)
        put("EXIF:Artist", data.artist)
        put("EXIF:ImageDescription", data.imageDescription)
        put("EXIF:UserComment", data.userComment)
        put("EXIF:ISOSpeedRatings", data.iso)
        put("EXIF:FNumber", data.fNumber)
        put("EXIF:ExposureTime", data.exposureTime)
        put("EXIF:FocalLength", data.focalLength)
        if (data.orientation > 0) put("EXIF:Orientation", data.orientation.toString())
        if (data.latitude != null && data.longitude != null) {
            put("GPS:GPSLatitude", data.latitude.toString())
            put("GPS:GPSLongitude", data.longitude.toString())
        }
    }

    private fun MutableList<String>.put(tag: String, value: String?) {
        if (!value.isNullOrBlank()) add("-$tag=$value")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun sanitizeFileName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "image.jpg" }
}