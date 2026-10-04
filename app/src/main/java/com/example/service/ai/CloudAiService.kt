package com.example.service.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.CustomCloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class CloudAiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            if (url.isEmpty()) return "http://10.0.2.2:11434"
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            while (url.endsWith("/")) {
                url = url.substring(0, url.length - 1)
            }
            return url
        }

        fun buildModelsUrl(baseUrl: String): String {
            val normalized = normalizeBaseUrl(baseUrl)
            return if (normalized.endsWith("/v1")) {
                "$normalized/models"
            } else {
                "$normalized/v1/models"
            }
        }

        fun buildChatCompletionsUrl(baseUrl: String): String {
            val normalized = normalizeBaseUrl(baseUrl)
            return if (normalized.endsWith("/v1")) {
                "$normalized/chat/completions"
            } else {
                "$normalized/v1/chat/completions"
            }
        }
    }

    suspend fun testConnection(provider: CustomCloudProvider): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val modelsUrl = buildModelsUrl(provider.baseUrl)
            val requestBuilder = Request.Builder()
                .url(modelsUrl)
                .get()

            if (provider.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            }

            parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val models = parseModelsList(body)
                    ConnectionTestResult(
                        isSuccess = true,
                        latencyMs = latency,
                        statusCode = response.code,
                        message = "Connected successfully (${latency}ms). Found ${models.size} models.",
                        availableModels = models
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        latencyMs = latency,
                        statusCode = response.code,
                        message = "Server error ${response.code}: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ConnectionTestResult(
                isSuccess = false,
                latencyMs = latency,
                statusCode = -1,
                message = "Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun fetchModels(provider: CustomCloudProvider): List<String> = withContext(Dispatchers.IO) {
        try {
            val modelsUrl = buildModelsUrl(provider.baseUrl)
            val requestBuilder = Request.Builder().url(modelsUrl).get()
            if (provider.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            }
            parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    parseModelsList(body)
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun analyzeScreenshot(
        imageFile: File?,
        provider: CustomCloudProvider,
        geminiApiKey: String = ""
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val imageBase64 = imageFile?.let { fileToBase64(it) }

            if (provider.isDefaultGemini) {
                analyzeWithGemini(imageBase64, provider.selectedModel, geminiApiKey, startTime)
            } else {
                analyzeWithCustomCloudProvider(imageBase64, provider, startTime)
            }
        } catch (e: Exception) {
            AiAnalysisResult(
                title = "Analysis Error",
                description = "Failed to analyze screenshot: ${e.localizedMessage}",
                tags = listOf("Error"),
                modelUsed = provider.selectedModel,
                processingTimeMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun analyzeWithCustomCloudProvider(
        imageBase64: String?,
        provider: CustomCloudProvider,
        startTime: Long
    ): AiAnalysisResult {
        val prompt = buildAnalysisPrompt()
        val chatUrl = buildChatCompletionsUrl(provider.baseUrl)

        val requestJson = JSONObject().apply {
            put("model", provider.selectedModel.ifBlank { "gpt-4o-mini" })
            put("temperature", 0.2)

            val messages = JSONArray()
            val userMsg = JSONObject().apply {
                put("role", "user")
                val contentArray = JSONArray()

                val textContent = JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                }
                contentArray.put(textContent)

                if (!imageBase64.isNullOrBlank()) {
                    val imageContent = JSONObject().apply {
                        put("type", "image_url")
                        val imageUrlObj = JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        }
                        put("image_url", imageUrlObj)
                    }
                    contentArray.put(imageContent)
                }

                put("content", contentArray)
            }
            messages.put(userMsg)
            put("messages", messages)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(chatUrl)
            .post(requestBody)

        if (provider.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
        }

        parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val elapsed = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return AiAnalysisResult(
                    title = "Cloud API Error (${response.code})",
                    description = "Server returned ${response.code}: $body",
                    tags = listOf("Error"),
                    modelUsed = provider.selectedModel,
                    processingTimeMs = elapsed,
                    isSuccess = false,
                    errorMessage = "HTTP ${response.code}"
                )
            }

            val content = extractChatCompletionContent(body)
            return parseAiJsonOutput(content, provider.selectedModel, elapsed)
        }
    }

    private fun analyzeWithGemini(
        imageBase64: String?,
        modelName: String,
        apiKey: String,
        startTime: Long
    ): AiAnalysisResult {
        val effectiveModel = modelName.ifBlank { "gemini-2.5-flash" }
        val prompt = buildAnalysisPrompt()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

        val root = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject().apply {
                val parts = JSONArray()
                parts.put(JSONObject().apply { put("text", prompt) })

                if (!imageBase64.isNullOrBlank()) {
                    parts.put(JSONObject().apply {
                        val inlineData = JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", imageBase64)
                        }
                        put("inline_data", inlineData)
                    })
                }
                put("parts", parts)
            }
            contents.put(contentObj)
            put("contents", contents)
        }

        val requestBody = root.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            val elapsed = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return AiAnalysisResult(
                    title = "Gemini Error",
                    description = "Error ${response.code}: $body",
                    tags = listOf("Error"),
                    modelUsed = effectiveModel,
                    processingTimeMs = elapsed,
                    isSuccess = false,
                    errorMessage = "Gemini API error: ${response.code}"
                )
            }

            val text = extractGeminiContent(body)
            return parseAiJsonOutput(text, effectiveModel, elapsed)
        }
    }

    fun parseAiJsonOutput(rawText: String, modelUsed: String, processingTimeMs: Long): AiAnalysisResult {
        try {
            // Find JSON within markdown fences or raw string
            val cleanJson = extractJsonSubstring(rawText)
            val json = JSONObject(cleanJson)

            val title = json.optString("title", "Untitled Screenshot")
            val description = json.optString("description", "")
            val tags = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.optString(i)?.trim()
                    if (!tag.isNullOrBlank()) tags.add(tag)
                }
            }

            val links = mutableListOf<String>()
            val linksArray = json.optJSONArray("links") ?: json.optJSONArray("detectedLinks")
            if (linksArray != null) {
                for (i in 0 until linksArray.length()) {
                    val link = linksArray.optString(i)?.trim()
                    if (!link.isNullOrBlank()) links.add(link)
                }
            }

            val suggestedCollection = json.optString("suggestedCollection", "").takeIf { it.isNotBlank() }
            val exifUserComment = json.optString("exifUserComment", "").takeIf { it.isNotBlank() }
            val ocrText = json.optString("ocrText", "").takeIf { it.isNotBlank() }

            return AiAnalysisResult(
                title = title,
                description = description,
                tags = if (tags.isEmpty()) listOf("Screenshot") else tags,
                detectedLinks = links,
                suggestedCollection = suggestedCollection,
                exifUserComment = exifUserComment,
                ocrText = ocrText,
                modelUsed = modelUsed,
                processingTimeMs = processingTimeMs,
                isSuccess = true
            )
        } catch (e: Exception) {
            // Graceful fallback if model produced non-strict JSON
            return AiAnalysisResult(
                title = "Analyzed Image",
                description = rawText.take(300),
                tags = listOf("Screenshot", "AI-Processed"),
                modelUsed = modelUsed,
                processingTimeMs = processingTimeMs,
                isSuccess = true
            )
        }
    }

    fun extractJsonSubstring(text: String): String {
        val trimmed = text.trim()
        val fenceStart = trimmed.indexOf("```json")
        if (fenceStart != -1) {
            val contentStart = fenceStart + 7
            val fenceEnd = trimmed.indexOf("```", contentStart)
            if (fenceEnd != -1) {
                return trimmed.substring(contentStart, fenceEnd).trim()
            }
        }
        val altFenceStart = trimmed.indexOf("```")
        if (altFenceStart != -1) {
            val contentStart = altFenceStart + 3
            val fenceEnd = trimmed.indexOf("```", contentStart)
            if (fenceEnd != -1) {
                val candidate = trimmed.substring(contentStart, fenceEnd).trim()
                if (candidate.startsWith("{")) return candidate
            }
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    private fun extractChatCompletionContent(responseBody: String): String {
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.getJSONObject(0)
            val msg = first.optJSONObject("message")
            if (msg != null) {
                return msg.optString("content", "")
            }
            val text = first.optString("text", "")
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun extractGeminiContent(responseBody: String): String {
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                return parts.getJSONObject(0).optString("text", "")
            }
        }
        return ""
    }

    private fun parseModelsList(jsonString: String): List<String> {
        val models = mutableListOf<String>()
        try {
            val json = JSONObject(jsonString)
            val data = json.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i)
                    val id = item?.optString("id")
                    if (!id.isNullOrBlank()) {
                        models.add(id)
                    }
                }
            } else {
                // Ollama format: "models": [{"name": "..."}]
                val ollamaModels = json.optJSONArray("models")
                if (ollamaModels != null) {
                    for (i in 0 until ollamaModels.length()) {
                        val item = ollamaModels.optJSONObject(i)
                        val name = item?.optString("name")
                        if (!name.isNullOrBlank()) {
                            models.add(name)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return models
    }

    private fun parseCustomHeaders(jsonString: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        try {
            if (jsonString.isNotBlank() && jsonString.startsWith("{")) {
                val json = JSONObject(jsonString)
                for (key in json.keys()) {
                    headers[key] = json.getString(key)
                }
            }
        } catch (_: Exception) {}
        return headers
    }

    private fun fileToBase64(file: File): String {
        // Safe sampled decode to avoid OOM on low-end devices with high-res camera photos
        val targetDim = 800
        val bitmap = com.example.service.perf.PerformanceManager.decodeSampledBitmapFromFile(
            file = file,
            targetWidth = targetDim,
            targetHeight = targetDim,
            preferRgb565 = true
        ) ?: return ""

        val ratio = Math.min(
            targetDim.toFloat() / bitmap.width,
            targetDim.toFloat() / bitmap.height
        )
        val scaled = if (ratio < 1.0f) {
            val res = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, (bitmap.width * ratio).toInt()),
                Math.max(1, (bitmap.height * ratio).toInt()),
                true
            )
            if (res != bitmap) {
                bitmap.recycle()
            }
            res
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        if (scaled != bitmap) {
            scaled.recycle()
        }
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun extractOcrText(
        imageFile: File?,
        provider: CustomCloudProvider,
        geminiApiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (imageFile == null || !imageFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Image file does not exist"))
        }

        try {
            val imageBase64 = fileToBase64(imageFile)
            val ocrPrompt = "Perform high-accuracy optical character recognition (OCR) on this image. Extract and transcribe ALL text, numbers, codes, labels, dates, and links verbatim in order of appearance. Do not add markdown fences, intros, or summaries. Output purely the transcribed text."

            if (provider.isDefaultGemini) {
                val effectiveModel = provider.selectedModel.ifBlank { "gemini-2.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$geminiApiKey"

                val root = JSONObject().apply {
                    val contents = JSONArray()
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray()
                        parts.put(JSONObject().apply { put("text", ocrPrompt) })
                        parts.put(JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", imageBase64)
                            }
                            put("inline_data", inlineData)
                        })
                        put("parts", parts)
                    }
                    contents.put(contentObj)
                    put("contents", contents)
                }

                val requestBody = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("Gemini OCR error: ${response.code}"))
                    }
                    val text = extractGeminiContent(body).trim()
                    Result.success(text)
                }
            } else {
                val chatUrl = buildChatCompletionsUrl(provider.baseUrl)
                val requestJson = JSONObject().apply {
                    put("model", provider.selectedModel.ifBlank { "gpt-4o-mini" })
                    val messages = JSONArray()
                    val userMsg = JSONObject().apply {
                        put("role", "user")
                        val contentArray = JSONArray()
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", ocrPrompt)
                        })
                        contentArray.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            })
                        })
                        put("content", contentArray)
                    }
                    messages.put(userMsg)
                    put("messages", messages)
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder().url(chatUrl).post(requestBody)
                if (provider.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
                }
                parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                    requestBuilder.addHeader(k, v)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("Custom Provider OCR error: ${response.code}"))
                    }
                    val text = extractChatCompletionContent(body).trim()
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOcrToAi(
        ocrText: String,
        provider: CustomCloudProvider,
        geminiApiKey: String = ""
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val prompt = """
Analyze this extracted OCR text transcribed from an image or screenshot:

---
$ocrText
---

Return ONLY a valid JSON object with the following fields:
{
  "title": "A concise, informative title based on the OCR content (3-6 words)",
  "description": "Clear 1-2 sentence description explaining the key takeaways and context from the text",
  "tags": ["relevant", "searchable", "keywords"],
  "links": ["any phone numbers, URLs, addresses, or emails found in the text"],
  "suggestedCollection": "One matching category from: Work & Receipts, Code & Dev, Design Inspiration, Travel & Tickets, Social & Chat, or Personal",
  "exifUserComment": "OCR Analysis: Concise summary suitable for embedding directly into image EXIF metadata",
  "ocrText": ${JSONObject.quote(ocrText)}
}
Do not include any prose outside the JSON.
""".trimIndent()

        try {
            if (provider.isDefaultGemini) {
                val effectiveModel = provider.selectedModel.ifBlank { "gemini-2.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$geminiApiKey"

                val root = JSONObject().apply {
                    val contents = JSONArray()
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray()
                        parts.put(JSONObject().apply { put("text", prompt) })
                        put("parts", parts)
                    }
                    contents.put(contentObj)
                    put("contents", contents)
                }

                val requestBody = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext AiAnalysisResult(
                            title = "OCR AI Error",
                            description = "Server returned ${response.code}: $body",
                            tags = listOf("Error"),
                            modelUsed = effectiveModel,
                            processingTimeMs = elapsed,
                            isSuccess = false,
                            errorMessage = "Gemini API error: ${response.code}"
                        )
                    }
                    val text = extractGeminiContent(body)
                    parseAiJsonOutput(text, effectiveModel, elapsed)
                }
            } else {
                val chatUrl = buildChatCompletionsUrl(provider.baseUrl)
                val requestJson = JSONObject().apply {
                    put("model", provider.selectedModel.ifBlank { "gpt-4o-mini" })
                    val messages = JSONArray()
                    val userMsg = JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                    messages.put(userMsg)
                    put("messages", messages)
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder().url(chatUrl).post(requestBody)
                if (provider.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
                }
                parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                    requestBuilder.addHeader(k, v)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext AiAnalysisResult(
                            title = "OCR AI Error",
                            description = "Server returned ${response.code}: $body",
                            tags = listOf("Error"),
                            modelUsed = provider.selectedModel,
                            processingTimeMs = elapsed,
                            isSuccess = false,
                            errorMessage = "API error: ${response.code}"
                        )
                    }
                    val text = extractChatCompletionContent(body)
                    parseAiJsonOutput(text, provider.selectedModel, elapsed)
                }
            }
        } catch (e: Exception) {
            AiAnalysisResult(
                title = "Analysis Error",
                description = "Failed to analyze OCR with AI: ${e.localizedMessage}",
                tags = listOf("Error"),
                modelUsed = provider.selectedModel,
                processingTimeMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun buildAnalysisPrompt(): String {
        return """
Analyze this screenshot/image with high precision for cataloging, search indexing, and EXIF embedding.
Return ONLY a valid JSON object with the following fields:
{
  "title": "A concise, informative title (3-6 words)",
  "description": "Clear 1-2 sentence description explaining what is shown, context, and key takeaway",
  "ocrText": "Verbatim transcript of any readable text, labels, numbers, or code in the image",
  "tags": ["relevant", "searchable", "keywords"],
  "links": ["any phone numbers, URLs, addresses detected in text"],
  "suggestedCollection": "One matching category from: Work & Receipts, Code & Dev, Design Inspiration, Travel & Tickets, Social & Chat, or Personal",
  "exifUserComment": "Compact summary string suitable for embedding directly into EXIF UserComment header"
}
Do not include any prose outside the JSON.
""".trimIndent()
    }
}
