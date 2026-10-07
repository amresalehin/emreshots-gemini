package com.amresalehin.emreshots.viewmodel

import android.app.Application
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emreshots.R
import com.amresalehin.emreshots.data.local.AppDatabase
import com.amresalehin.emreshots.data.local.SecureApiKeyStore
import com.amresalehin.emreshots.data.local.seedInitialData
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.CollectionRepository
import com.amresalehin.emreshots.data.repository.ProviderRepository
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import com.amresalehin.emreshots.service.ai.AiAnalysisResult
import com.amresalehin.emreshots.service.ai.CloudAiService
import com.amresalehin.emreshots.service.ai.ConnectionTestResult
import com.amresalehin.emreshots.service.backup.BackupData
import com.amresalehin.emreshots.service.backup.BackupRestoreManager
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.service.backup.RestoreResult
import com.amresalehin.emreshots.service.exif.ExifMetadataManager
import com.amresalehin.emreshots.service.media.MediaSyncManager
import com.amresalehin.emreshots.service.media.DuplicateDetectionService
import com.amresalehin.emreshots.service.media.DuplicateGroup
import com.amresalehin.emreshots.service.media.BackgroundSyncScheduler
import com.amresalehin.emreshots.service.ocr.LocalOcrService
import com.amresalehin.emreshots.service.ocr.LocalOcrLlmManager
import com.amresalehin.emreshots.service.ocr.LocalOcrAiResult
import com.amresalehin.emreshots.service.ocr.OcrArtefactLlmFixer
import com.amresalehin.emreshots.service.reminder.ReminderReceiver
import com.amresalehin.emreshots.service.ocr.TessDataManager
import com.amresalehin.emreshots.service.ocr.TessLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

