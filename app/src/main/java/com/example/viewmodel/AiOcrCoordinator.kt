package com.amresalehin.emreshots.viewmodel

import android.app.Application
import android.net.Uri
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import com.amresalehin.emreshots.service.ai.AiAnalysisResult
import com.amresalehin.emreshots.service.ai.CloudAiService
import com.amresalehin.emreshots.service.ai.OnDeviceVisionService
import com.amresalehin.emreshots.service.exif.ExifMetadataManager
import com.amresalehin.emreshots.service.ocr.LocalOcrService
import com.amresalehin.emreshots.service.ocr.TessLanguage
import com.amresalehin.emreshots.service.ocr.OcrArtefactLlmFixer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AiOcrCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val screenshotRepository: ScreenshotRepository,
    private val allScreenshots: StateFlow<List<ScreenshotItem>>,
    private val collections: StateFlow<List<CollectionItem>>,
    private val providers: StateFlow<List<CustomCloudProvider>>,
    private val activeProvider: StateFlow<CustomCloudProvider?>,
    private val ocrEnabled: StateFlow<Boolean>,
    private val smartTagsEnabled: StateFlow<Boolean>,
    private val linksDetectionEnabled: StateFlow<Boolean>,
    private val aiQualityPreset: StateFlow<String>,
    private val onDeviceVisionMode: StateFlow<String>,
    private val onDeviceVisionModel: StateFlow<String>,
    private val visionCaptionTagProviderId: StateFlow<String>,
    private val ocrLanguage: StateFlow<String>,
    private val autoWriteExifSetting: StateFlow<Boolean>,
    private val exifDataStore: MutableStateFlow<Map<String, ExifData>>,
    private val exifManager: ExifMetadataManager,
    private val aiService: CloudAiService,
    private val localOcrService: LocalOcrService,
    private val ocrArtefactLlmFixer: OcrArtefactLlmFixer,
    private val onMessage: (String) -> Unit
) {
    private val _snackbarMessage = object {
        var value: String? = null
            set(newValue) {
                field = newValue
                if (!newValue.isNullOrBlank()) onMessage(newValue)
            }
    }

    private val onDeviceVisionService = OnDeviceVisionService(application)
    private val visionAnalysisMutex = Mutex()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    private val _lastFailedScreenshotIds = MutableStateFlow<Set<String>>(emptySet())
    val lastFailedScreenshotIds: StateFlow<Set<String>> = _lastFailedScreenshotIds.asStateFlow()
    private val _analysisStatusText = MutableStateFlow<String?>(null)
    val analysisStatusText: StateFlow<String?> = _analysisStatusText.asStateFlow()
    private val _indexingState = MutableStateFlow(IndexingState())
    val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()
    private var indexingJob: Job? = null
    val exifDataState: StateFlow<Map<String, ExifData>> = exifDataStore.asStateFlow()
    private val _isExtractingOcr = MutableStateFlow(false)
    val isExtractingOcr: StateFlow<Boolean> = _isExtractingOcr.asStateFlow()
    private val _ocrStatusText = MutableStateFlow<String?>(null)
    val ocrStatusText: StateFlow<String?> = _ocrStatusText.asStateFlow()

    private fun resolveImageFile(screenshot: ScreenshotItem): File? {
        val direct = File(screenshot.filePath)
        if (direct.exists() && direct.canRead() && direct.length() > 0) {
            return direct
        }
        if (!screenshot.uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(screenshot.uriString)
                val context = application
                val cacheDir = File(context.cacheDir, "ai_media_cache").apply { mkdirs() }
                val safeId = screenshot.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val cacheFile = File(cacheDir, "cached_${safeId}.jpg")
                if (cacheFile.exists() && cacheFile.length() > 0 && cacheFile.lastModified() >= screenshot.addedOn) {
                    return cacheFile
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    return cacheFile
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private suspend fun analyzeScreenshotInternal(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean,
        forcedMode: String? = null,
        forcedProviderSelection: String? = null
    ): AiAnalysisResult = visionAnalysisMutex.withLock {
        val mode = forcedMode ?: onDeviceVisionMode.value
        val forceLocal = mode.equals("FORCE_LOCAL", ignoreCase = true)
        val forceCloud = mode.equals("DISABLED", ignoreCase = true)
        val file = resolveImageFile(screenshot)

        if (mode.equals("FORCE_LOCAL", ignoreCase = true) && file == null) {
            return AiAnalysisResult(isSuccess = false, errorMessage = "Local-only analysis could not access the screenshot.")
        }

        val selection = forcedProviderSelection ?: visionCaptionTagProviderId.value
        val localId = selection.removePrefix("local:")
        val cloudId = selection.removePrefix("cloud:")

        var result: AiAnalysisResult? = null

        // Local vision is a first-class producer of the same persisted metadata as cloud vision.
        if ((forceLocal || (selection.isBlank() && !forceCloud)) && file != null) {
            val quality = when (aiQualityPreset.value.lowercase()) {
                "fast" -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.FAST
                "deep" -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.DEEP
                else -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.BALANCED
            }
            val local = onDeviceVisionService.analyze(
                request = com.amresalehin.emreshots.service.ai.VisionAnalysisRequest(
                    imagePath = file.absolutePath,
                    quality = quality,
                    ocrText = if (ocrEnabled.value) screenshot.ocrText else null,
                    requestedCapabilities = com.amresalehin.emreshots.service.ai.OnDeviceVisionCapability.entries.toSet()
                ),
                modePreference = mode,
                modelPreference = if (selection.startsWith("local:")) localId else onDeviceVisionModel.value
            )
            if (local.isSuccess) {
                result = AiAnalysisResult(
                    title = local.title,
                    description = local.description,
                    tags = local.tags,
                    detectedLinks = local.detectedLinks,
                    ocrText = null,
                    modelUsed = local.modelUsed,
                    processingTimeMs = local.processingTimeMs,
                    isSuccess = true
                )
            } else if (forceLocal) {
                return AiAnalysisResult(
                    isSuccess = false,
                    errorMessage = local.errorMessage ?: "Local vision inference failed.",
                    modelUsed = local.modelUsed,
                    processingTimeMs = local.processingTimeMs
                )
            }
        }

        // Explicit local provider selection must never fall through to a cloud provider.
        if (result == null && selection.startsWith("local:") && !forceCloud) {
            if (file == null) {
                return AiAnalysisResult(isSuccess = false, errorMessage = "Local VLM needs access to the image file.")
            }
            val local = onDeviceVisionService.analyze(
                request = com.amresalehin.emreshots.service.ai.VisionAnalysisRequest(
                    imagePath = file.absolutePath,
                    quality = when (aiQualityPreset.value.lowercase()) {
                        "fast" -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.FAST
                        "deep" -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.DEEP
                        else -> com.amresalehin.emreshots.service.ai.VisionQualityPreset.BALANCED
                    },
                    ocrText = if (ocrEnabled.value) screenshot.ocrText else null,
                    requestedCapabilities = com.amresalehin.emreshots.service.ai.OnDeviceVisionCapability.entries.toSet()
                ),
                modePreference = "FORCE_LOCAL",
                modelPreference = localId
            )
            if (!local.isSuccess) {
                return AiAnalysisResult(
                    isSuccess = false,
                    errorMessage = local.errorMessage ?: "Local vision inference failed.",
                    modelUsed = local.modelUsed,
                    processingTimeMs = local.processingTimeMs
                )
            }
            result = AiAnalysisResult(
                title = local.title,
                description = local.description,
                tags = local.tags,
                detectedLinks = local.detectedLinks,
                ocrText = null,
                modelUsed = local.modelUsed,
                processingTimeMs = local.processingTimeMs,
                isSuccess = true
            )
        }

        if (result == null) {
            val provider = if (selection.startsWith("cloud:")) {
                providers.value.firstOrNull { it.id == cloudId && it.apiKey.isNotBlank() }
            } else {
                activeProvider.value
            } ?: return AiAnalysisResult(
                isSuccess = false,
                errorMessage = "No AI provider configured for Captioning & Tagging. Choose an offline VLM or add a custom endpoint in Settings."
            )

            result = aiService.analyzeScreenshot(
                imageFile = file,
                provider = provider,
                geminiApiKey = provider.apiKey,
                qualityPreset = aiQualityPreset.value
            )
        }

        val finalResult = result ?: return AiAnalysisResult(
            isSuccess = false,
            errorMessage = "No vision analysis result was produced."
        )

        if (finalResult.isSuccess) {
            // Re-read after inference so OCR or other edits made while inference was running are never lost.
            val latestScreenshot = screenshotRepository.getScreenshotSync(screenshot.id) ?: screenshot
            val matchedCol = collections.value.find {
                it.name.equals(finalResult.suggestedCollection, ignoreCase = true)
            }
            val newColIds = if (matchedCol != null && !latestScreenshot.collectionIds.contains(matchedCol.id)) {
                latestScreenshot.collectionIds + matchedCol.id
            } else {
                latestScreenshot.collectionIds
            }
            var updatedScreenshot = latestScreenshot.copy(
                title = if (finalResult.title.isNotBlank()) finalResult.title else latestScreenshot.title,
                description = if (finalResult.description.isNotBlank()) finalResult.description else latestScreenshot.description,
                // Canonical OCR is owned by the Tesseract + local text-LLM pipeline.
                ocrText = latestScreenshot.ocrText,
                tags = if (smartTagsEnabled.value) (latestScreenshot.tags + finalResult.tags).distinct() else latestScreenshot.tags,
                links = if (linksDetectionEnabled.value) (latestScreenshot.links + finalResult.detectedLinks).distinct() else latestScreenshot.links,
                collectionIds = newColIds,
                aiProcessed = true,
                aiModelUsed = finalResult.modelUsed
            )

            if (autoWriteExif) {
                val aiExifResult = exifManager.applyAiMetadataToExifSafe(
                    context = application,
                    screenshot = updatedScreenshot,
                    title = updatedScreenshot.title,
                    description = updatedScreenshot.description,
                    tags = updatedScreenshot.tags,
                    modelName = finalResult.modelUsed
                )
                if (aiExifResult.isSuccess) {
                    val (savedFile, exif) = aiExifResult.getOrThrow()
                    updatedScreenshot = updatedScreenshot.copy(filePath = savedFile.absolutePath)
                    val current = exifDataStore.value.toMutableMap()
                    current[screenshot.id] = exif
                    exifDataStore.value = current
                }
            }

            screenshotRepository.update(updatedScreenshot)
        }

        return finalResult
    }
    fun analyzeLocalVision(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = false,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _analysisStatusText.value = "Analyzing locally — image stays on this device…"
            val result = analyzeScreenshotInternal(
                screenshot = screenshot,
                autoWriteExif = autoWriteExif,
                forcedMode = "FORCE_LOCAL",
                forcedProviderSelection = "local:" + onDeviceVisionModel.value
            )
            _isAnalyzing.value = false
            _analysisStatusText.value = null
            _snackbarMessage.value = if (result.isSuccess) "Local AI analysis complete via " + result.modelUsed + "." else "Local AI failed: " + (result.errorMessage ?: "Unknown error")
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    fun analyzeCloudVision(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = false,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val selectedId = visionCaptionTagProviderId.value.removePrefix("cloud:")
            val provider = providers.value.firstOrNull { it.id == selectedId && it.apiKey.isNotBlank() }
                ?: activeProvider.value
            if (provider == null) {
                val result = AiAnalysisResult(isSuccess = false, errorMessage = "No cloud provider configured.")
                _snackbarMessage.value = result.errorMessage
                withContext(Dispatchers.Main) { onComplete?.invoke(result) }
                return@launch
            }
            _isAnalyzing.value = true
            _analysisStatusText.value = "Cloud Vision — uploading the image…"
            val result = analyzeScreenshotInternal(
                screenshot = screenshot,
                autoWriteExif = autoWriteExif,
                forcedMode = "DISABLED",
                forcedProviderSelection = "cloud:" + provider.id
            )
            _isAnalyzing.value = false
            _analysisStatusText.value = null
            _snackbarMessage.value = if (result.isSuccess) "Cloud Vision complete via " + result.modelUsed + "." else "Cloud Vision failed: " + (result.errorMessage ?: "Unknown error")
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    fun analyzeScreenshot(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = autoWriteExifSetting.value,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _analysisStatusText.value = if (onDeviceVisionMode.value.equals("FORCE_LOCAL", ignoreCase = true)) "Analyzing locally…" else "Analyzing…"

            val result = analyzeScreenshotInternal(screenshot, autoWriteExif)

            _snackbarMessage.value = if (result.isSuccess) {
                "AI analysis complete via ${result.modelUsed} (${result.processingTimeMs}ms)."
            } else {
                "AI analysis failed: ${result.errorMessage ?: "Unknown error"}"
            }

            _isAnalyzing.value = false
            _analysisStatusText.value = null
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        }
    }
    fun extractOcr(screenshot: ScreenshotItem, onComplete: ((String?) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            _isExtractingOcr.value = true
            val langLabel = TessLanguage.findByCode(ocrLanguage.value).englishName
            val engineLabel = "Tesseract ($langLabel)"
            _ocrStatusText.value = "Extracting text using $engineLabel…"

            val file = resolveImageFile(screenshot)
            val result = if (file != null) {
                localOcrService.recognize(file, languageCode = ocrLanguage.value)
            } else {
                Result.failure(IllegalArgumentException("Screenshot image is not accessible."))
            }

            _isExtractingOcr.value = false
            _ocrStatusText.value = null

            if (result.isSuccess) {
                val rawOcr = result.getOrNull().orEmpty()
                _ocrStatusText.value = "Local AI: repairing text & generating tags (PixelShot)…"

                // Directly sent to local text-only LLM (PixelShot pipeline)
                val aiResult = ocrArtefactLlmFixer.processOcrWithLocalAi(rawOcr)
                val mergedTags = (screenshot.tags + aiResult.tags).distinct().filter { it.isNotBlank() }
                val mergedLinks = (screenshot.links + aiResult.detectedLinks).distinct().filter { it.isNotBlank() }
                val newTitle = if (screenshot.title.isBlank() || screenshot.title.startsWith("Screenshot_") || screenshot.title.startsWith("IMG_")) {
                    aiResult.title ?: screenshot.title
                } else screenshot.title

                val updated = screenshot.copy(
                    ocrText = aiResult.fixedOcrText,
                    tags = mergedTags,
                    links = mergedLinks,
                    title = newTitle,
                    aiProcessed = true,
                    aiModelUsed = "Local LLM"
                )
                screenshotRepository.update(updated)

                _isExtractingOcr.value = false
                _ocrStatusText.value = null
                _snackbarMessage.value = "$engineLabel + Local AI: text cleaned & ${aiResult.tags.size} tags assigned."
                withContext(Dispatchers.Main) { onComplete?.invoke(aiResult.fixedOcrText) }
            } else {
                _isExtractingOcr.value = false
                _ocrStatusText.value = null
                _snackbarMessage.value = "$engineLabel failed: " + (result.exceptionOrNull()?.message ?: "Unknown error")
                withContext(Dispatchers.Main) { onComplete?.invoke(null) }
            }
        }
    }

    /**
     * Runs local OCR and directly routes text through the local small text LLM.
     * Fixes OCR artefacts and generates tags automatically. No cloud or VLM overhead.
     */
    fun batchExtractOcr(items: List<ScreenshotItem>, onlyMissing: Boolean = true) {
        if (isExtractingOcr.value) return
        if (!ocrEnabled.value) {
            _snackbarMessage.value = "OCR is disabled in Settings. Enable it to run text extraction."
            return
        }
        val targets = items.filter { !it.isVideo && (!onlyMissing || it.ocrText.isNullOrBlank()) }
        if (targets.isEmpty()) {
            _snackbarMessage.value = if (onlyMissing) "No media is waiting for local OCR." else "No image media is available for local OCR."
            return
        }

        scope.launch(Dispatchers.IO) {
            _isExtractingOcr.value = true
            var completed = 0
            var failed = 0
            val langLabel = TessLanguage.findByCode(ocrLanguage.value).englishName
            val engineLabel = "Tesseract ($langLabel)"
            try {
                targets.forEachIndexed { index, item ->
                    if (!isActive) return@forEachIndexed
                    _ocrStatusText.value = "$engineLabel (" + (index + 1) + "/" + targets.size + "): " + item.title.ifBlank { "Image " + (index + 1) }
                    val file = resolveImageFile(item)
                    val result = if (file != null) {
                        localOcrService.recognize(file, languageCode = ocrLanguage.value)
                    } else {
                        Result.failure(IllegalArgumentException("Image is not accessible."))
                    }
                    if (result.isSuccess) {
                        val rawOcr = result.getOrNull().orEmpty()
                        val aiResult = ocrArtefactLlmFixer.processOcrWithLocalAi(rawOcr)
                        val mergedTags = (item.tags + aiResult.tags).distinct().filter { it.isNotBlank() }
                        val mergedLinks = (item.links + aiResult.detectedLinks).distinct().filter { it.isNotBlank() }
                        val newTitle = if (item.title.isBlank() || item.title.startsWith("Screenshot_") || item.title.startsWith("IMG_")) {
                            aiResult.title ?: item.title
                        } else item.title

                        val updated = item.copy(
                            ocrText = aiResult.fixedOcrText,
                            tags = mergedTags,
                            links = mergedLinks,
                            title = newTitle,
                            aiProcessed = true,
                            aiModelUsed = "Local LLM"
                        )
                        screenshotRepository.update(updated)
                        completed++
                    } else {
                        failed++
                    }
                }
            } finally {
                _isExtractingOcr.value = false
                _ocrStatusText.value = null
            }
            _snackbarMessage.value = "$engineLabel + Local AI: $completed processed" + (if (failed > 0) ", $failed failed" else "") + ". Text fixed & tags generated."
        }
    }

    fun writeOcrAndAiToMetadata(
        screenshot: ScreenshotItem,
        ocrText: String,
        aiTitle: String,
        aiDescription: String,
        tags: List<String>
    ) {
        scope.launch(Dispatchers.IO) {
            val userComment = "OCR: ${ocrText.take(150)} | Summary: $aiDescription | Tags: ${tags.joinToString(", ")}"
            val currentExif = exifManager.readExif(application, screenshot)
            val updatedExif = currentExif.copy(
                imageDescription = aiTitle.ifBlank { currentExif.imageDescription },
                userComment = userComment
            )

            val writeResult = exifManager.writeExifSafe(application, screenshot, updatedExif)
            if (writeResult.isSuccess) {
                val savedFile = writeResult.getOrThrow()
                val updatedScreenshot = screenshot.copy(
                    filePath = savedFile.absolutePath,
                    title = aiTitle.ifBlank { screenshot.title },
                    description = aiDescription.ifBlank { screenshot.description },
                    ocrText = ocrText
                )
                screenshotRepository.update(updatedScreenshot)

                val newExif = exifManager.readExif(application, updatedScreenshot)
                val current = exifDataStore.value.toMutableMap()
                current[screenshot.id] = newExif
                exifDataStore.value = current

                _snackbarMessage.value = "OCR and AI insights written directly into image EXIF metadata!"
            } else {
                _snackbarMessage.value = "Failed to write metadata: ${writeResult.exceptionOrNull()?.message}"
            }
        }
    }

    fun startIndexing(
        targetScreenshots: List<ScreenshotItem>? = null,
        onlyUnindexed: Boolean = true,
        autoWriteExif: Boolean = autoWriteExifSetting.value
    ) {
        if (_indexingState.value.isIndexing) {
            _snackbarMessage.value = "AI indexing is already in progress."
            return
        }

        val allItems = targetScreenshots ?: allScreenshots.value
        val items = if (onlyUnindexed) allItems.filter { !it.aiProcessed } else allItems

        if (items.isEmpty()) {
            _snackbarMessage.value = if (onlyUnindexed && allItems.isNotEmpty())
                "All ${allItems.size} media items are already indexed with AI!"
            else
                "No media items available to index."
            return
        }

        // Batch indexing can run fully offline when local vision is selected/available.
        // analyzeScreenshotInternal is the single authority for choosing local vs cloud.
        val provider = activeProvider.value
        val localMode = onDeviceVisionMode.value.equals("FORCE_LOCAL", ignoreCase = true) ||
                visionCaptionTagProviderId.value.startsWith("local:") ||
                (visionCaptionTagProviderId.value.isBlank() && onDeviceVisionMode.value.equals("AUTOMATIC", ignoreCase = true))
        if (provider == null && !localMode) {
            _snackbarMessage.value = "No AI provider configured. Choose an offline VLM or add a cloud endpoint in Settings."
            return
        }

        indexingJob?.cancel()
        _lastFailedScreenshotIds.value = emptySet()
        indexingJob = scope.launch(Dispatchers.IO) {
            val total = items.size
            val modelName = if (localMode) {
                onDeviceVisionModel.value.ifBlank { "On-device VLM" }
            } else {
                provider?.selectedModel?.ifBlank { provider.name } ?: "Cloud Vision"
            }
            _indexingState.value = IndexingState(
                isIndexing = true,
                current = 0,
                total = total,
                progress = 0f,
                currentItemTitle = items.first().title.ifBlank { "Starting indexer..." },
                currentModel = modelName,
                successCount = 0,
                failureCount = 0,
                isCancelled = false
            )
            _isAnalyzing.value = true

            var successes = 0
            var failures = 0
            val failedIds = mutableSetOf<String>()

            for ((index, item) in items.withIndex()) {
                if (!isActive) {
                    break
                }

                val itemTitle = item.title.ifBlank { "Media #${index + 1}" }
                _indexingState.value = _indexingState.value.copy(
                    current = index + 1,
                    progress = index.toFloat() / total,
                    currentItemTitle = itemTitle
                )
                _analysisStatusText.value = "Indexing (${index + 1}/$total): $itemTitle"

                val res = analyzeScreenshotInternal(item, autoWriteExif)
                if (res.isSuccess) {
                    successes++
                } else {
                    failures++
                    failedIds += item.id
                }

                _indexingState.value = _indexingState.value.copy(
                    progress = (index + 1).toFloat() / total,
                    successCount = successes,
                    failureCount = failures
                )
            }

            _lastFailedScreenshotIds.value = failedIds.toSet()
            val wasCancelled = !isActive
            _indexingState.value = _indexingState.value.copy(
                isIndexing = false,
                isCancelled = wasCancelled,
                progress = if (wasCancelled) _indexingState.value.progress else 1f
            )
            _isAnalyzing.value = false
            _analysisStatusText.value = null

            if (wasCancelled) {
                _snackbarMessage.value = "AI indexing cancelled ($successes of $total processed)."
            } else {
                _snackbarMessage.value = "AI Indexing complete: $successes indexed successfully" +
                        if (failures > 0) " ($failures failed)" else "!"
            }
        }
    }

    fun cancelIndexing() {
        indexingJob?.cancel()
        indexingJob = null
        _indexingState.value = _indexingState.value.copy(isIndexing = false, isCancelled = true)
        _isAnalyzing.value = false
        _analysisStatusText.value = null
        _snackbarMessage.value = "AI indexing cancelled"
    }

    fun batchAnalyzeScreenshots(screenshots: List<ScreenshotItem>, autoWriteExif: Boolean = autoWriteExifSetting.value) {
        startIndexing(targetScreenshots = screenshots, onlyUnindexed = false, autoWriteExif = autoWriteExif)
    }

    fun retryFailedItems(autoWriteExif: Boolean = autoWriteExifSetting.value) {
        val failedItems = allScreenshots.value.filter { it.id in _lastFailedScreenshotIds.value }
        if (failedItems.isEmpty()) {
            _snackbarMessage.value = application.getString(R.string.no_failed_items_to_retry)
            return
        }
        startIndexing(targetScreenshots = failedItems, onlyUnindexed = false, autoWriteExif = autoWriteExif)
    }


}
