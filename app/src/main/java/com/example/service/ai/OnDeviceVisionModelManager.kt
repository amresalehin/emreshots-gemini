package com.amresalehin.emreshots.service.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OnDeviceVisionModelManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    val root = File(context.filesDir, "models/ondevice-vision").apply { mkdirs() }

    suspend fun installedModels(): List<InstalledVisionModel> = withContext(Dispatchers.IO) {
        // Also discover any custom folders in root containing GGUF files
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (OnDeviceVisionCatalog.find(dir.name) == null) {
                val gguf = dir.listFiles()?.firstOrNull { it.name.endsWith(".gguf", ignoreCase = true) && !it.name.startsWith("mmproj") }
                if (gguf != null && gguf.length() > 0) {
                    val mm = dir.listFiles()?.firstOrNull { it.name.startsWith("mmproj") && it.name.endsWith(".gguf") }
                    val arts = mutableListOf(ModelArtifact("base", gguf.name, "", gguf.length(), "CUSTOM"))
                    if (mm != null) arts.add(ModelArtifact("mmproj", mm.name, "", mm.length(), "CUSTOM"))
                    val m = OnDeviceVisionModel(
                        id = dir.name,
                        family = "GGUF Custom",
                        displayName = dir.name.removePrefix("custom-").replace("-", " ").replaceFirstChar { it.uppercase() },
                        parameterCount = "Custom",
                        quantization = "GGUF",
                        storageMb = (gguf.length() / (1024 * 1024)).toInt().coerceAtLeast(1),
                        minRamMb = 2048,
                        recommendedRamMb = 4096,
                        license = "Local GGUF",
                        sourceUrl = "",
                        artifacts = arts
                    )
                    OnDeviceVisionCatalog.registerCustomModel(m)
                }
            }
        }
        OnDeviceVisionCatalog.all().mapNotNull { installedModel(it) }
    }

    suspend fun installedModel(m: OnDeviceVisionModel): InstalledVisionModel? = withContext(Dispatchers.IO) {
        if (m.artifacts.isEmpty()) return@withContext null
        val d = File(root, m.id)
        if (!d.isDirectory) return@withContext null
        val valid = m.artifacts.all { a ->
            val f = File(d, a.fileName)
            verifyArtifact(f, a)
        }
        if (!valid) return@withContext null
        InstalledVisionModel(m, d, d.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    suspend fun isInstalled(m: OnDeviceVisionModel): Boolean = installedModel(m) != null

    suspend fun install(m: OnDeviceVisionModel, onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        require(m.artifacts.isNotEmpty()) { "No download artifacts specified for ${m.displayName}" }
        val d = File(root, m.id).apply { mkdirs() }
        val total = m.artifacts.sumOf { it.sizeBytes }
        var done = 0L
        onProgress(0, total)

        m.artifacts.forEach { a ->
            val target = File(d, a.fileName)
            if (verifyArtifact(target, a)) {
                done += target.length().coerceAtLeast(a.sizeBytes)
                onProgress(done, total)
                return@forEach
            }
            val part = File(d, a.fileName + ".part")
            download(a.url, part, a.sizeBytes) { onProgress(done + it, total) }
            check(verifyArtifact(part, a)) { "Verification failed for ${a.fileName}: corrupt or non-GGUF file." }
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "Failed to save ${a.fileName}" }
            done += target.length().coerceAtLeast(a.sizeBytes)
            onProgress(done, total)
        }
        check(installedModel(m) != null) { "Model bundle verification failed after installation." }
    }

    suspend fun delete(m: OnDeviceVisionModel): Boolean = withContext(Dispatchers.IO) {
        File(root, m.id).deleteRecursively()
    }

    suspend fun storageUsageBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun importCustomGguf(uri: Uri, preferredName: String? = null): OnDeviceVisionModel = withContext(Dispatchers.IO) {
        val fileName = preferredName?.ifBlank { null }
            ?: runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                }
            }.getOrNull()
            ?: "custom-model.gguf"

        val cleanBaseName = fileName.substringBeforeLast(".").ifBlank { "custom-model" }
        val safeId = "custom-" + cleanBaseName.lowercase().replace("[^a-z0-9_-]".toRegex(), "-")
        val d = File(root, safeId).apply { mkdirs() }
        val targetFile = File(d, "$cleanBaseName.gguf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read selected GGUF file from storage.")

        check(targetFile.exists() && targetFile.length() > 0) { "Imported GGUF file is empty." }

        val artifacts = listOf(
            ModelArtifact("base", targetFile.name, "", targetFile.length(), "CUSTOM")
        )
        val model = OnDeviceVisionModel(
            id = safeId,
            family = "GGUF Custom",
            displayName = cleanBaseName.replace("-", " ").replace("_", " ").trim().replaceFirstChar { it.uppercase() },
            parameterCount = "Custom",
            quantization = "GGUF",
            storageMb = (targetFile.length() / (1024 * 1024)).toInt().coerceAtLeast(1),
            minRamMb = 2048,
            recommendedRamMb = 4096,
            license = "User GGUF",
            sourceUrl = "",
            artifacts = artifacts,
            notes = "User imported GGUF model."
        )
        OnDeviceVisionCatalog.registerCustomModel(model)
        model
    }

    private fun verifyArtifact(file: File, artifact: ModelArtifact): Boolean {
        if (!file.isFile || file.length() == 0L) return false

        // 1. If standard GGUF file or extension
        if (isGgufFile(file)) return true

        // 2. If SHA-256 matches pinned hash
        if (artifact.sha256.isNotBlank() && artifact.sha256 != "UNVERIFIED" && artifact.sha256 != "CUSTOM") {
            if (sha256(file).equals(artifact.sha256, ignoreCase = true)) return true
        }

        // 3. If file size matches or is valid binary > 512KB
        if (file.name.endsWith(".gguf", ignoreCase = true) && file.length() >= 512_000L) {
            return true
        }
        if (artifact.sizeBytes > 0L && file.length() == artifact.sizeBytes) {
            return true
        }

        return false
    }

    private fun isGgufFile(file: File): Boolean {
        if (!file.isFile || file.length() < 1024) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return@runCatching false
                magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun download(url: String, p: File, expected: Long, onProgress: (Long) -> Unit) {
        var offset = if (p.exists()) p.length() else 0L
        if (expected > 0 && offset > expected) {
            p.delete()
            offset = 0L
        }
        val request = Request.Builder()
            .url(url)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val append = offset > 0 && response.code == 206
            if (!append) {
                offset = 0L
                RandomAccessFile(p, "rw").use { it.setLength(0L) }
            }
            val body = response.body ?: error("Empty model response")
            body.byteStream().use { input ->
                RandomAccessFile(p, "rw").use { output ->
                    output.seek(offset)
                    val buffer = ByteArray(65536)
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
        check(p.length() > 0) { "Downloaded empty model artifact" }
    }

    private fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val b = ByteArray(65536)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                d.update(b, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