data class IndexingState(
    val isIndexing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    val currentItemTitle: String = "",
    val currentModel: String = "",
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val isCancelled: Boolean = false
)

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

    val exifManager = ExifMetadataManager(application)
    val aiService = CloudAiService()
    private val localOcrService = LocalOcrService(application)
    val mediaScanner = com.amresalehin.emreshots.service.media.DeviceMediaScanner(application)

    private val _hasMediaPermissions = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasPermissions(application))
    val hasMediaPermissions: StateFlow<Boolean> = _hasMediaPermissions.asStateFlow()

    private val _hasMediaLocationPermission = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasMediaLocationPermission(application))
    val hasMediaLocationPermission: StateFlow<Boolean> = _hasMediaLocationPermission.asStateFlow()

    private val _hasAllMetadataPermissions = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasAllMetadataPermissions(application))
    val hasAllMetadataPermissions: StateFlow<Boolean> = _hasAllMetadataPermissions.asStateFlow()

    private val _pendingWriteIntentSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingWriteIntentSender: StateFlow<android.content.IntentSender?> = _pendingWriteIntentSender.asStateFlow()

    private var pendingWriteScreenshot: ScreenshotItem? = null
    private var pendingWriteExifData: ExifData? = null

    val allScreenshots: StateFlow<List<ScreenshotItem>> = screenshotRepository.allScreenshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionItem>> = collectionRepository.allCollections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val secureApiKeyStore = SecureApiKeyStore(application)

    val providers: StateFlow<List<CustomCloudProvider>> = providerRepository.allProviders
        .map { list -> list.map { it.copy(apiKey = secureApiKeyStore.get(it.id) ?: "") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProvider: StateFlow<CustomCloudProvider?> = providerRepository.activeProvider
        .map { provider ->
            provider?.let { p -> p.copy(apiKey = secureApiKeyStore.get(p.id) ?: "") }
                ?.takeIf { it.apiKey.isNotBlank() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    private val _lastFailedScreenshotIds = MutableStateFlow<Set<String>>(emptySet())
    val lastFailedScreenshotIds: StateFlow<Set<String>> = _lastFailedScreenshotIds.asStateFlow()


    private val _analysisStatusText = MutableStateFlow<String?>(null)
    val analysisStatusText: StateFlow<String?> = _analysisStatusText.asStateFlow()

    private val _indexingState = MutableStateFlow(IndexingState())
    val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()

    private var indexingJob: kotlinx.coroutines.Job? = null

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _exifDataState = MutableStateFlow<Map<String, ExifData>>(emptyMap())
    val exifDataState: StateFlow<Map<String, ExifData>> = _exifDataState.asStateFlow()

    private val appPreferences = com.amresalehin.emreshots.data.local.AppPreferences(application)
    private val mediaSyncManager = MediaSyncManager(application)
    // Media synchronization is handled independently from gallery display state.
    // Settings are persisted in DataStore so they survive process death and are not tied to SharedPreferences.
    private val _ocrEnabled = MutableStateFlow(true)
    val ocrEnabled: StateFlow<Boolean> = _ocrEnabled.asStateFlow()
    private val _linksDetectionEnabled = MutableStateFlow(true)
    val linksDetectionEnabled: StateFlow<Boolean> = _linksDetectionEnabled.asStateFlow()
    private val _smartTagsEnabled = MutableStateFlow(true)
    val smartTagsEnabled: StateFlow<Boolean> = _smartTagsEnabled.asStateFlow()
    private val _autoSyncDeviceMedia = MutableStateFlow(true)
    val autoSyncDeviceMedia: StateFlow<Boolean> = _autoSyncDeviceMedia.asStateFlow()
    private val _aiQualityPreset = MutableStateFlow("Balanced")
    val aiQualityPreset: StateFlow<String> = _aiQualityPreset.asStateFlow()
    private val _autoWriteExifSetting = MutableStateFlow(false)
    val autoWriteExifSetting: StateFlow<Boolean> = _autoWriteExifSetting.asStateFlow()
    private val onDeviceVisionService = com.amresalehin.emreshots.service.ai.OnDeviceVisionService(application)
    // Serialize vision writes so local and cloud VLM runs cannot race each other.
    private val visionAnalysisMutex = Mutex()
    private val _onDeviceVisionMode = MutableStateFlow("Automatic")
    val onDeviceVisionMode: StateFlow<String> = _onDeviceVisionMode.asStateFlow()
    private val _onDeviceVisionModel = MutableStateFlow("auto")
    private val _visionCaptionTagProviderId = MutableStateFlow("")
    val visionCaptionTagProviderId: StateFlow<String> = _visionCaptionTagProviderId.asStateFlow()
    val onDeviceVisionModel: StateFlow<String> = _onDeviceVisionModel.asStateFlow()

    private val _isExtractingOcr = MutableStateFlow(false)
    val isExtractingOcr: StateFlow<Boolean> = _isExtractingOcr.asStateFlow()
    private val _ocrStatusText = MutableStateFlow<String?>(null)
    val ocrStatusText: StateFlow<String?> = _ocrStatusText.asStateFlow()
    private val _showFileNames = MutableStateFlow(true)
    val showFileNames: StateFlow<Boolean> = _showFileNames.asStateFlow()
    private val _showTags = MutableStateFlow(true)
    val showTags: StateFlow<Boolean> = _showTags.asStateFlow()

    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _isSyncingDeviceMedia = MutableStateFlow(false)
    val isSyncingDeviceMedia: StateFlow<Boolean> = _isSyncingDeviceMedia.asStateFlow()
    val isLowEndDevice = com.amresalehin.emreshots.service.perf.PerformanceManager.isLowEndDevice(application)
    val isLowRamDevice = com.amresalehin.emreshots.service.perf.PerformanceManager.isLowRamDevice(application)

    val lastBackupInfo: StateFlow<String?> = appPreferences.lastBackupInfo.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tessDataManager: TessDataManager = localOcrService.tessDataManager
    val ocrArtefactLlmFixer = OcrArtefactLlmFixer(application)
    val localOcrLlmManager: LocalOcrLlmManager = ocrArtefactLlmFixer.localLlmManager

    val isLocalOcrLlmInstalled: StateFlow<Boolean> = localOcrLlmManager.isModelInstalled
    val isDownloadingLocalOcrLlm: StateFlow<Boolean> = localOcrLlmManager.isDownloading
    val localOcrLlmDownloadProgress: StateFlow<Float> = localOcrLlmManager.downloadProgress

    private val _ocrLanguage = MutableStateFlow("eng")
    val ocrLanguage: StateFlow<String> = _ocrLanguage.asStateFlow()

    private val _installedOcrLanguages = MutableStateFlow<List<String>>(emptyList())
    val installedOcrLanguages: StateFlow<List<String>> = _installedOcrLanguages.asStateFlow()

    private val _ocrLanguageDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val ocrLanguageDownloadProgress: StateFlow<Map<String, Float>> = _ocrLanguageDownloadProgress.asStateFlow()

    fun setOcrLanguage(language: String) {
        _ocrLanguage.value = language
        viewModelScope.launch { appPreferences.setOcrLanguage(language) }
    }

    fun downloadLocalOcrLlm() {
        viewModelScope.launch {
            localOcrLlmManager.downloadModel()
        }
    }

    fun importLocalOcrLlm(uri: Uri) {
        viewModelScope.launch {
            localOcrLlmManager.importModel(uri)
        }
    }

    /**
     * Directly sends extracted OCR text to the local text-only LLM (PixelShot pipeline).
     * Fixes OCR artefacts and generates categorization tags without cloud or VLM overhead.
     */
    fun scheduleBackgroundOcrArtefactFix(screenshot: ScreenshotItem, rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val aiResult = ocrArtefactLlmFixer.processOcrWithLocalAi(rawText)
            val mergedTags = (screenshot.tags + aiResult.tags).distinct().filter { it.isNotBlank() }
            val mergedLinks = (screenshot.links + aiResult.detectedLinks).distinct().filter { it.isNotBlank() }
            val newTitle = if (screenshot.title.isBlank() || screenshot.title.startsWith("Screenshot_") || screenshot.title.startsWith("IMG_")) {
                aiResult.title ?: screenshot.title
            } else screenshot.title

            screenshotRepository.update(
                screenshot.copy(
                    ocrText = aiResult.fixedOcrText,
                    tags = mergedTags,
                    links = mergedLinks,
                    title = newTitle,
                    aiProcessed = true,
                    aiModelUsed = "Local LLM"
                )
            )
        }
    }

    fun refreshInstalledOcrLanguages() {
        viewModelScope.launch(Dispatchers.IO) {
            tessDataManager.ensureBundledData()
            _installedOcrLanguages.value = tessDataManager.getInstalledLanguages()
        }
    }

    fun downloadOcrLanguage(code: String) {
        if (_ocrLanguageDownloadProgress.value.containsKey(code)) return
        viewModelScope.launch(Dispatchers.IO) {
            _ocrLanguageDownloadProgress.value = _ocrLanguageDownloadProgress.value + (code to 0.01f)
            val result = tessDataManager.downloadLanguage(code) { progress ->
                _ocrLanguageDownloadProgress.value = _ocrLanguageDownloadProgress.value + (code to progress)
            }
            _ocrLanguageDownloadProgress.value = _ocrLanguageDownloadProgress.value - code
            if (result.isSuccess) {
                refreshInstalledOcrLanguages()
                _snackbarMessage.value = "Language pack '${TessLanguage.findByCode(code).englishName}' installed."
            } else {
                _snackbarMessage.value = "Failed to download language: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
        }
    }

    fun deleteOcrLanguage(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = tessDataManager.deleteLanguage(code)
            if (deleted) {
                if (_ocrLanguage.value == code) {
                    setOcrLanguage("eng")
                }
                refreshInstalledOcrLanguages()
                _snackbarMessage.value = "Language pack '$code' removed."
            }
        }
    }

    fun importCustomOcrLanguage(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = tessDataManager.importLanguageFile(uri, fileName)
            if (result.isSuccess) {
                refreshInstalledOcrLanguages()
                val code = fileName.substringBeforeLast(".")
                setOcrLanguage(code)
                _snackbarMessage.value = "Custom language '$code' imported and selected."
            } else {
                _snackbarMessage.value = "Failed to import language: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
        }
    }

    fun setOcrEnabled(enabled: Boolean) {
        _ocrEnabled.value = enabled
        viewModelScope.launch { appPreferences.setOcrEnabled(enabled) }
    }
    fun setLinksDetectionEnabled(enabled: Boolean) {
        _linksDetectionEnabled.value = enabled
        viewModelScope.launch { appPreferences.setLinksDetectionEnabled(enabled) }
    }
    fun setSmartTagsEnabled(enabled: Boolean) {
        _smartTagsEnabled.value = enabled
        viewModelScope.launch { appPreferences.setSmartTagsEnabled(enabled) }
    }
    fun setAutoSyncDeviceMedia(enabled: Boolean) {
        _autoSyncDeviceMedia.value = enabled
        viewModelScope.launch {
            appPreferences.setAutoSyncDeviceMedia(enabled)
            if (enabled) BackgroundSyncScheduler.schedule(getApplication()) else BackgroundSyncScheduler.cancel(getApplication())
        }
    }
    fun setAiQualityPreset(preset: String) {
        _aiQualityPreset.value = preset
        viewModelScope.launch { appPreferences.setAiQualityPreset(preset) }
    }
    fun setOnDeviceVisionMode(value: String) {
        _onDeviceVisionMode.value = value
        viewModelScope.launch { appPreferences.setOnDeviceVisionMode(value) }
    }

    fun setOnDeviceVisionModel(value: String) {
        _onDeviceVisionModel.value = value
        viewModelScope.launch { appPreferences.setOnDeviceVisionModel(value) }
    }

    fun setVisionCaptionTagProviderId(value: String) {
        _visionCaptionTagProviderId.value = value
        viewModelScope.launch { appPreferences.setVisionCaptionTagProviderId(value) }
    }

    fun getOnDeviceVisionRecommendation(callback: (com.amresalehin.emreshots.service.ai.OnDeviceModelRecommendation) -> Unit) {
        viewModelScope.launch {
            callback(onDeviceVisionService.recommendation())
        }
    }

    fun setAutoWriteExifSetting(enabled: Boolean) {
        _autoWriteExifSetting.value = enabled
        viewModelScope.launch { appPreferences.setAutoWriteExif(enabled) }
    }
    fun setGridColumns(cols: Int) {
        val normalized = cols.coerceIn(2, 5)
        _gridColumns.value = normalized
        viewModelScope.launch { appPreferences.setGridColumns(normalized) }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
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
        _snackbarMessage.value = context.getString(R.string.memory_cache_cleared)
    }

    // Gallery Organization & View Options
    val sortOption = MutableStateFlow(MediaSortOption.NEWEST)
    val groupByOption = MutableStateFlow(MediaGroupBy.NONE)
    val viewMode = MutableStateFlow(GalleryViewMode.GRID)

    fun setSortOption(option: MediaSortOption) { sortOption.value = option }
    fun setGroupByOption(option: MediaGroupBy) { groupByOption.value = option }
    fun setViewMode(mode: GalleryViewMode) { viewMode.value = mode }

    val filteredScreenshots: StateFlow<List<ScreenshotItem>> = combine(allScreenshots, _searchQuery, _selectedFilter, collections) { screenshots, query, filter, collectionList ->
        var result = screenshots
        if (query.isNotBlank()) {
            val clauses = parseAdvancedQuery(query)
            result = result.filter { matchesAdvancedQuery(it, clauses, collectionList) }
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

    private data class SearchClause(val field: String?, val value: String, val negated: Boolean)

    private fun parseAdvancedQuery(query: String): List<SearchClause> {
        val pattern = Pattern.compile("""(-)?([A-Za-z]+):(?:"([^"]+)"|(\S+))|(-)?(?:"([^"]+)"|(\S+))""")
        val matcher = pattern.matcher(query)
        val clauses = mutableListOf<SearchClause>()
        while (matcher.find()) {
            val negated = matcher.group(1) == "-" || matcher.group(5) == "-"
            val field = matcher.group(2)?.lowercase()
            val value = matcher.group(3) ?: matcher.group(4) ?: matcher.group(6) ?: matcher.group(7)
            if (!value.isNullOrBlank()) clauses += SearchClause(field, value.trim(), negated)
        }
        return clauses
    }

    private fun matchesAdvancedQuery(item: ScreenshotItem, clauses: List<SearchClause>, collectionList: List<CollectionItem>): Boolean {
        val haystack = listOf(item.title, item.description, item.tags.joinToString(" "), item.links.joinToString(" "), item.notes.orEmpty(), item.ocrText.orEmpty(), item.filePath).joinToString(" ").lowercase()
        return clauses.all { clause ->
            val value = clause.value.lowercase()
            val matched = when (clause.field) {
                null -> haystack.contains(value)
                "title" -> item.title.lowercase().contains(value)
                "description", "desc" -> item.description.lowercase().contains(value)
                "tag", "tags" -> item.tags.any { it.lowercase().contains(value) }
                "ocr", "text" -> item.ocrText?.lowercase()?.contains(value) == true
                "link", "url", "domain" -> item.links.any { it.lowercase().contains(value) }
                "note", "notes" -> item.notes?.lowercase()?.contains(value) == true
                "type" -> when (value) {
                    "video" -> item.isVideo
                    "photo" -> !item.isVideo && !item.tags.any { it.equals("Screenshot", true) }
                    "screenshot" -> !item.isVideo && (item.tags.any { it.equals("Screenshot", true) } || item.title.contains("screenshot", true))
                    else -> haystack.contains(value)
                }
                "favorite", "fav" -> item.isFavorite == (value == "true" || value == "yes" || value == "1")
                "ai" -> item.aiProcessed == (value == "true" || value == "yes" || value == "1" || value == "indexed")
                "collection", "in" -> item.collectionIds.mapNotNull { id -> collectionList.find { it.id == id }?.name?.lowercase() }.any { it.contains(value) }
                "before" -> parseDateStart(value)?.let { item.addedOn < it } ?: false
                "after" -> parseDateEnd(value)?.let { item.addedOn > it } ?: false
                else -> haystack.contains(value)
            }
            if (clause.negated) !matched else matched
        }
    }

    private fun parseDateStart(value: String): Long? = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.time }.getOrNull()
    private fun parseDateEnd(value: String): Long? = parseDateStart(value)?.plus(24 * 60 * 60 * 1000L - 1)

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
        viewModelScope.launch {
            appPreferences.onDeviceVisionMode.collect { _onDeviceVisionMode.value = it }
        }
        viewModelScope.launch {
            appPreferences.onDeviceVisionModel.collect { _onDeviceVisionModel.value = it }
        }
        viewModelScope.launch {
            appPreferences.showFileNames.collect { _showFileNames.value = it }
        }
        viewModelScope.launch {
            appPreferences.showTags.collect { _showTags.value = it }
        }

        viewModelScope.launch { appPreferences.ocrEnabled.collect { _ocrEnabled.value = it } }
        viewModelScope.launch { appPreferences.ocrLanguage.collect { _ocrLanguage.value = it } }
        refreshInstalledOcrLanguages()
        viewModelScope.launch { appPreferences.linksDetectionEnabled.collect { _linksDetectionEnabled.value = it } }
        viewModelScope.launch { appPreferences.smartTagsEnabled.collect { _smartTagsEnabled.value = it } }
        viewModelScope.launch { appPreferences.autoSyncDeviceMedia.collect { _autoSyncDeviceMedia.value = it } }
        viewModelScope.launch { appPreferences.aiQualityPreset.collect { _aiQualityPreset.value = it } }
        viewModelScope.launch { appPreferences.autoWriteExif.collect { _autoWriteExifSetting.value = it } }
        viewModelScope.launch { appPreferences.visionCaptionTagProviderId.collect { _visionCaptionTagProviderId.value = it } }
        viewModelScope.launch { appPreferences.gridColumns.collect { _gridColumns.value = it.coerceIn(2, 5) } }
        viewModelScope.launch(Dispatchers.IO) {
            localOcrLlmManager.ensureModelReady()
            seedInitialData(database, getApplication())
            migrateProviderApiKeysToKeystore()
            ensureConfiguredProviderActiveIfNeeded()
        }
        checkPermissions()
    }

    private suspend fun migrateProviderApiKeysToKeystore() {
        providerRepository.getAllProvidersSync().forEach { provider ->
            if (provider.apiKey.isNotBlank()) {
                secureApiKeyStore.put(provider.id, provider.apiKey)
                providerRepository.update(provider.copy(apiKey = ""))
            }
        }
    }

    private suspend fun ensureConfiguredProviderActiveIfNeeded() {
        val active = providerRepository.getActiveProviderSync()
        val activeKey = active?.let { secureApiKeyStore.get(it.id).orEmpty() }
        if (active != null && activeKey.orEmpty().isNotBlank()) return

        val configured = providerRepository.getAllProvidersSync().firstOrNull {
            secureApiKeyStore.get(it.id).orEmpty().isNotBlank()
        }
        if (configured != null) {
            providerRepository.setActiveProvider(configured.id)
        }
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        _hasMediaPermissions.value = com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasPermissions(app)
        _hasMediaLocationPermission.value = com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasMediaLocationPermission(app)
        _hasAllMetadataPermissions.value = com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasAllMetadataPermissions(app)
    }

    fun onPermissionsResult(granted: Boolean) {
        checkPermissions()
        if (granted) {
            _snackbarMessage.value = "Media and metadata permissions updated!"
            syncDeviceMedia()
        } else {
            _snackbarMessage.value = "Permission denied. Some metadata features may be restricted."
        }
    }

    fun syncDeviceMedia(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingDeviceMedia.value = true
            try {
                checkPermissions()
                if (!_hasMediaPermissions.value) {
                    _snackbarMessage.value = "Please grant photos and videos permissions to auto-sync."
                    onComplete?.invoke(0)
                    return@launch
                }
                _analysisStatusText.value = "Syncing gallery…"
                runCatching { mediaSyncManager.synchronize() }
                    .onSuccess { result -> onComplete?.invoke(result.added) }
                    .onFailure { _snackbarMessage.value = "Gallery sync failed: " + (it.message ?: "Unknown error") }
            } finally {
                _analysisStatusText.value = null
                _isSyncingDeviceMedia.value = false
            }
        }
    }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun batchRename(items: List<ScreenshotItem>, template: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTemplate = template.trim()
            if (cleanTemplate.isBlank()) { _snackbarMessage.value = "Enter a rename template."; return@launch }
            var renamed = 0; var failed = 0
            items.forEachIndexed { index, item ->
                val extension = item.filePath.substringAfterLast(".", "").takeIf { it.isNotBlank() } ?: if (item.isVideo) "mp4" else "jpg"
                val collectionName = item.collectionIds.firstNotNullOfOrNull { id -> collections.value.find { it.id == id }?.name }.orEmpty()
                val baseName = cleanTemplate.replace("{index}", (index + 1).toString())
                    .replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.addedOn)))
                    .replace("{time}", SimpleDateFormat("HH-mm-ss", Locale.US).format(Date(item.addedOn)))
                    .replace("{title}", item.title.ifBlank { "Screenshot" }.sanitizeFileName())
                    .replace("{collection}", collectionName.ifBlank { "Unsorted" }.sanitizeFileName())
                    .replace("{type}", if (item.isVideo) "video" else "image").sanitizeFileName().take(180)
                if (baseName.isBlank()) { failed++; return@forEachIndexed }
                val newDisplayName = "$baseName.$extension"
                if (renameMediaItem(item, newDisplayName)) {
                    val newPath = if (item.filePath.isBlank()) item.filePath else File(item.filePath).let { File(it.parentFile, newDisplayName).absolutePath }
                    screenshotRepository.update(item.copy(title = baseName, filePath = newPath)); renamed++
                } else failed++
            }
            _snackbarMessage.value = "Batch rename complete: $renamed renamed${if (failed > 0) ", $failed failed" else ""}."
        }
    }

    private fun String.sanitizeFileName(): String = replace(Regex("""[\\/:*?""<>|]"""), "_").replace(Regex("""\s+"""), " ").trim(' ', '.')

    private fun renameMediaItem(item: ScreenshotItem, newDisplayName: String): Boolean {
        val context = getApplication<Application>()
        val uri = item.uriString?.let(Uri::parse)
        if (uri != null && uri.toString().startsWith("content://media/")) return runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName) }, null, null) > 0
        }.getOrDefault(false)
        val file = File(item.filePath)
        return file.exists() && runCatching { file.renameTo(File(file.parentFile, newDisplayName)) }.getOrDefault(false)
    }

    fun setFilter(filter: ScreenshotFilter) {
        if (_selectedFilter.value == filter) {
            _selectedFilter.value = ScreenshotFilter.ALL
        } else {
            _selectedFilter.value = filter
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun setShowFileNames(value: Boolean) {
        _showFileNames.value = value
        viewModelScope.launch { appPreferences.setShowFileNames(value) }
    }

    fun setShowTags(value: Boolean) {
        _showTags.value = value
        viewModelScope.launch { appPreferences.setShowTags(value) }
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

    fun clearPendingWriteIntent() {
        _pendingWriteIntentSender.value = null
        pendingWriteScreenshot = null
        pendingWriteExifData = null
    }

    fun onWriteConsentGranted() {
        val shot = pendingWriteScreenshot ?: return
        val data = pendingWriteExifData ?: return
        pendingWriteScreenshot = null
        pendingWriteExifData = null
        _pendingWriteIntentSender.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(shot.uriString)
                getApplication<Application>().contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exifInterface = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                    exifManager.applyDataToExifInterface(exifInterface, data)
                    exifInterface.saveAttributes()
                }
                val localFile = File(shot.filePath)
                if (localFile.exists() && localFile.canWrite()) {
                    exifManager.writeExif(localFile, data)
                }
                val updatedScreenshot = shot.copy(
                    title = data.imageDescription?.takeIf { it.isNotBlank() } ?: shot.title,
                    description = data.userComment?.takeIf { it.isNotBlank() } ?: shot.description
                )
                screenshotRepository.update(updatedScreenshot)
                val updatedExif = exifManager.readExif(getApplication(), updatedScreenshot)
                val current = _exifDataState.value.toMutableMap()
                current[shot.id] = updatedExif
                _exifDataState.value = current
                _snackbarMessage.value = "Write consent granted: EXIF saved to original media!"
            } catch (e: Exception) {
                // Fallback to safe file write
                saveExif(shot, data)
            }
        }
    }

    fun onWriteConsentDenied() {
        val shot = pendingWriteScreenshot
        val data = pendingWriteExifData
        pendingWriteScreenshot = null
        pendingWriteExifData = null
        _pendingWriteIntentSender.value = null
        if (shot != null && data != null) {
            // Save safe working copy instead
            saveExif(shot, data)
        }
    }

    fun saveExif(screenshot: ScreenshotItem, exifData: ExifData, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            // If it's a MediaStore item, test if direct write needs Scoped Storage user permission
            if (!screenshot.uriString.isNullOrBlank() && screenshot.uriString.startsWith("content://media/")) {
                val uri = Uri.parse(screenshot.uriString)
                try {
                    val pfd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "rw")
                    if (pfd != null) {
                        pfd.use {
                            val exifInterface = androidx.exifinterface.media.ExifInterface(it.fileDescriptor)
                            exifManager.applyDataToExifInterface(exifInterface, exifData)
                            exifInterface.saveAttributes()
                        }
                        val localFile = File(screenshot.filePath)
                        if (localFile.exists() && localFile.canWrite()) {
                            exifManager.writeExif(localFile, exifData)
                        }
                        val updatedScreenshot = screenshot.copy(
                            title = exifData.imageDescription?.takeIf { it.isNotBlank() } ?: screenshot.title,
                            description = exifData.userComment?.takeIf { it.isNotBlank() } ?: screenshot.description
                        )
                        screenshotRepository.update(updatedScreenshot)
                        val updatedExif = exifManager.readExif(getApplication(), updatedScreenshot)
                        val current = _exifDataState.value.toMutableMap()
                        current[screenshot.id] = updatedExif
                        _exifDataState.value = current
                        _snackbarMessage.value = "EXIF metadata saved directly to device gallery image!"
                        onComplete?.invoke(true)
                        return@launch
                    }
                } catch (e: Exception) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && e is SecurityException) {
                        try {
                            val pi = android.provider.MediaStore.createWriteRequest(getApplication<Application>().contentResolver, listOf(uri))
                            pendingWriteScreenshot = screenshot
                            pendingWriteExifData = exifData
                            _pendingWriteIntentSender.value = pi.intentSender
                            return@launch
                        } catch (_: Exception) {}
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                        val pi = e.userAction.actionIntent.intentSender
                        pendingWriteScreenshot = screenshot
                        pendingWriteExifData = exifData
                        _pendingWriteIntentSender.value = pi
                        return@launch
                    }
                }
            }

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

    private fun resolveImageFile(screenshot: ScreenshotItem): File? {
        val direct = File(screenshot.filePath)
        if (direct.exists() && direct.canRead() && direct.length() > 0) {
            return direct
        }
        if (!screenshot.uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(screenshot.uriString)
                val context = getApplication<Application>()
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
                    context = getApplication(),
                    screenshot = updatedScreenshot,
                    title = updatedScreenshot.title,
                    description = updatedScreenshot.description,
                    tags = updatedScreenshot.tags,
                    modelName = finalResult.modelUsed
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
        }

        return finalResult
    }
    fun analyzeLocalVision(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = false,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            _isExtractingOcr.value = true
            val langLabel = TessLanguage.findByCode(_ocrLanguage.value).englishName
            val engineLabel = "Tesseract ($langLabel)"
            _ocrStatusText.value = "Extracting text using $engineLabel…"

            val file = resolveImageFile(screenshot)
            val result = if (file != null) {
                localOcrService.recognize(file, languageCode = _ocrLanguage.value)
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
        if (!_ocrEnabled.value) {
            _snackbarMessage.value = "OCR is disabled in Settings. Enable it to run text extraction."
            return
        }
        val targets = items.filter { !it.isVideo && (!onlyMissing || it.ocrText.isNullOrBlank()) }
        if (targets.isEmpty()) {
            _snackbarMessage.value = if (onlyMissing) "No media is waiting for local OCR." else "No image media is available for local OCR."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isExtractingOcr.value = true
            var completed = 0
            var failed = 0
            val langLabel = TessLanguage.findByCode(_ocrLanguage.value).englishName
            val engineLabel = "Tesseract ($langLabel)"
            try {
                targets.forEachIndexed { index, item ->
                    if (!isActive) return@forEachIndexed
                    _ocrStatusText.value = "$engineLabel (" + (index + 1) + "/" + targets.size + "): " + item.title.ifBlank { "Image " + (index + 1) }
                    val file = resolveImageFile(item)
                    val result = if (file != null) {
                        localOcrService.recognize(file, languageCode = _ocrLanguage.value)
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
        indexingJob = viewModelScope.launch(Dispatchers.IO) {
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
            _snackbarMessage.value = getApplication<Application>().getString(R.string.no_failed_items_to_retry)
            return
        }
        startIndexing(targetScreenshots = failedItems, onlyUnindexed = false, autoWriteExif = autoWriteExif)
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
            ReminderReceiver.schedule(
                getApplication(),
                screenshot.id,
                timeMs,
                context.getString(R.string.reminder_notification_title),
                text
            )
            _snackbarMessage.value = "Reminder scheduled!"
        }
    }

    fun removeReminder(screenshot: ScreenshotItem) {
        viewModelScope.launch(Dispatchers.IO) {
            ReminderReceiver.cancel(getApplication(), screenshot.id)
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

                inputStream.use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                }

                var width = 0
                var height = 0
                var duration = 0L

                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(targetFile.absolutePath)
                        width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
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
    fun saveProvider(provider: CustomCloudProvider, makeActive: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val isGemini = provider.baseUrl.contains("generativelanguage.googleapis.com") ||
                    provider.name.contains("Gemini", ignoreCase = true)
            val existingKey = secureApiKeyStore.get(provider.id).orEmpty()
            val effectiveKey = provider.apiKey.ifBlank { existingKey }
            val updated = provider.copy(
                isDefaultGemini = isGemini || provider.isDefaultGemini
            )
            if (provider.apiKey.isNotBlank()) {
                secureApiKeyStore.put(provider.id, provider.apiKey)
            }
            val shouldActivate = makeActive && effectiveKey.isNotBlank()
            providerRepository.saveProvider(updated.copy(apiKey = ""), shouldActivate)
            val action = if (shouldActivate) "saved & activated" else "saved"
            _snackbarMessage.value = "AI Provider '" + updated.name + "' " + action + "!"
        }
    }

    fun deleteProvider(provider: CustomCloudProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            providerRepository.deleteProviderWithFallback(provider)
            secureApiKeyStore.remove(provider.id)
            _snackbarMessage.value = "Provider '${provider.name}' removed"
        }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = providerRepository.getAllProvidersSync().find { it.id == providerId }
            val apiKey = provider?.let { secureApiKeyStore.get(it.id).orEmpty() }.orEmpty()
            if (provider == null || apiKey.isBlank()) {
                _snackbarMessage.value = "Provider is not configured. Add an API key before activating it."
                return@launch
            }
            providerRepository.setActiveProvider(providerId)
            _snackbarMessage.value = "Active AI Provider set to: " + provider.name
        }
    }

    fun testProviderConnection(provider: CustomCloudProvider, onResult: (ConnectionTestResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = aiService.testConnection(provider, provider.apiKey)
            val updated = provider.copy(
                lastTestedTime = System.currentTimeMillis(),
                lastTestStatus = if (result.isSuccess) "Connected (${result.latencyMs}ms)" else result.message
            )
            providerRepository.update(updated)
            onResult(result)
        }
    }

    fun fetchProviderModels(provider: CustomCloudProvider, onResult: (com.amresalehin.emreshots.service.ai.FetchModelsResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = aiService.fetchModels(provider, provider.apiKey)
            onResult(result)
        }
    }

    // Backup & Restore
    private fun getSettingsMap(): Map<String, String> {
        return mapOf(
            "ocr_enabled" to ocrEnabled.value.toString(),
            "links_detection_enabled" to linksDetectionEnabled.value.toString(),
            "smart_tags_enabled" to smartTagsEnabled.value.toString(),
            "auto_sync_device_media" to autoSyncDeviceMedia.value.toString(),
            "ai_quality_preset" to aiQualityPreset.value,
            "auto_write_exif" to autoWriteExifSetting.value.toString(),
            "grid_columns" to gridColumns.value.toString()
        )
    }

    fun exportBackupJson(): String {
        // Safe synchronous fallback using current state (never blocks main thread or Room)
        val shots = allScreenshots.value
        val cols = collections.value
        val provs = providers.value.map { it.copy(apiKey = "") }
        return BackupRestoreManager.createBackupJson(shots, cols, provs, getSettingsMap())
    }

    suspend fun generateBackupJson(): String = withContext(Dispatchers.IO) {
        val shots = screenshotRepository.getAllScreenshotsSync()
        val cols = collectionRepository.getAllCollectionsSync()
        val provs = providerRepository.getAllProvidersSync().map { it.copy(apiKey = "") }
        BackupRestoreManager.createBackupJson(shots, cols, provs, getSettingsMap())
    }

    fun exportBackupToUri(context: Context, uri: Uri, onComplete: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                val writeResult = BackupRestoreManager.writeBackupToUri(context, uri, json)
                withContext(Dispatchers.Main) {
                    if (writeResult.isSuccess) {
                        val count = allScreenshots.value.size
                        val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
                        val info = "Last export: $timeStr ($count items)"
                        viewModelScope.launch { appPreferences.setLastBackupInfo(info) }
                        _snackbarMessage.value = "Backup successfully exported!"
                    } else {
                        _snackbarMessage.value = "Failed to export backup: ${writeResult.exceptionOrNull()?.message}"
                    }
                    onComplete?.invoke(writeResult)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = "Export error: ${e.message}"
                    onComplete?.invoke(Result.failure(e))
                }
            }
        }
    }

    fun shareBackup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                withContext(Dispatchers.Main) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, json)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share EmreShots Backup JSON")
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = "Failed to share backup: ${e.message}"
                }
            }
        }
    }

    fun createBackupFile(onComplete: ((Result<File>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                val result = BackupRestoreManager.createLocalBackupFile(getApplication(), json)
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val count = allScreenshots.value.size
                    val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
                    val info = "Last backup: $timeStr ($count items)"
                    viewModelScope.launch { appPreferences.setLastBackupInfo(info) }
                    _snackbarMessage.value = "Backup created: ${file.name} ($count items)"
                } else {
                    _snackbarMessage.value = "Failed to create backup: ${result.exceptionOrNull()?.message}"
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(result)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Backup error: ${e.message}"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
            }
        }
    }

    fun restoreFromUri(uri: Uri, mode: RestoreMode, onComplete: ((RestoreResult) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val readResult = BackupRestoreManager.readBackupFromUri(getApplication(), uri)
            if (readResult.isFailure) {
                val errorMsg = readResult.exceptionOrNull()?.message ?: "Invalid backup file"
                _snackbarMessage.value = "Failed to read backup: $errorMsg"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(RestoreResult(isSuccess = false, message = errorMsg))
                }
                return@launch
            }

            val data = readResult.getOrThrow()
            restoreBackupData(data, mode, onComplete)
        }
    }

    fun restoreFromJson(jsonString: String, mode: RestoreMode, onComplete: ((RestoreResult) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = BackupRestoreManager.parseBackupJson(jsonString)
                restoreBackupData(data, mode, onComplete)
            } catch (e: Exception) {
                val msg = "Failed to parse backup JSON: ${e.message}"
                _snackbarMessage.value = msg
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(RestoreResult(isSuccess = false, message = msg))
                }
            }
        }
    }

    private suspend fun restoreBackupData(
        data: BackupData,
        mode: RestoreMode,
        onComplete: ((RestoreResult) -> Unit)? = null
    ) {
        try {
            if (mode == RestoreMode.REPLACE) {
                screenshotRepository.deleteAll()
                collectionRepository.deleteAll()
                providerRepository.deleteAll()
            }

            // Restore collections
            if (data.collections.isNotEmpty()) {
                collectionRepository.insertAll(data.collections)
            }

            // Restore screenshots
            if (data.screenshots.isNotEmpty()) {
                screenshotRepository.insertAll(data.screenshots)
            }

            // Restore providers without ever activating an unconfigured provider.
            if (data.providers.isNotEmpty()) {
                data.providers.forEach { provider ->
                    if (provider.apiKey.isNotBlank()) {
                        secureApiKeyStore.put(provider.id, provider.apiKey)
                    }
                }
                providerRepository.insertAll(data.providers.map { it.copy(apiKey = "") })
            }
            ensureConfiguredProviderActiveIfNeeded()

            // Restore settings
            var settingsRestored = 0
            data.settings["ocr_enabled"]?.toBooleanStrictOrNull()?.let { setOcrEnabled(it); settingsRestored++ }
            data.settings["links_detection_enabled"]?.toBooleanStrictOrNull()?.let { setLinksDetectionEnabled(it); settingsRestored++ }
            data.settings["smart_tags_enabled"]?.toBooleanStrictOrNull()?.let { setSmartTagsEnabled(it); settingsRestored++ }
            data.settings["auto_sync_device_media"]?.toBooleanStrictOrNull()?.let { setAutoSyncDeviceMedia(it); settingsRestored++ }
            data.settings["ai_quality_preset"]?.let { setAiQualityPreset(it); settingsRestored++ }
            data.settings["auto_write_exif"]?.toBooleanStrictOrNull()?.let { setAutoWriteExifSetting(it); settingsRestored++ }
            data.settings["grid_columns"]?.toIntOrNull()?.let { setGridColumns(it); settingsRestored++ }

            val totalRestored = data.screenshots.size
            val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
            val info = "Restored: $timeStr ($totalRestored items)"
            viewModelScope.launch { appPreferences.setLastBackupInfo(info) }

            val successMsg = "Successfully restored $totalRestored items, ${data.collections.size} collections, ${data.providers.size} AI providers!"
            _snackbarMessage.value = successMsg

            val result = RestoreResult(
                isSuccess = true,
                screenshotsRestored = data.screenshots.size,
                collectionsRestored = data.collections.size,
                providersRestored = data.providers.size,
                settingsRestored = settingsRestored,
                message = successMsg
            )
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        } catch (e: Exception) {
            val errMsg = "Failed to restore: ${e.message}"
            _snackbarMessage.value = errMsg
            withContext(Dispatchers.Main) {
                onComplete?.invoke(RestoreResult(isSuccess = false, message = errMsg))
            }
        }
    }
}
