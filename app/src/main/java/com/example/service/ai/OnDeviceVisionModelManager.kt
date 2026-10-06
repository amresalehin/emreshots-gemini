package com.amresalehin.emreshots.service.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OnDeviceVisionModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val root = File(context.filesDir, "models/ondevice-vision").apply { mkdirs() }

    suspend fun installedModels() = withContext(Dispatchers.IO) {
        OnDeviceVisionCatalog.all().mapNotNull { installedModel(it) }
    }

    suspend fun installedModel(m: OnDeviceVisionModel): InstalledVisionModel? = withContext(Dispatchers.IO) {
        if (m.artifacts.isEmpty()) return@withContext null
        val directory = File(root, m.id)
        if (!directory.isDirectory) return@withContext null
        if (!m.artifacts.all { artifact ->
                val file = File(directory, artifact.fileName)
                file.isFile &&
                    file.length() == artifact.sizeBytes &&
                    artifact.sha256 != "UNVERIFIED" &&
                    sha256(file) == artifact.sha256
            }
        ) return@withContext null
        InstalledVisionModel(m, directory, directory.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    suspend fun isInstalled(m: OnDeviceVisionModel) = installedModel(m) != null

    suspend fun install(m: OnDeviceVisionModel, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            require(m.artifacts.isNotEmpty()) { "No verified downloadable bundle is available for " + m.displayName }
            require(m.artifacts.none { it.sha256 == "UNVERIFIED" }) { "Checksum is not pinned for this model." }

            val directory = File(root, m.id).apply { mkdirs() }
            val total = m.artifacts.sumOf { it.sizeBytes }
            var done = 0L
            onProgress(0, total)

            m.artifacts.forEach { artifact ->
                val target = File(directory, artifact.fileName)
                if (target.isFile && target.length() == artifact.sizeBytes && sha256(target).equals(artifact.sha256, true)) {
                    done += artifact.sizeBytes
                    onProgress(done, total)
                    return@forEach
                }

                val part = File(directory, artifact.fileName + ".part")
                if (part.isFile && part.length() == artifact.sizeBytes && sha256(part).equals(artifact.sha256, true)) {
                    if (target.exists()) target.delete()
                    check(part.renameTo(target)) { "Could not finalize " + artifact.fileName }
                    done += artifact.sizeBytes
                    onProgress(done, total)
                    return@forEach
                }

                downloadWithRetry(artifact.url, part, artifact.sizeBytes) { current ->
                    onProgress(done + current, total)
                }

                check(part.length() == artifact.sizeBytes) {
                    "Downloaded size mismatch for " + artifact.fileName + ": expected " + artifact.sizeBytes + ", got " + part.length()
                }
                check(sha256(part).equals(artifact.sha256, true)) {
                    "Integrity verification failed for " + artifact.fileName
                }
                if (target.exists()) target.delete()
                check(part.renameTo(target)) { "Could not finalize " + artifact.fileName }
                done += artifact.sizeBytes
                onProgress(done, total)
            }

            check(installedModel(m) != null) { "Model bundle verification failed after installation." }
        }

    suspend fun delete(m: OnDeviceVisionModel) = withContext(Dispatchers.IO) {
        File(root, m.id).deleteRecursively()
    }

    suspend fun storageUsageBytes() = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun downloadWithRetry(
        url: String,
        part: File,
        expected: Long,
        onProgress: (Long) -> Unit
    ) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                download(url, part, expected, onProgress)
                return
            } catch (t: Throwable) {
                lastError = t
                if (attempt < 2) {
                    Thread.sleep((1000L * (attempt + 1)))
                }
            }
        }
        throw lastError ?: IllegalStateException("Model download failed.")
    }

    private fun download(
        url: String,
        part: File,
        expected: Long,
        onProgress: (Long) -> Unit
    ) {
        var offset = if (part.exists()) part.length() else 0L
        if (offset > expected) {
            part.delete()
            offset = 0L
        }
        if (offset == expected && part.isFile) return

        val request = Request.Builder().url(url).apply {
            if (offset > 0L) header("Range", "bytes=" + offset + "-")
        }.build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP " + response.code }
            val append = offset > 0L && response.code == 206
            if (!append) {
                offset = 0L
                RandomAccessFile(part, "rw").use { it.setLength(0L) }
            }
            val body = response.body ?: error("Empty model response")
            body.byteStream().use { input ->
                RandomAccessFile(part, "rw").use { output ->
                    output.seek(offset)
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = offset
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        onProgress(totalRead)
                    }
                }
            }
        }

        check(part.isFile && part.length() > 0L) { "Downloaded empty model artifact" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
