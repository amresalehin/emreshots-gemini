package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TessDataManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    val tessDataDir: File
        get() = File(context.filesDir, "tessdata").apply { if (!exists()) mkdirs() }

    val dataPath: String
        get() = context.filesDir.absolutePath

    /**
     * Extracts bundled traineddata from assets/tessdata/ into files/tessdata/ if not yet extracted.
     */
    suspend fun ensureBundledData(): Boolean = withContext(Dispatchers.IO) {
        try {
            tessDataDir // ensure dir exists
            val assetManager = context.assets
            val assetFiles = try {
                assetManager.list("tessdata") ?: emptyArray()
            } catch (_: Throwable) {
                emptyArray()
            }

            for (fileName in assetFiles) {
                if (!fileName.endsWith(".traineddata")) continue
                val targetFile = File(tessDataDir, fileName)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    assetManager.open("tessdata/$fileName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns the list of installed language codes (e.g. ["eng", "spa"]).
     */
    fun getInstalledLanguages(): List<String> {
        val dir = File(context.filesDir, "tessdata")
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".traineddata") && f.length() > 0 } ?: return emptyList()
        return files.map { it.name.removeSuffix(".traineddata") }.sorted()
    }

    fun isLanguageInstalled(code: String): Boolean {
        val file = File(tessDataDir, "$code.traineddata")
        return file.exists() && file.length() > 0
    }

    fun getLanguageFileSize(code: String): Long {
        val file = File(tessDataDir, "$code.traineddata")
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Downloads traineddata for a given language code from tessdata_fast repository.
     */
    suspend fun downloadLanguage(
        code: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            tessDataDir
            val lang = TessLanguage.findByCode(code)
            val url = lang.downloadUrl
            val request = Request.Builder().url(url).build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Download failed with HTTP ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                IllegalStateException("Empty response body")
            )

            val contentLength = body.contentLength()
            val tempFile = File(tessDataDir, "$code.tmp")
            val targetFile = File(tessDataDir, "$code.traineddata")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file was empty"))
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1f)
            Result.success(targetFile)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Imports a user-selected .traineddata file from a content Uri.
     */
    suspend fun importLanguageFile(uri: Uri, preferredName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            tessDataDir
            var code = preferredName.substringBeforeLast(".")
            if (code.isBlank()) code = "custom"
            val targetFile = File(tessDataDir, "$code.traineddata")

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open selected file."))

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext Result.failure(IllegalStateException("Imported file is empty."))
            }

            Result.success(targetFile)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Deletes a language traineddata file.
     */
    fun deleteLanguage(code: String): Boolean {
        val file = File(tessDataDir, "$code.traineddata")
        return if (file.exists()) file.delete() else false
    }
}
