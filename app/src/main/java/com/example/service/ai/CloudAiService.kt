package com.amresalehin.emreshots.service.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.amresalehin.emreshots.data.model.CustomCloudProvider
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

        fun isGeminiProvider(provider: CustomCloudProvider): Boolean {
            return provider.isDefaultGemini ||
                    provider.baseUrl.contains("generativelanguage.googleapis.com") ||
                    provider.name.contains("Gemini", ignoreCase = true)
        }

        fun isOllamaProvider(provider: CustomCloudProvider): Boolean {
            return provider.baseUrl.contains(":11434") ||
                    provider.name.contains("Ollama", ignoreCase = true)
        }

        fun isAnthropicProvider(provider: CustomCloudProvider): Boolean {
            return provider.baseUrl.contains("anthropic.com") ||
                    provider.name.contains("Anthropic", ignoreCase = true) ||
                    provider.name.contains("Claude", ignoreCase = true)
        }

        fun getCuratedModels(provider: CustomCloudProvider): List<String> {
            val name = provider.name.lowercase()
            val url = provider.baseUrl.lowercase()
            return when {
                isGeminiProvider(provider) -> listOf(
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-1.5-flash",
                    "gemini-1.5-pro",
                    "gemini-2.0-flash"
                )
                isOllamaProvider(provider) -> listOf(
                    "llama3.2-vision",
                    "llava",
                    "bakllava",
                    "minicpm-v",
                    "qwen2-vl",
                    "llama3.2",
                    "mistral"
                )
                url.contains("groq.com") || name.contains("groq") -> listOf(
                    "llama-3.2-11b-vision-preview",
                    "llama-3.2-90b-vision-preview",
                    "llama-3.3-70b-versatile",
                    "mixtral-8x7b-32768"
                )
                url.contains("openrouter.ai") || name.contains("openrouter") -> listOf(
                    "google/gemini-2.5-flash",
                    "openai/gpt-4o-mini",
                    "anthropic/claude-3.5-sonnet",
                    "meta-llama/llama-3.2-11b-vision-instruct",
                    "deepseek/deepseek-chat"
                )
                url.contains("deepseek.com") || name.contains("deepseek") -> listOf(
                    "deepseek-chat",
                    "deepseek-reasoner"
                )
                isAnthropicProvider(provider) -> listOf(
                    "claude-3-5-sonnet-20241022",
                    "claude-3-5-haiku-20241022",
                    "claude-3-opus-20240229"
                )
                url.contains("openai.com") || name.contains("openai") || name.contains("gpt") -> listOf(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "gpt-4-turbo",
                    "gpt-3.5-turbo"
                )
                else -> listOf(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "gemini-2.5-flash",
                    "llama3.2-vision"
                )
            }
        }

        fun buildModelsUrl(baseUrl: String): String {
            val normalized = normalizeBaseUrl(baseUrl)
            if (normalized.contains("generativelanguage.googleapis.com")) {
                return "$normalized/v1beta/models"
            }
            return if (normalized.endsWith("/v1") || normalized.endsWith("/v1beta")) {
                "$normalized/models"
            } else if (normalized.endsWith("/models")) {
                normalized
            } else if (normalized.contains("/v1/")) {
                "$normalized/models"
            } else {
                "$normalized/v1/models"
            }
        }

        fun buildChatCompletionsUrl(baseUrl: String): String {
            val normalized = normalizeBaseUrl(baseUrl)
            return if (normalized.endsWith("/v1") || normalized.endsWith("/v1beta")) {
                "$normalized/chat/completions"
            } else if (normalized.contains("/v1/")) {
                "$normalized/chat/completions"
            } else {
                "$normalized/v1/chat/completions"
            }
        }

        fun parseErrorMessage(body: String): String {
            try {
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    val errObj = json.optJSONObject("error")
                    if (errObj != null) {
                        val msg = errObj.optString("message", "")
                        if (msg.isNotBlank()) return msg
                    }
                    val directMsg = json.optString("message", "")
                    if (directMsg.isNotBlank()) return directMsg
                    val errStr = json.optString("error", "")
                    if (errStr.isNotBlank()) return errStr
                }
            } catch (_: Exception) {}
            return body.take(150).replace("\n", " ").trim()
        }
    }

    suspend fun testConnection(
        provider: CustomCloudProvider,
        fallbackGeminiKey: String = ""
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val result = fetchModels(provider, fallbackGeminiKey)
        if (result.isSuccess) {
            ConnectionTestResult(
                isSuccess = true,
                latencyMs = result.latencyMs,
                statusCode = 200,
                message = result.message,
                availableModels = result.models
            )
        } else {
            ConnectionTestResult(
                isSuccess = false,
                latencyMs = result.latencyMs,
                statusCode = 400,
                message = result.message,
                availableModels = result.suggestedModels
            )
        }
    }

    suspend fun fetchModels(
        provider: CustomCloudProvider,
        fallbackGeminiKey: String = ""
    ): FetchModelsResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val curated = getCuratedModels(provider)

        try {
            if (isGeminiProvider(provider)) {
                val effectiveKey = provider.apiKey.ifBlank { fallbackGeminiKey }.trim()
                if (effectiveKey.isBlank()) {
                    return@withContext FetchModelsResult(
                        isSuccess = false,
                        models = emptyList(),
                        message = "Google Gemini requires an API key. Please enter your API key in the provider settings.",
                        suggestedModels = curated,
                        latencyMs = 0L
                    )
                }
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$effectiveKey"
                val requestBuilder = Request.Builder().url(url).get()
                requestBuilder.addHeader("x-goog-api-key", effectiveKey)
                parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                    requestBuilder.addHeader(k, v)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val parsed = parseModelsList(body).filter { modelId ->
                            !modelId.contains("embedding", ignoreCase = true) &&
                            !modelId.contains("aqa", ignoreCase = true) &&
                            !modelId.contains("imagen", ignoreCase = true) &&
                            !modelId.contains("bison", ignoreCase = true)
                        }
                        val sorted = parsed.sortedWith(compareByDescending<String> {
                            when {
                                it.contains("2.5") -> 5
                                it.contains("2.0") -> 4
                                it.contains("1.5") && it.contains("flash") -> 3
                                it.contains("1.5") -> 2
                                it.contains("flash") -> 1
                                else -> 0
                            }
                        }.thenBy { it })
                        val finalModels = if (sorted.isNotEmpty()) sorted else curated
                        return@withContext FetchModelsResult(
                            isSuccess = true,
                            models = finalModels,
                            message = "Discovered ${finalModels.size} models from Google Gemini (${latency}ms)",
                            suggestedModels = curated,
                            latencyMs = latency
                        )
                    } else {
                        val errorMsg = parseErrorMessage(body)
                        return@withContext FetchModelsResult(
                            isSuccess = false,
                            models = emptyList(),
                            message = "Gemini API error (${response.code}): $errorMsg",
                            suggestedModels = curated,
                            latencyMs = latency
                        )
                    }
                }
            }

            // Ollama native check (supports /api/tags, /v1/models, /models)
            if (isOllamaProvider(provider)) {
                val normalized = normalizeBaseUrl(provider.baseUrl)
                val cleanBase = if (normalized.endsWith("/v1")) normalized.removeSuffix("/v1") else normalized
                val urlsToTry = listOf("$cleanBase/api/tags", "$cleanBase/v1/models", "$cleanBase/models")
                var lastError = ""
                for (targetUrl in urlsToTry) {
                    try {
                        val req = Request.Builder().url(targetUrl).get().build()
                        client.newCall(req).execute().use { response ->
                            val latency = System.currentTimeMillis() - startTime
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: ""
                                val parsed = parseModelsList(body).map { it.removeSuffix(":latest") }
                                if (parsed.isNotEmpty()) {
                                    return@withContext FetchModelsResult(
                                        isSuccess = true,
                                        models = parsed,
                                        message = "Discovered ${parsed.size} local models from Ollama (${latency}ms)",
                                        suggestedModels = curated,
                                        latencyMs = latency
                                    )
                                }
                            } else {
                                lastError = "HTTP ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        lastError = e.localizedMessage ?: "Connection refused"
                    }
                }
                val latency = System.currentTimeMillis() - startTime
                return@withContext FetchModelsResult(
                    isSuccess = false,
                    models = emptyList(),
                    message = "Could not fetch Ollama models: $lastError. Make sure Ollama server is running.",
                    suggestedModels = curated,
                    latencyMs = latency
                )
            }

            // Anthropic Claude native check
            if (isAnthropicProvider(provider)) {
                val effectiveKey = provider.apiKey.trim()
                if (effectiveKey.isBlank()) {
                    return@withContext FetchModelsResult(
                        isSuccess = false,
                        models = emptyList(),
                        message = "Anthropic requires an API key to discover models.",
                        suggestedModels = curated,
                        latencyMs = 0L
                    )
                }
                val url = "https://api.anthropic.com/v1/models"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("x-api-key", effectiveKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val parsed = parseModelsList(body)
                        val finalModels = if (parsed.isNotEmpty()) parsed else curated
                        return@withContext FetchModelsResult(
                            isSuccess = true,
                            models = finalModels,
                            message = "Discovered ${finalModels.size} models from Anthropic (${latency}ms)",
                            suggestedModels = curated,
                            latencyMs = latency
                        )
                    } else {
                        val errorMsg = parseErrorMessage(body)
                        return@withContext FetchModelsResult(
                            isSuccess = false,
                            models = emptyList(),
                            message = "Anthropic API error (${response.code}): $errorMsg",
                            suggestedModels = curated,
                            latencyMs = latency
                        )
                    }
                }
            }

            // Standard OpenAI-compatible endpoints (OpenAI, Groq, OpenRouter, DeepSeek, Together, LM Studio, etc.)
            val modelsUrl = buildModelsUrl(provider.baseUrl)
            val requestBuilder = Request.Builder().url(modelsUrl).get()
            if (provider.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            }
            if (provider.baseUrl.contains("openrouter.ai")) {
                requestBuilder.addHeader("HTTP-Referer", "https://emreshots.app")
                requestBuilder.addHeader("X-Title", "EmreShots")
            }
            parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val parsed = parseModelsList(body)
                    val sorted = parsed.sortedWith(compareByDescending<String> {
                        it.contains("vision", ignoreCase = true) ||
                        it.contains("flash", ignoreCase = true) ||
                        it.contains("4o", ignoreCase = true) ||
                        it.contains("vl", ignoreCase = true) ||
                        it.contains("claude", ignoreCase = true)
                    }.thenBy { it })

                    val finalModels = if (sorted.isNotEmpty()) sorted else curated
                    return@withContext FetchModelsResult(
                        isSuccess = true,
                        models = finalModels,
                        message = "Discovered ${finalModels.size} models from ${provider.name} (${latency}ms)",
                        suggestedModels = curated,
                        latencyMs = latency
                    )
                } else {
                    val errorMsg = parseErrorMessage(body)
                    return@withContext FetchModelsResult(
                        isSuccess = false,
                        models = emptyList(),
                        message = "Server error (${response.code}): $errorMsg",
                        suggestedModels = curated,
                        latencyMs = latency
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            return@withContext FetchModelsResult(
                isSuccess = false,
                models = emptyList(),
                message = "Failed to connect: $msg",
                suggestedModels = curated,
                latencyMs = latency
            )
        }
    }

    suspend fun fetchModelsList(provider: CustomCloudProvider, fallbackGeminiKey: String = ""): List<String> {
        val result = fetchModels(provider, fallbackGeminiKey)
        return if (result.isSuccess && result.models.isNotEmpty()) result.models else result.suggestedModels
    }

    suspend fun analyzeScreenshot(
        imageFile: File?,
        provider: CustomCloudProvider,
        geminiApiKey: String = "",
        qualityPreset: String = "Balanced"
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val targetDimension = when (qualityPreset) {
                "Fast" -> 640
                "Deep" -> 1200
                else -> 800
            }
            val imageBase64 = imageFile?.let { fileToBase64(it, targetDimension) }

            if (isGeminiProvider(provider)) {
                val effectiveKey = provider.apiKey.ifBlank { geminiApiKey }.trim()
                if (effectiveKey.isBlank()) {
                    return@withContext AiAnalysisResult(
                        title = "API Key Missing",
                        description = "Google Gemini API key is missing. Please enter your API key in Settings.",
                        tags = listOf("Error"),
                        modelUsed = provider.selectedModel,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        isSuccess = false,
                        errorMessage = "Gemini API key is required"
                    )
                }
                analyzeWithGemini(imageBase64, provider.selectedModel, effectiveKey, startTime)
            } else if (isAnthropicProvider(provider)) {
                analyzeWithAnthropic(imageBase64, provider, startTime)
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

    private fun analyzeWithAnthropic(
        imageBase64: String?,
        provider: CustomCloudProvider,
        startTime: Long
    ): AiAnalysisResult {
        val effectiveModel = provider.selectedModel.ifBlank { "claude-3-5-sonnet-20241022" }
        val prompt = buildAnalysisPrompt()
        val url = if (provider.baseUrl.endsWith("/v1/messages")) provider.baseUrl
        else if (provider.baseUrl.endsWith("/v1")) "${provider.baseUrl}/messages"
        else "${normalizeBaseUrl(provider.baseUrl)}/v1/messages"

        val root = JSONObject().apply {
            put("model", effectiveModel)
            put("max_tokens", 1024)
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
                        put("type", "image")
                        val sourceObj = JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", imageBase64)
                        }
                        put("source", sourceObj)
                    }
                    contentArray.put(imageContent)
                }

                put("content", contentArray)
            }
            messages.put(userMsg)
            put("messages", messages)
        }

        val requestBody = root.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("x-api-key", provider.apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")

        parseCustomHeaders(provider.customHeadersJson).forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val elapsed = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(body)
                return AiAnalysisResult(
                    title = "Anthropic Error",
                    description = "Error ${response.code}: $errorMsg",
                    tags = listOf("Error"),
                    modelUsed = effectiveModel,
                    processingTimeMs = elapsed,
                    isSuccess = false,
                    errorMessage = "Anthropic API error (${response.code}): $errorMsg"
                )
            }

            val text = extractClaudeContent(body)
            return parseAiJsonOutput(text, effectiveModel, elapsed)
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

    private fun extractClaudeContent(responseBody: String): String {
        try {
            val json = JSONObject(responseBody)
            val contentArr = json.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until contentArr.length()) {
                    val block = contentArr.optJSONObject(i)
                    if (block != null && block.optString("type") == "text") {
                        sb.append(block.optString("text"))
                    }
                }
                if (sb.isNotEmpty()) return sb.toString()
            }
        } catch (_: Exception) {}
        return ""
    }

    fun parseModelsList(jsonString: String): List<String> {
        val models = mutableListOf<String>()
        val trimmed = jsonString.trim()
        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    if (item != null) {
                        val id = item.optString("id").ifBlank { item.optString("name").ifBlank { item.optString("model") } }
                        val cleanId = id.removePrefix("models/")
                        if (cleanId.isNotBlank() && !models.contains(cleanId)) {
                            models.add(cleanId)
                        }
                    } else {
                        val str = array.optString(i).removePrefix("models/")
                        if (str.isNotBlank() && !models.contains(str)) {
                            models.add(str)
                        }
                    }
                }
                return models
            }

            val json = JSONObject(trimmed)
            val data = json.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i)
                    if (item != null) {
                        val id = item.optString("id").ifBlank { item.optString("name").ifBlank { item.optString("model") } }
                        val cleanId = id.removePrefix("models/")
                        if (cleanId.isNotBlank() && !models.contains(cleanId)) {
                            models.add(cleanId)
                        }
                    } else {
                        val str = data.optString(i).removePrefix("models/")
                        if (str.isNotBlank() && !models.contains(str)) {
                            models.add(str)
                        }
                    }
                }
            }

            // Ollama or Gemini format: "models": [...]
            val modelsArray = json.optJSONArray("models")
            if (modelsArray != null) {
                for (i in 0 until modelsArray.length()) {
                    val item = modelsArray.optJSONObject(i)
                    if (item != null) {
                        val rawName = item.optString("name").ifBlank { item.optString("id").ifBlank { item.optString("model") } }
                        val cleanName = rawName.removePrefix("models/")
                        if (cleanName.isNotBlank() && !models.contains(cleanName)) {
                            models.add(cleanName)
                        }
                    } else {
                        val str = modelsArray.optString(i).removePrefix("models/")
                        if (str.isNotBlank() && !models.contains(str)) {
                            models.add(str)
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

    private fun fileToBase64(file: File, targetDim: Int): String {
        // Safe sampled decode to avoid OOM on low-end devices with high-res camera photos.
        val bitmap = com.amresalehin.emreshots.service.perf.PerformanceManager.decodeSampledBitmapFromFile(
            file = file,
            targetWidth = targetDim,
            targetHeight = targetDim,
            preferRgb565 = true
        ) ?: return ""

        try {
            val ratio = Math.min(
                targetDim.toFloat() / bitmap.width,
                targetDim.toFloat() / bitmap.height
            )
            val scaled = if (ratio < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, (bitmap.width * ratio).toInt()),
                    Math.max(1, (bitmap.height * ratio).toInt()),
                    true
                )
            } else {
                bitmap
            }

            try {
                val outputStream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val bytes = outputStream.toByteArray()
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                if (scaled !== bitmap && !scaled.isRecycled) {
                    scaled.recycle()
                }
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
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
            val imageBase64 = fileToBase64(imageFile, 800)
            val ocrPrompt = "Perform high-accuracy optical character recognition (OCR) on this image. Extract and transcribe ALL text, numbers, codes, labels, dates, and links verbatim in order of appearance. Do not add markdown fences, intros, or summaries. Output purely the transcribed text."

            val isGemini = isGeminiProvider(provider)
            val effectiveGeminiKey = provider.apiKey.ifBlank { geminiApiKey }.trim()

            if (isGemini) {
                if (effectiveGeminiKey.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Google Gemini API key is required. Please set up your key in Settings."))
                }
                val effectiveModel = provider.selectedModel.ifBlank { "gemini-2.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$effectiveGeminiKey"

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
            val isGemini = isGeminiProvider(provider)
            val effectiveGeminiKey = provider.apiKey.ifBlank { geminiApiKey }.trim()

            if (isGemini) {
                if (effectiveGeminiKey.isBlank()) {
                    return@withContext AiAnalysisResult(
                        title = "API Key Missing",
                        description = "Google Gemini API key is missing. Please set up your key in Settings.",
                        tags = listOf("Error"),
                        modelUsed = provider.selectedModel,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        isSuccess = false,
                        errorMessage = "Gemini API key is required"
                    )
                }
                val effectiveModel = provider.selectedModel.ifBlank { "gemini-2.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$effectiveGeminiKey"

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
