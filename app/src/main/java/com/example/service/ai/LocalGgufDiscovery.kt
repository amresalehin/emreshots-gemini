package com.amresalehin.emreshots.service.ai

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalGgufModel(
    val uri: Uri,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val isProjector: Boolean,
)

class LocalGgufDiscovery(private val context: Context) {
    suspend fun scan(folderUris: List<Uri>): List<LocalGgufModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalGgufModel>()

        context.getExternalFilesDir("models")?.let { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
                .forEach { file ->
                    results += LocalGgufModel(
                        uri = Uri.fromFile(file),
                        displayName = file.nameWithoutExtension,
                        fileName = file.name,
                        sizeBytes = file.length(),
                        isProjector = file.name.contains("mmproj", ignoreCase = true),
                    )
                }
        }

        folderUris.distinct().forEach { uri ->
            runCatching {
                val root = DocumentFile.fromTreeUri(context, uri)
                if (root != null) scanTree(root, results)
            }
        }

        results.distinctBy { it.uri.toString() }.sortedBy { it.displayName.lowercase() }
    }

    private fun scanTree(node: DocumentFile, out: MutableList<LocalGgufModel>) {
        node.listFiles().forEach { child ->
            if (child.isDirectory) {
                scanTree(child, out)
            } else if (child.name?.endsWith(".gguf", ignoreCase = true) == true && child.isFile) {
                val name = child.name.orEmpty()
                out += LocalGgufModel(
                    uri = child.uri,
                    displayName = name.substringBeforeLast('.'),
                    fileName = name,
                    sizeBytes = child.length(),
                    isProjector = name.contains("mmproj", ignoreCase = true),
                )
            }
        }
    }

    suspend fun importToPrivateStorage(model: LocalGgufModel): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = File(context.filesDir, "models/discovered").apply { mkdirs() }
            val safeName = model.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(targetDir, safeName)
            context.contentResolver.openInputStream(model.uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open local GGUF file")
            require(target.length() > 0L) { "GGUF file is empty" }
            target
        }
    }
}
