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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val expectedModelSha256 = "741ad12b64088fedc17c33aacb22e48be1972ef36a39f03666dd68bd15614fb9"
    private val minimumModelBytes = 80L * 1024L * 1024L

    val modelFile: File
        get() = File(modelsDir, defaultModelName)

    private val inferenceMutex = Mutex()

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
        return file.exists() && file.length() > 80L * 1024L * 1024L && isValidGguf(file)
    }

    private fun hasBundledAsset(): Boolean {
        return try {
            val assetFiles = context.assets.list("models") ?: emptyArray()
            assetFiles.any { it.equals(defaultModelName, ignoreCase = true) }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Extracts bundled predownloaded model from assets if available, or verifies on-disk file.
     */
    suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
        val target = modelFile
        if (target.exists() && target.length() > 80L * 1024L * 1024L && isValidGguf(target)) {
            _isModelInstalled.value = true
            return@withContext true
        }

        // 1. Check if predownloaded/bundled in assets
        try {
            val assetFiles = context.assets.list("models") ?: emptyArray()
            val bundledName = assetFiles.firstOrNull { it.equals(defaultModelName, ignoreCase = true) }
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
                if (dest.length() > 80L * 1024L * 1024L && isValidGguf(dest)) {
                    _isModelInstalled.value = true
                    _statusMessage.value = "Predownloaded local AI ready"
                    return@withContext true
                }
            }
        } catch (t: Throwable) {
            // Asset extraction failed or not found
        }


        val ready = target.exists() && target.length() > 80L * 1024L * 1024L && isValidGguf(target)
        _isModelInstalled.value = ready
        ready
    }

    /**
     * Downloads the lightweight local model if not yet present.
     */
    suspend fun downloadModel(onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext false
        _isDownloading.value = true
        _statusMessage.value = "Downloading optional local OCR AI (about 84 MB)…"
        val tempFile = File(modelsDir, "$defaultModelName.tmp")

        try {
            tempFile.delete()
            val request = Request.Builder().url(defaultDownloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _statusMessage.value = "Download failed: HTTP ${response.code}"
                    return@withContext false
                }
                val body = response.body ?: error("The model server returned an empty response.")
                val totalLength = body.contentLength().coerceAtLeast(1L)
                var bytesCopied = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            bytesCopied += count
                            val progress = (bytesCopied.toFloat() / totalLength).coerceIn(0f, 1f)
                            _downloadProgress.value = progress
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            // Reject truncated downloads, HTML error pages, and substituted model files.
            if (!tempFile.isFile || tempFile.length() < minimumModelBytes ||
                !isValidGguf(tempFile) ||
                !sha256(tempFile).equals(expectedModelSha256, ignoreCase = true)
            ) {
                _statusMessage.value = "Downloaded model failed integrity verification."
                tempFile.delete()
                return@withContext false
            }

            val finalFile = File(modelsDir, defaultModelName)
            if (finalFile.exists() && !finalFile.delete()) error("Could not replace the previous local OCR model.")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            _isModelInstalled.value = isInstalledOnDisk()
            if (!_isModelInstalled.value) {
                finalFile.delete()
                error("The installed local OCR model failed verification.")
            }
            _downloadProgress.value = 1f
            onProgress?.invoke(1f)
            _statusMessage.value = "Local OCR AI installed and verified"
            true
        } catch (e: Exception) {
            _statusMessage.value = "Download error: ${e.message ?: "unknown error"}"
            tempFile.delete()
            false
        } finally {
            _isDownloading.value = false
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
    suspend fun processOcrWithLocalLlm(rawText: String): LocalOcrAiResult? =
        withContext(Dispatchers.IO) {
            if (rawText.isBlank() || rawText.length < 3) return@withContext null
            if (!ensureModelReady()) return@withContext null

            // llama.cpp is native code. Serialize loads/inference and never substitute an
            // arbitrary GGUF from the shared models directory into this text-only runtime.
            inferenceMutex.withLock {
                var helper: LlamaHelper? = null
                var scope: kotlinx.coroutines.CoroutineScope? = null
                try {
                    val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 32)
                    val inferenceScope = kotlinx.coroutines.CoroutineScope(
                        Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
                    )
                    scope = inferenceScope
                    val llama = LlamaHelper(context.contentResolver, inferenceScope, events)
                    helper = llama

                    var loaded = false
                    llama.load(Uri.fromFile(modelFile).toString(), 2048, null) {
                        loaded = it > 0
                    }

                    val loadedEvent = withTimeoutOrNull(30000) {
                        events.first { event ->
                            event is LlamaHelper.LLMEvent.Loaded || event is LlamaHelper.LLMEvent.Error
                        }
                    }

                    if (loadedEvent !is LlamaHelper.LLMEvent.Loaded || !loaded) {
                        return@withLock null
                    }

                    val promptText = rawText.take(7000)
                    val prompt = """
You are an on-device screenshot AI assistant like PixelShot.
Analyze this extracted OCR text:
1. Fix OCR scanning typos, hyphenations across line-breaks, spacing before punctuation, and broken URLs.
2. Generate 3 to 6 concise lowercase topic/content tags (e.g., "receipt", "shopping", "code", "chat", "flight", "finance").
3. Create a short title (2 to 5 words).

Respond ONLY in JSON format:
{"fixedText": "cleaned text transcript", "tags": ["tag1", "tag2"], "title": "short title"}

Text:
$promptText
""".trimIndent()

                    llama.predict(prompt, "", false)

                    val resultEvent = withTimeoutOrNull(25000) {
                        events.first { event ->
                            event is LlamaHelper.LLMEvent.Done || event is LlamaHelper.LLMEvent.Error
                        }
                    }

                    if (resultEvent is LlamaHelper.LLMEvent.Done) {
                        parseLlmJson(resultEvent.fullText.trim(), rawText)
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    // Native/model failures must never take down the OCR pipeline. The caller
                    // falls back to deterministic OCR cleanup and tag extraction.
                    null
                } finally {
                    try { helper?.abort() } catch (_: Throwable) {}
                    try { helper?.release() } catch (_: Throwable) {}
                    try { scope?.cancel() } catch (_: Throwable) {}
                }
            }
        }

    private fun isValidGguf(file: File): Boolean {
        return try {
            if (!file.isFile || file.length() < 16L) return false
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (_: Throwable) {
            false
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
