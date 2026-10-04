package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.seedInitialData
import com.example.data.model.CollectionItem
import com.example.data.model.CustomCloudProvider
import com.example.data.model.ExifData
import com.example.data.model.GalleryViewMode
import com.example.data.model.MediaGroupBy
import com.example.data.model.MediaSortOption
import com.example.data.model.ScreenshotItem
import com.example.data.repository.CollectionRepository
import com.example.data.repository.ProviderRepository
import com.example.data.repository.ScreenshotRepository
import com.example.service.ai.AiAnalysisResult
import com.example.service.ai.CloudAiService
import com.example.service.ai.ConnectionTestResult
import com.example.service.exif.ExifMetadataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ScreenshotFilter(val displayName: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    AI_PROCESSED("AI Processed"),
    HAS_LINKS("With Links"),
    FAVORITES("Favorites"),
    REMINDERS("Reminders")
}

class ScreenshotsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val screenshotRepository = ScreenshotRepository(database.screenshotDao())
    val collectionRepository = CollectionRepository(database.collectionDao())
    val providerRepository = ProviderRepository(database.providerDao())

    val exifManager = ExifMetadataManager()
    val aiService = CloudAiService()
    val mediaScanner = com.example.service.media.DeviceMediaScanner(application)

    private val _hasMediaPermissions = MutableStateFlow(com.example.service.media.DeviceMediaScanner.hasPermissions(application))
    val hasMediaPermissions: StateFlow<Boolean> = _hasMediaPermissions.asStateFlow()

    val allScreenshots: StateFlow<List<ScreenshotItem>> = screenshotRepository.allScreenshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionItem>> = collectionRepository.allCollections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers: StateFlow<List<CustomCloudProvider>> = providerRepository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProvider: StateFlow<CustomCloudProvider?> = providerRepository.activeProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStatusText = MutableStateFlow<String?>(null)
    val analysisStatusText: StateFlow<String?> = _analysisStatusText.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _exifDataState = MutableStateFlow<Map<String, ExifData>>(emptyMap())
    val exifDataState: StateFlow<Map<String, ExifData>> = _exifDataState.asStateFlow()

    // Enhanced Settings Controls
    val ocrEnabled = MutableStateFlow(true)
    val linksDetectionEnabled = MutableStateFlow(true)
    val smartTagsEnabled = MutableStateFlow(true)
    val remindersDetectionEnabled = MutableStateFlow(true)
    val autoSyncDeviceMedia = MutableStateFlow(true)
    val aiQualityPreset = MutableStateFlow("Balanced") // "Fast", "Balanced", "Deep"
    val autoWriteExifSetting = MutableStateFlow(false) // Default to NOT writing directly to metadata
    val isLowEndDevice = com.example.service.perf.PerformanceManager.isLowEndDevice(application)
    val isLowRamDevice = com.example.service.perf.PerformanceManager.isLowRamDevice(application)
    val gridColumns = MutableStateFlow(if (isLowEndDevice) 2 else 2)

    val isExtractingOcr = MutableStateFlow(false)
    val ocrStatusText = MutableStateFlow<String?>(null)

    fun setOcrEnabled(enabled: Boolean) { ocrEnabled.value = enabled }
    fun setLinksDetectionEnabled(enabled: Boolean) { linksDetectionEnabled.value = enabled }
    fun setSmartTagsEnabled(enabled: Boolean) { smartTagsEnabled.value = enabled }
    fun setRemindersDetectionEnabled(enabled: Boolean) { remindersDetectionEnabled.value = enabled }
    fun setAutoSyncDeviceMedia(enabled: Boolean) { autoSyncDeviceMedia.value = enabled }
    fun setAiQualityPreset(preset: String) { aiQualityPreset.value = preset }
    fun setAutoWriteExifSetting(enabled: Boolean) { autoWriteExifSetting.value = enabled }
    fun setGridColumns(cols: Int) { gridColumns.value = cols }

    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                coil.Coil.imageLoader(context).memoryCache?.clear()
                coil.Coil.imageLoader(context).diskCache?.clear()
                _snackbarMessage.value = "Thumbnail and temporary cache cleared!"
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to clear cache: ${e.message}"
            }
        }
    }

    fun trimMemory() {
        val context = getApplication<Application>()
        coil.Coil.imageLoader(context).memoryCache?.clear()
        System.gc()
        _snackbarMessage.value = "Memory trimmed and garbage collected!"
    }

    // Gallery Organization & View Options
    val sortOption = MutableStateFlow(MediaSortOption.NEWEST)
    val groupByOption = MutableStateFlow(MediaGroupBy.NONE)
    val viewMode = MutableStateFlow(GalleryViewMode.GRID)

    fun setSortOption(option: MediaSortOption) { sortOption.value = option }
    fun setGroupByOption(option: MediaGroupBy) { groupByOption.value = option }
    fun setViewMode(mode: GalleryViewMode) { viewMode.value = mode }

    val filteredScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        allScreenshots,
        _searchQuery,
        _selectedFilter
    ) { screenshots, query, filter ->
        var result = screenshots
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { item ->
                item.title.lowercase().contains(q) ||
                        item.description.lowercase().contains(q) ||
                        item.tags.any { it.lowercase().contains(q) } ||
                        (item.notes?.lowercase()?.contains(q) == true) ||
                        item.links.any { it.lowercase().contains(q) }
            }
        }
        when (filter) {
            ScreenshotFilter.ALL -> result
            ScreenshotFilter.PHOTOS -> result.filter { !it.isVideo && !it.tags.contains("Screenshot") }
            ScreenshotFilter.VIDEOS -> result.filter { it.isVideo }
            ScreenshotFilter.SCREENSHOTS -> result.filter { it.tags.contains("Screenshot") || it.title.lowercase().contains("screenshot") }
            ScreenshotFilter.AI_PROCESSED -> result.filter { it.aiProcessed }
            ScreenshotFilter.HAS_LINKS -> result.filter { it.links.isNotEmpty() }
            ScreenshotFilter.FAVORITES -> result.filter { it.isFavorite }
            ScreenshotFilter.REMINDERS -> result.filter { it.reminderTime != null }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        filteredScreenshots,
        sortOption
    ) { items, sort ->
        when (sort) {
            MediaSortOption.NEWEST -> items.sortedByDescending { it.addedOn }
            MediaSortOption.OLDEST -> items.sortedBy { it.addedOn }
            MediaSortOption.TITLE_AZ -> items.sortedBy { it.title.lowercase() }
            MediaSortOption.TITLE_ZA -> items.sortedByDescending { it.title.lowercase() }
            MediaSortOption.SIZE_DESC -> items.sortedByDescending { it.fileSize }
            MediaSortOption.SIZE_ASC -> items.sortedBy { it.fileSize }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedScreenshots: StateFlow<Map<String, List<ScreenshotItem>>> = combine(
        sortedScreenshots,
        groupByOption
    ) { items, groupBy ->
        when (groupBy) {
            MediaGroupBy.NONE -> mapOf("" to items)
            MediaGroupBy.DATE -> items.groupBy { formatDateGroup(it.addedOn) }
            MediaGroupBy.TYPE -> items.groupBy { item ->
                when {
                    item.isVideo -> "Videos"
                    item.tags.contains("Screenshot") || item.title.lowercase().contains("screenshot") -> "Screenshots"
                    else -> "Photos"
                }
            }
            MediaGroupBy.AI_STATUS -> items.groupBy { item ->
                if (item.aiProcessed) "✦ AI Indexed & Tagged" else "Pending AI Analysis"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun formatDateGroup(timestamp: Long): String {
        val now = Calendar.getInstance()
        val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val diffDays = (now.timeInMillis - timestamp) / (1000 * 60 * 60 * 24)

        return when {
            diffDays <= 0L && now.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) -> "Today"
            diffDays <= 1L -> "Yesterday"
            diffDays < 7L -> "This Week"
            diffDays < 30L -> "This Month"
            else -> {
                val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                format.format(Date(timestamp))
            }
        }
    }

    init {
        // Ensure initial seed data is loaded if first run
        viewModelScope.launch(Dispatchers.IO) {
            seedInitialData(database, getApplication())
        }
        checkPermissions()
    }

    fun checkPermissions() {
        _hasMediaPermissions.value = com.example.service.media.DeviceMediaScanner.hasPermissions(getApplication())
    }

    fun onPermissionsResult(granted: Boolean) {
        _hasMediaPermissions.value = granted
        if (granted) {
            _snackbarMessage.value = "Media permissions granted! Syncing device media..."
            syncDeviceMedia()
        } else {
            _snackbarMessage.value = "Media permission denied. You can still pick files individually."
        }
    }

    fun syncDeviceMedia(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            checkPermissions()
            if (!_hasMediaPermissions.value) {
                _snackbarMessage.value = "Please grant photos and videos permissions to auto-sync."
                onComplete?.invoke(0)
                return@launch
            }
            _analysisStatusText.value = "Scanning device photos and videos..."
            val scanned = mediaScanner.scanDeviceMedia(100)
            if (scanned.isNotEmpty()) {
                val existingIds = allScreenshots.value.map { it.id }.toSet()
                val newItems = scanned.filter { !existingIds.contains(it.id) }
                if (newItems.isNotEmpty()) {
                    screenshotRepository.insertAll(newItems)
                }
                _snackbarMessage.value = "Indexed ${newItems.size} new photos and videos!"
                onComplete?.invoke(newItems.size)
            } else {
                _snackbarMessage.value = "Gallery is up to date."
                onComplete?.invoke(0)
            }
            _analysisStatusText.value = null
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ScreenshotFilter) {
        _selectedFilter.value = filter
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun loadExif(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = exifManager.readExif(getApplication(), screenshot)
            val current = _exifDataState.value.toMutableMap()
            current[screenshot.id] = data
            _exifDataState.value = current
        }
    }

    fun saveExif(screenshot: ScreenshotItem, exifData: ExifData, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = exifManager.writeExifSafe(getApplication(), screenshot, exifData)
            if (result.isSuccess) {
                val savedFile = result.getOrThrow()
                val updatedScreenshot = screenshot.copy(
                    filePath = savedFile.absolutePath,
                    title = exifData.imageDescription?.takeIf { it.isNotBlank() } ?: screenshot.title,
                    description = exifData.userComment?.takeIf { it.isNotBlank() } ?: screenshot.description
                )
                screenshotRepository.update(updatedScreenshot)

                val updatedExif = exifManager.readExif(getApplication(), updatedScreenshot)
                val current = _exifDataState.value.toMutableMap()
                current[screenshot.id] = updatedExif
                _exifDataState.value = current

                _snackbarMessage.value = "EXIF metadata saved directly to image!"
                onComplete?.invoke(true)
            } else {
                _snackbarMessage.value = "Failed to save EXIF: ${result.exceptionOrNull()?.message}"
                onComplete?.invoke(false)
            }
        }
    }

    fun applyAiExif(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = activeProvider.value
            val modelName = provider?.selectedModel ?: "Shots AI"
            val result = exifManager.applyAiMetadataToExifSafe(
                context = getApplication(),
                screenshot = screenshot,
                title = screenshot.title,
                description = screenshot.description,
                tags = screenshot.tags,
                modelName = modelName
            )
            if (result.isSuccess) {
                val (savedFile, updatedExif) = result.getOrThrow()
                val updatedScreenshot = screenshot.copy(filePath = savedFile.absolutePath)
                screenshotRepository.update(updatedScreenshot)

                val current = _exifDataState.value.toMutableMap()
                current[screenshot.id] = updatedExif
                _exifDataState.value = current
                _snackbarMessage.value = "AI metadata written directly into image EXIF headers!"
            } else {
                _snackbarMessage.value = "Failed to write AI to EXIF: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun analyzeScreenshot(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = autoWriteExifSetting.value,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _analysisStatusText.value = "Analyzing with ${activeProvider.value?.name ?: "Cloud AI"}..."

            val provider = activeProvider.value ?: CustomCloudProvider(
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com",
                selectedModel = "gemini-2.5-flash",
                isDefaultGemini = true
            )

            val file = File(screenshot.filePath).takeIf { it.exists() }
            val geminiKey = try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            } catch (_: Exception) { "" }

            val result = aiService.analyzeScreenshot(
                imageFile = file,
                provider = provider,
                geminiApiKey = provider.apiKey.ifBlank { geminiKey }
            )

            if (result.isSuccess) {
                // Match suggested collection if available
                val matchedCol = collections.value.find {
                    it.name.equals(result.suggestedCollection, ignoreCase = true)
                }
                val newColIds = if (matchedCol != null && !screenshot.collectionIds.contains(matchedCol.id)) {
                    screenshot.collectionIds + matchedCol.id
                } else {
                    screenshot.collectionIds
                }

                var updatedScreenshot = screenshot.copy(
                    title = if (result.title.isNotBlank()) result.title else screenshot.title,
                    description = if (result.description.isNotBlank()) result.description else screenshot.description,
                    ocrText = result.ocrText ?: screenshot.ocrText,
                    tags = (screenshot.tags + result.tags).distinct(),
                    links = (screenshot.links + result.detectedLinks).distinct(),
                    collectionIds = newColIds,
                    aiProcessed = true,
                    aiModelUsed = result.modelUsed
                )

                if (autoWriteExif) {
                    val aiExifResult = exifManager.applyAiMetadataToExifSafe(
                        context = getApplication(),
                        screenshot = updatedScreenshot,
                        title = updatedScreenshot.title,
                        description = updatedScreenshot.description,
                        tags = updatedScreenshot.tags,
                        modelName = result.modelUsed
                    )
                    if (aiExifResult.isSuccess) {
                        val (savedFile, exif) = aiExifResult.getOrThrow()
                        updatedScreenshot = updatedScreenshot.copy(filePath = savedFile.absolutePath)
                        val current = _exifDataState.value.toMutableMap()
                        current[screenshot.id] = exif
                        _exifDataState.value = current
                    }
                }

                screenshotRepository.update(updatedScreenshot)

                _snackbarMessage.value = "AI Analysis complete via ${result.modelUsed} (${result.processingTimeMs}ms)!"
            } else {
                _snackbarMessage.value = "AI Analysis failed: ${result.errorMessage ?: "Unknown error"}"
            }

            _isAnalyzing.value = false
            _analysisStatusText.value = null
            onComplete?.invoke(result)
        }
    }

    fun extractOcr(screenshot: ScreenshotItem, onComplete: ((String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            isExtractingOcr.value = true
            ocrStatusText.value = "Extracting text from image..."

            val provider = activeProvider.value ?: CustomCloudProvider(
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com",
                selectedModel = "gemini-2.5-flash",
                isDefaultGemini = true
            )
            val file = File(screenshot.filePath).takeIf { it.exists() }
            val geminiKey = try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            } catch (_: Exception) { "" }

            val result = aiService.extractOcrText(
                imageFile = file,
                provider = provider,
                geminiApiKey = provider.apiKey.ifBlank { geminiKey }
            )

            isExtractingOcr.value = false
            ocrStatusText.value = null

            if (result.isSuccess) {
                val ocr = result.getOrNull().orEmpty()
                val updated = screenshot.copy(ocrText = ocr)
                screenshotRepository.update(updated)
                _snackbarMessage.value = "Text extracted successfully (${ocr.length} chars)!"
                onComplete?.invoke(ocr)
            } else {
                _snackbarMessage.value = "OCR failed: ${result.exceptionOrNull()?.message}"
                onComplete?.invoke(null)
            }
        }
    }

    fun sendOcrToAi(
        screenshot: ScreenshotItem,
        ocrText: String,
        writeToMetadata: Boolean = false,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _analysisStatusText.value = "Processing OCR text with AI..."

            val provider = activeProvider.value ?: CustomCloudProvider(
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com",
                selectedModel = "gemini-2.5-flash",
                isDefaultGemini = true
            )
            val geminiKey = try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            } catch (_: Exception) { "" }

            val result = aiService.sendOcrToAi(
                ocrText = ocrText,
                provider = provider,
                geminiApiKey = provider.apiKey.ifBlank { geminiKey }
            )

            if (result.isSuccess) {
                val matchedCol = collections.value.find {
                    it.name.equals(result.suggestedCollection, ignoreCase = true)
                }
                val newColIds = if (matchedCol != null && !screenshot.collectionIds.contains(matchedCol.id)) {
                    screenshot.collectionIds + matchedCol.id
                } else {
                    screenshot.collectionIds
                }

                var updated = screenshot.copy(
                    title = if (result.title.isNotBlank()) result.title else screenshot.title,
                    description = if (result.description.isNotBlank()) result.description else screenshot.description,
                    ocrText = ocrText,
                    tags = (screenshot.tags + result.tags).distinct(),
                    links = (screenshot.links + result.detectedLinks).distinct(),
                    collectionIds = newColIds,
                    aiProcessed = true,
                    aiModelUsed = result.modelUsed
                )

                if (writeToMetadata) {
                    val aiExifResult = exifManager.applyAiMetadataToExifSafe(
                        context = getApplication(),
                        screenshot = updated,
                        title = updated.title,
                        description = "OCR Summary: ${updated.description}",
                        tags = updated.tags,
                        modelName = result.modelUsed
                    )
                    if (aiExifResult.isSuccess) {
                        val (savedFile, exif) = aiExifResult.getOrThrow()
                        updated = updated.copy(filePath = savedFile.absolutePath)
                        val current = _exifDataState.value.toMutableMap()
                        current[screenshot.id] = exif
                        _exifDataState.value = current
                    }
                }

                screenshotRepository.update(updated)
                _snackbarMessage.value = if (writeToMetadata)
                    "OCR analyzed & written to EXIF metadata!"
                else
                    "OCR analyzed successfully with AI!"
            } else {
                _snackbarMessage.value = "Failed to analyze OCR: ${result.errorMessage ?: "Unknown error"}"
            }

            _isAnalyzing.value = false
            _analysisStatusText.value = null
            onComplete?.invoke(result)
        }
    }

    fun writeOcrAndAiToMetadata(
        screenshot: ScreenshotItem,
        ocrText: String,
        aiTitle: String,
        aiDescription: String,
        tags: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val userComment = "OCR: ${ocrText.take(150)} | Summary: $aiDescription | Tags: ${tags.joinToString(", ")}"
            val currentExif = exifManager.readExif(getApplication(), screenshot)
            val updatedExif = currentExif.copy(
                imageDescription = aiTitle.ifBlank { currentExif.imageDescription },
                userComment = userComment
            )

            val writeResult = exifManager.writeExifSafe(getApplication(), screenshot, updatedExif)
            if (writeResult.isSuccess) {
                val savedFile = writeResult.getOrThrow()
                val updatedScreenshot = screenshot.copy(
                    filePath = savedFile.absolutePath,
                    title = aiTitle.ifBlank { screenshot.title },
                    description = aiDescription.ifBlank { screenshot.description },
                    ocrText = ocrText
                )
                screenshotRepository.update(updatedScreenshot)

                val newExif = exifManager.readExif(getApplication(), updatedScreenshot)
                val current = _exifDataState.value.toMutableMap()
                current[screenshot.id] = newExif
                _exifDataState.value = current

                _snackbarMessage.value = "OCR and AI insights written directly into image EXIF metadata!"
            } else {
                _snackbarMessage.value = "Failed to write metadata: ${writeResult.exceptionOrNull()?.message}"
            }
        }
    }

    fun batchAnalyzeScreenshots(screenshots: List<ScreenshotItem>, autoWriteExif: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            val total = screenshots.size
            screenshots.forEachIndexed { index, item ->
                _analysisStatusText.value = "Analyzing (${index + 1}/$total): ${item.title.ifBlank { "Screenshot" }}"
                analyzeScreenshot(item, autoWriteExif)
            }
            _isAnalyzing.value = false
            _analysisStatusText.value = null
            _snackbarMessage.value = "Batch analysis finished for $total screenshots!"
        }
    }

    fun toggleFavorite(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = screenshot.copy(isFavorite = !screenshot.isFavorite)
            screenshotRepository.update(updated)
        }
    }

    fun updateScreenshot(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            screenshotRepository.update(screenshot)
        }
    }

    fun deleteScreenshot(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(screenshot.filePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            screenshotRepository.delete(screenshot)
            _snackbarMessage.value = "Screenshot deleted"
        }
    }

    fun setReminder(screenshot: ScreenshotItem, timeMs: Long, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = screenshot.copy(reminderTime = timeMs, reminderText = text)
            screenshotRepository.update(updated)
            _snackbarMessage.value = "Reminder scheduled!"
        }
    }

    fun removeReminder(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = screenshot.copy(reminderTime = null, reminderText = null)
            screenshotRepository.update(updated)
            _snackbarMessage.value = "Reminder removed"
        }
    }

    fun addTag(screenshot: ScreenshotItem, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !screenshot.tags.contains(trimmed)) {
            val updated = screenshot.copy(tags = screenshot.tags + trimmed)
            updateScreenshot(updated)
        }
    }

    fun removeTag(screenshot: ScreenshotItem, tag: String) {
        val updated = screenshot.copy(tags = screenshot.tags.filter { it != tag })
        updateScreenshot(updated)
    }

    fun addScreenshotToCollection(screenshotId: String, collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val screenshot = screenshotRepository.getScreenshotSync(screenshotId) ?: return@launch
            if (!screenshot.collectionIds.contains(collectionId)) {
                screenshotRepository.update(screenshot.copy(collectionIds = screenshot.collectionIds + collectionId))
            }
        }
    }

    fun removeScreenshotFromCollection(screenshotId: String, collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val screenshot = screenshotRepository.getScreenshotSync(screenshotId) ?: return@launch
            screenshotRepository.update(screenshot.copy(collectionIds = screenshot.collectionIds - collectionId))
        }
    }

    fun createCollection(name: String, description: String, iconName: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newCol = CollectionItem(
                name = name.trim(),
                description = description.trim(),
                iconName = iconName,
                colorHex = colorHex
            )
            collectionRepository.insert(newCol)
            _snackbarMessage.value = "Collection '$name' created!"
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            collectionRepository.deleteById(collectionId)
            // Remove collection ID from all screenshots
            val all = allScreenshots.value
            all.filter { it.collectionIds.contains(collectionId) }.forEach { item ->
                screenshotRepository.update(item.copy(collectionIds = item.collectionIds - collectionId))
            }
            _snackbarMessage.value = "Collection removed"
        }
    }

    fun importImageFromUri(uri: Uri) {
        importMediaFromUri(uri)
    }

    fun importMediaFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isVideo = mimeType.startsWith("video") || uri.toString().lowercase().endsWith(".mp4")

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val dir = File(context.filesDir, "imported_media").apply { mkdirs() }
                val extension = if (isVideo) "mp4" else "jpg"
                val prefix = if (isVideo) "vid" else "img"
                val targetFile = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")

                FileOutputStream(targetFile).use { out ->
                    inputStream.copyTo(out)
                }

                var width = 0
                var height = 0
                var duration = 0L

                if (isVideo) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(targetFile.absolutePath)
                        width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.release()
                    } catch (_: Exception) {}
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(targetFile.absolutePath, options)
                    width = options.outWidth
                    height = options.outHeight
                }

                val now = System.currentTimeMillis()
                val newItem = ScreenshotItem(
                    id = UUID.randomUUID().toString(),
                    filePath = targetFile.absolutePath,
                    uriString = uri.toString(),
                    mediaType = if (isVideo) "VIDEO" else "PHOTO",
                    durationMs = duration,
                    title = targetFile.nameWithoutExtension.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    description = if (isVideo) "Imported video ($duration ms)" else "Imported photo awaiting AI indexing.",
                    tags = if (isVideo) listOf("Imported", "Video") else listOf("Imported", "Photo"),
                    addedOn = now,
                    fileSize = targetFile.length(),
                    width = width,
                    height = height
                )
                screenshotRepository.insert(newItem)
                if (!isVideo) {
                    loadExif(newItem)
                }
                _snackbarMessage.value = "Imported 1 ${if (isVideo) "video" else "photo"} successfully!"
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to import media: ${e.message}"
            }
        }
    }

    // Provider Management
    fun saveProvider(provider: CustomCloudProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            providerRepository.insert(provider)
            _snackbarMessage.value = "Provider '${provider.name}' saved!"
        }
    }

    fun deleteProvider(provider: CustomCloudProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            providerRepository.delete(provider)
            _snackbarMessage.value = "Provider removed"
        }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            providerRepository.setActiveProvider(providerId)
            val p = providers.value.find { it.id == providerId }
            _snackbarMessage.value = "Active AI Provider set to: ${p?.name}"
        }
    }

    fun testProviderConnection(provider: CustomCloudProvider, onResult: (ConnectionTestResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = aiService.testConnection(provider)
            val updated = provider.copy(
                lastTestedTime = System.currentTimeMillis(),
                lastTestStatus = if (result.isSuccess) "Connected (${result.latencyMs}ms)" else result.message
            )
            providerRepository.update(updated)
            onResult(result)
        }
    }

    fun fetchProviderModels(provider: CustomCloudProvider, onResult: (List<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val models = aiService.fetchModels(provider)
            onResult(models)
        }
    }
}
