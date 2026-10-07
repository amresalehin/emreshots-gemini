package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Manages an on-device lightweight text-only Local LLM dedicated to repairing
 * OCR artefacts and generating tags (PixelShot style) in the background.
 *
 * Runs purely on CPU/NEON using llama.cpp with NO vision projector (mmproj) overhead,
 * maintaining a minimal RAM footprint (<150MB) and high inference speed.
 */
class LocalOcrLlmManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    val defaultModelName = "SmolLM2-135M-Instruct-Q2_K.gguf"
    val defaultDownloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q2_K.gguf"

    val modelFile: File
        get() {
            val bundled = File(modelsDir, defaultModelName)
            if (bundled.exists() && bundled.length() > 80L * 1024L * 1024L) return bundled
            return modelsDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf", ignoreCase = true) && it.length() > 80L * 1024L * 1024L } ?: bundled
        }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isModelInstalled = MutableStateFlow(false)
    val isModelInstalled: StateFlow<Boolean> = _isModelInstalled.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Local AI ready")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        checkInstalledState()
    }

    fun checkInstalledState(): Boolean {
        val ready = isInstalledOnDisk() || hasBundledAsset()
        _isModelInstalled.value = ready
        return ready
    }

    private fun isInstalledOnDisk(): Boolean {
        val file = modelFile
        return file.exists() && file.length() > 80L * 1024L * 1024L
    }

    private fun hasBundledAsset(): Boolean {
        return try {
            val assetFiles = context.assets.list("models") ?: emptyArray()
            assetFiles.any { it.endsWith(".gguf", ignoreCase = true) }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Extracts bundled predownloaded model from assets if available, or verifies on-disk file.
     */
    suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
        val target = modelFile
        if (target.exists() && target.length() > 1024 * 1024) {
            _isModelInstalled.value = true
            return@withContext true
        }

        // 1. Check if predownloaded/bundled in assets
        try {
            val assetFiles = context.assets.list("models") ?: emptyArray()
            val bundledName = assetFiles.firstOrNull { it.endsWith(".gguf", ignoreCase = true) }
            if (bundledName != null) {
                _statusMessage.value = "Extracting predownloaded local AI…"
                val dest = File(modelsDir, bundledName)
                context.assets.open("models/$bundledName").use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (dest.length() > 1024 * 1024) {
                    _isModelInstalled.value = true
                    _statusMessage.value = "Predownloaded local AI ready"
                    return@withContext true
                }
            }
        } catch (t: Throwable) {
            // Asset extraction failed or not found
        }


        val ready = target.exists() && target.length() > 1024 * 1024
        _isModelInstalled.value = ready
        ready
    }

    /**
     * Downloads the lightweight local model if not yet present.
     */
    suspend fun downloadModel(onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext false
        _isDownloading.value = true
        _statusMessage.value = "Downloading small local AI (~88 MB)…"
        val tempFile = File(modelsDir, "$defaultModelName.tmp")

        try {
            val request = Request.Builder().url(defaultDownloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _statusMessage.value = "Download failed: HTTP ${response.code}"
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalLength = body.contentLength().coerceAtLeast(1L)
                var bytesCopied = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            val progress = (bytesCopied.toFloat() / totalLength).coerceIn(0f, 1f)
                            _downloadProgress.value = progress
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            val finalFile = File(modelsDir, defaultModelName)
            if (tempFile.exists() && tempFile.length() > 1024 * 1024) {
                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)
                _isModelInstalled.value = true
                _statusMessage.value = "Local AI installed and ready"
                return@withContext true
            }
            false
        } catch (e: Exception) {
            _statusMessage.value = "Download error: ${e.message}"
            tempFile.delete()
            false
        } finally {
            _isDownloading.value = false
            _downloadProgress.value = 0f
        }
    }

    /**
     * Imports a user-selected GGUF file from Uri.
     */
    suspend fun importModel(uri: Uri, fileName: String = defaultModelName): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(modelsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            val success = dest.exists() && dest.length() > 1024 * 1024
            if (success) {
                _isModelInstalled.value = true
                _statusMessage.value = "Imported custom local AI model"
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs text cleaning & tag generation using the small local text LLM.
     * Returns structured result (fixed OCR text + categorization tags + title).
     */
    suspend fun processOcrWithLocalLlm(rawText: String): LocalOcrAiResult? = withContext(Dispatchers.IO) {
        if (rawText.isBlank() || rawText.length < 3) return@withContext null
        if (!ensureModelReady()) return@withContext null

        return@withContext try {
            val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 32)
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            val helper = LlamaHelper(context.contentResolver, scope, events)

            var loaded = false
            // mmproj is null: purely text-only model inference, NO VLM overhead!
            helper.load(Uri.fromFile(modelFile).toString(), 2048, null) {
                loaded = it > 0
            }

            val loadedEvent = withTimeoutOrNull(30000) {
                events.first { e -> e is LlamaHelper.LLMEvent.Loaded || e is LlamaHelper.LLMEvent.Error }
            }

            if (loadedEvent !is LlamaHelper.LLMEvent.Loaded || !loaded) {
                helper.release()
                scope.cancel()
                return@withContext null
            }

            val prompt = """
You are an on-device screenshot AI assistant like PixelShot.
Analyze this extracted OCR text:
1. Fix OCR scanning typos, hyphenations across line-breaks, spacing before punctuation, and broken URLs.
2. Generate 3 to 6 concise lowercase topic/content tags (e.g., "receipt", "shopping", "code", "chat", "flight", "finance").
3. Create a short title (2 to 5 words).

Respond ONLY in JSON format:
{"fixedText": "cleaned text transcript", "tags": ["tag1", "tag2"], "title": "short title"}

Text:
$rawText
""".trimIndent()

            helper.predict(prompt, "", false)

            val resultEvent = withTimeoutOrNull(25000) {
                events.first { it is LlamaHelper.LLMEvent.Done || it is LlamaHelper.LLMEvent.Error }
            }

            helper.abort()
            helper.release()
            scope.cancel()

            if (resultEvent is LlamaHelper.LLMEvent.Done) {
                val full = resultEvent.fullText.trim()
                parseLlmJson(full, rawText)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses the LLM's JSON response, falling back gracefully if format differs.
     */
    private fun parseLlmJson(rawOutput: String, originalText: String): LocalOcrAiResult? {
        val s = rawOutput.indexOf("{")
        val e = rawOutput.lastIndexOf("}")
        if (s in 0 until e) {
            try {
                val json = JSONObject(rawOutput.substring(s, e + 1))
                val fixed = json.optString("fixedText").ifBlank {
                    json.optString("text").ifBlank { originalText }
                }
                val tags = mutableListOf<String>()
                val arr = json.optJSONArray("tags")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val t = arr.optString(i).trim().lowercase()
                        if (t.isNotBlank() && t.length in 2..25) {
                            tags.add(t)
                        }
                    }
                }
                val title = json.optString("title").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                return LocalOcrAiResult(
                    fixedOcrText = fixed,
                    tags = tags.distinct().take(6),
                    title = title
                )
            } catch (_: Throwable) {
                // fall through
            }
        }

        // If not strictly JSON but output is non-empty clean text
        if (rawOutput.isNotBlank() && !rawOutput.startsWith("Error")) {
            return LocalOcrAiResult(
                fixedOcrText = rawOutput,
                tags = emptyList(),
                title = null
            )
        }
        return null
    }

    /**
     * Clean OCR text helper for backward compatibility.
     */
    suspend fun cleanOcrTextWithLocalLlm(rawText: String): String? {
        return processOcrWithLocalLlm(rawText)?.fixedOcrText
    }
}
