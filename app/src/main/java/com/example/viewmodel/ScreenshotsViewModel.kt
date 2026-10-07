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
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ExifData
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

class ScreenshotsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val screenshotRepository = ScreenshotRepository(database.screenshotDao())
    val collectionRepository = CollectionRepository(database.collectionDao())
    val providerRepository = ProviderRepository(database.providerDao())

    val exifManager = ExifMetadataManager(application)
    val aiService = CloudAiService()
    private val onDeviceVisionService = com.amresalehin.emreshots.service.ai.OnDeviceVisionService(application)
    private val localOcrService = LocalOcrService(application)
    val mediaScanner = com.amresalehin.emreshots.service.media.DeviceMediaScanner(application)

    private val _hasMediaPermissions = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasPermissions(application))
    val hasMediaPermissions: StateFlow<Boolean> = _hasMediaPermissions.asStateFlow()

    private val _hasMediaLocationPermission = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasMediaLocationPermission(application))
    val hasMediaLocationPermission: StateFlow<Boolean> = _hasMediaLocationPermission.asStateFlow()

    private val _hasAllMetadataPermissions = MutableStateFlow(com.amresalehin.emreshots.service.media.DeviceMediaScanner.hasAllMetadataPermissions(application))
    val hasAllMetadataPermissions: StateFlow<Boolean> = _hasAllMetadataPermissions.asStateFlow()

    private val galleryViewModel = GalleryViewModel(application, screenshotRepository, collectionRepository)

    val allScreenshots: StateFlow<List<ScreenshotItem>> = galleryViewModel.allScreenshots
    val collections: StateFlow<List<CollectionItem>> = galleryViewModel.collections

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

    val searchQuery: StateFlow<String> = galleryViewModel.searchQuery
    val selectedFilter: StateFlow<ScreenshotFilter> = galleryViewModel.selectedFilter

    val sortOption: StateFlow<MediaSortOption> = galleryViewModel.sortOption
    val groupByOption: StateFlow<MediaGroupBy> = galleryViewModel.groupByOption
    val viewMode: StateFlow<GalleryViewMode> = galleryViewModel.viewMode
    val filteredScreenshots: StateFlow<List<ScreenshotItem>> = galleryViewModel.filteredScreenshots
    val sortedScreenshots: StateFlow<List<ScreenshotItem>> = galleryViewModel.sortedScreenshots
    val groupedScreenshots: StateFlow<Map<String, List<ScreenshotItem>>> = galleryViewModel.groupedScreenshots


    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

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
    private val _onDeviceVisionMode = MutableStateFlow("Automatic")
    val onDeviceVisionMode: StateFlow<String> = _onDeviceVisionMode.asStateFlow()
    private val _onDeviceVisionModel = MutableStateFlow("auto")
    private val _visionCaptionTagProviderId = MutableStateFlow("")
    val visionCaptionTagProviderId: StateFlow<String> = _visionCaptionTagProviderId.asStateFlow()
    val onDeviceVisionModel: StateFlow<String> = _onDeviceVisionModel.asStateFlow()

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

    private val exifDataStore = MutableStateFlow<Map<String, ExifData>>(emptyMap())

    private val aiOcrCoordinator = AiOcrCoordinator(
        application = application,
        scope = viewModelScope,
        screenshotRepository = screenshotRepository,
        collections = collections,
        providers = providers,
        activeProvider = activeProvider,
        ocrEnabled = ocrEnabled,
        smartTagsEnabled = smartTagsEnabled,
        linksDetectionEnabled = linksDetectionEnabled,
        aiQualityPreset = aiQualityPreset,
        onDeviceVisionMode = onDeviceVisionMode,
        onDeviceVisionModel = onDeviceVisionModel,
        visionCaptionTagProviderId = visionCaptionTagProviderId,
        ocrLanguage = ocrLanguage,
        autoWriteExifSetting = autoWriteExifSetting,
        exifDataStore = exifDataStore,
        exifManager = exifManager,
        aiService = aiService,
        localOcrService = localOcrService,
        ocrArtefactLlmFixer = ocrArtefactLlmFixer,
        onMessage = { _snackbarMessage.value = it }
    )

    val isAnalyzing: StateFlow<Boolean> = aiOcrCoordinator.isAnalyzing
    val lastFailedScreenshotIds: StateFlow<Set<String>> = aiOcrCoordinator.lastFailedScreenshotIds
    val analysisStatusText: StateFlow<String?> = aiOcrCoordinator.analysisStatusText
    val indexingState: StateFlow<IndexingState> = aiOcrCoordinator.indexingState
    val isExtractingOcr: StateFlow<Boolean> = aiOcrCoordinator.isExtractingOcr
    val ocrStatusText: StateFlow<String?> = aiOcrCoordinator.ocrStatusText

    private val mediaLibraryCoordinator = MediaLibraryCoordinator(
        application = application,
        scope = viewModelScope,
        screenshotRepository = screenshotRepository,
        collectionRepository = collectionRepository,
        allScreenshots = allScreenshots,
        collections = collections,
        activeProvider = activeProvider,
        exifDataStore = exifDataStore,
        exifManager = exifManager,
        onMessage = { _snackbarMessage.value = it }
    )

    val pendingWriteIntentSender: StateFlow<android.content.IntentSender?> =
        mediaLibraryCoordinator.pendingWriteIntentSender
    val exifDataState: StateFlow<Map<String, ExifData>> = exifDataStore.asStateFlow()

    private val providerBackupCoordinator = ProviderBackupCoordinator(
        application = application,
        scope = viewModelScope,
        providerRepository = providerRepository,
        screenshotRepository = screenshotRepository,
        collectionRepository = collectionRepository,
        secureApiKeyStore = secureApiKeyStore,
        appPreferences = appPreferences,
        aiService = aiService,
        allScreenshots = allScreenshots,
        collections = collections,
        providers = providers,
        ocrEnabled = ocrEnabled,
        linksDetectionEnabled = linksDetectionEnabled,
        smartTagsEnabled = smartTagsEnabled,
        autoSyncDeviceMedia = autoSyncDeviceMedia,
        aiQualityPreset = aiQualityPreset,
        autoWriteExifSetting = autoWriteExifSetting,
        gridColumns = gridColumns,
        setOcrEnabled = ::setOcrEnabled,
        setLinksDetectionEnabled = ::setLinksDetectionEnabled,
        setSmartTagsEnabled = ::setSmartTagsEnabled,
        setAutoSyncDeviceMedia = ::setAutoSyncDeviceMedia,
        setAiQualityPreset = ::setAiQualityPreset,
        setAutoWriteExifSetting = ::setAutoWriteExifSetting,
        setGridColumns = ::setGridColumns,
        ensureConfiguredProviderActiveIfNeeded = { ensureConfiguredProviderActiveIfNeeded() },
        onMessage = { _snackbarMessage.value = it }
    )

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
                _snackbarMessage.value = "Syncing gallery…"
                runCatching { mediaSyncManager.synchronize() }
                    .onSuccess { result -> onComplete?.invoke(result.added) }
                    .onFailure { _snackbarMessage.value = "Gallery sync failed: " + (it.message ?: "Unknown error") }
            } finally {
                
                _isSyncingDeviceMedia.value = false
            }
        }
    }
    fun setSearchQuery(query: String) { galleryViewModel.setSearchQuery(query) }

    fun setFilter(filter: ScreenshotFilter) { galleryViewModel.setFilter(filter) }
    fun setSortOption(option: MediaSortOption) = galleryViewModel.setSortOption(option)
    fun setGroupByOption(option: MediaGroupBy) = galleryViewModel.setGroupByOption(option)
    fun setViewMode(mode: GalleryViewMode) = galleryViewModel.setViewMode(mode)

    fun analyzeLocalVision(screenshot: ScreenshotItem, autoWriteExif: Boolean = false, onComplete: ((AiAnalysisResult) -> Unit)? = null) =
        aiOcrCoordinator.analyzeLocalVision(screenshot, autoWriteExif, onComplete)

    fun analyzeCloudVision(screenshot: ScreenshotItem, autoWriteExif: Boolean = false, onComplete: ((AiAnalysisResult) -> Unit)? = null) =
        aiOcrCoordinator.analyzeCloudVision(screenshot, autoWriteExif, onComplete)

    fun analyzeScreenshot(screenshot: ScreenshotItem, autoWriteExif: Boolean = autoWriteExifSetting.value, onComplete: ((AiAnalysisResult) -> Unit)? = null) =
        aiOcrCoordinator.analyzeScreenshot(screenshot, autoWriteExif, onComplete)

    fun extractOcr(screenshot: ScreenshotItem, onComplete: ((String?) -> Unit)? = null) =
        aiOcrCoordinator.extractOcr(screenshot, onComplete)

    fun batchExtractOcr(items: List<ScreenshotItem>, onlyMissing: Boolean = true) =
        aiOcrCoordinator.batchExtractOcr(items, onlyMissing)

    fun writeOcrAndAiToMetadata(
        screenshot: ScreenshotItem,
        ocrText: String,
        aiTitle: String,
        aiDescription: String,
        tags: List<String>
    ) = aiOcrCoordinator.writeOcrAndAiToMetadata(screenshot, ocrText, aiTitle, aiDescription, tags)

    fun startIndexing(
        targetScreenshots: List<ScreenshotItem>? = null,
        onlyUnindexed: Boolean = true,
        autoWriteExif: Boolean = autoWriteExifSetting.value
    ) = aiOcrCoordinator.startIndexing(targetScreenshots, onlyUnindexed, autoWriteExif)

    fun cancelIndexing() = aiOcrCoordinator.cancelIndexing()

    fun batchAnalyzeScreenshots(screenshots: List<ScreenshotItem>, autoWriteExif: Boolean = autoWriteExifSetting.value) =
        aiOcrCoordinator.batchAnalyzeScreenshots(screenshots, autoWriteExif)

    fun retryFailedItems(autoWriteExif: Boolean = autoWriteExifSetting.value) =
        aiOcrCoordinator.retryFailedItems(autoWriteExif)

    fun saveProvider(provider: CustomCloudProvider, makeActive: Boolean = true) =
        providerBackupCoordinator.saveProvider(provider, makeActive)

    fun deleteProvider(provider: CustomCloudProvider) =
        providerBackupCoordinator.deleteProvider(provider)

    fun setActiveProvider(providerId: String) =
        providerBackupCoordinator.setActiveProvider(providerId)

    fun testProviderConnection(provider: CustomCloudProvider, onResult: (ConnectionTestResult) -> Unit) =
        providerBackupCoordinator.testProviderConnection(provider, onResult)

    fun fetchProviderModels(provider: CustomCloudProvider, onResult: (com.amresalehin.emreshots.service.ai.FetchModelsResult) -> Unit) =
        providerBackupCoordinator.fetchProviderModels(provider, onResult)

    fun exportBackupJson(): String = providerBackupCoordinator.exportBackupJson()

    suspend fun generateBackupJson(): String = providerBackupCoordinator.generateBackupJson()

    fun exportBackupToUri(context: Context, uri: Uri, onComplete: ((Result<Unit>) -> Unit)? = null) =
        providerBackupCoordinator.exportBackupToUri(context, uri, onComplete)

    fun shareBackup(context: Context) = providerBackupCoordinator.shareBackup(context)

    fun createBackupFile(onComplete: ((Result<File>) -> Unit)? = null) =
        providerBackupCoordinator.createBackupFile(onComplete)

    fun restoreFromUri(uri: Uri, mode: RestoreMode, onComplete: ((RestoreResult) -> Unit)? = null) =
        providerBackupCoordinator.restoreFromUri(uri, mode, onComplete)

    fun restoreFromJson(jsonString: String, mode: RestoreMode, onComplete: ((RestoreResult) -> Unit)? = null) =
        providerBackupCoordinator.restoreFromJson(jsonString, mode, onComplete)


    fun batchRename(items: List<ScreenshotItem>, template: String) =
        mediaLibraryCoordinator.batchRename(items, template)

    fun loadExif(screenshot: ScreenshotItem) = mediaLibraryCoordinator.loadExif(screenshot)
    fun clearPendingWriteIntent() = mediaLibraryCoordinator.clearPendingWriteIntent()
    fun onWriteConsentGranted() = mediaLibraryCoordinator.onWriteConsentGranted()
    fun onWriteConsentDenied() = mediaLibraryCoordinator.onWriteConsentDenied()
    fun saveExif(screenshot: ScreenshotItem, exifData: ExifData, onComplete: ((Boolean) -> Unit)? = null) =
        mediaLibraryCoordinator.saveExif(screenshot, exifData, onComplete)
    fun applyAiExif(screenshot: ScreenshotItem) = mediaLibraryCoordinator.applyAiExif(screenshot)

    fun toggleFavorite(screenshot: ScreenshotItem) = mediaLibraryCoordinator.toggleFavorite(screenshot)
    fun updateScreenshot(screenshot: ScreenshotItem) = mediaLibraryCoordinator.updateScreenshot(screenshot)
    fun deleteScreenshot(screenshot: ScreenshotItem) = mediaLibraryCoordinator.deleteScreenshot(screenshot)
    fun setReminder(screenshot: ScreenshotItem, timeMs: Long, text: String) =
        mediaLibraryCoordinator.setReminder(screenshot, timeMs, text)
    fun removeReminder(screenshot: ScreenshotItem) = mediaLibraryCoordinator.removeReminder(screenshot)
    fun addTag(screenshot: ScreenshotItem, tag: String) = mediaLibraryCoordinator.addTag(screenshot, tag)
    fun removeTag(screenshot: ScreenshotItem, tag: String) = mediaLibraryCoordinator.removeTag(screenshot, tag)
    fun addScreenshotToCollection(screenshotId: String, collectionId: String) =
        mediaLibraryCoordinator.addScreenshotToCollection(screenshotId, collectionId)
    fun removeScreenshotFromCollection(screenshotId: String, collectionId: String) =
        mediaLibraryCoordinator.removeScreenshotFromCollection(screenshotId, collectionId)
    fun createCollection(name: String, description: String, iconName: String, colorHex: String) =
        mediaLibraryCoordinator.createCollection(name, description, iconName, colorHex)
    fun deleteCollection(collectionId: String) = mediaLibraryCoordinator.deleteCollection(collectionId)
    fun importImageFromUri(uri: Uri) = mediaLibraryCoordinator.importImageFromUri(uri)
    fun importMediaFromUri(uri: Uri) = mediaLibraryCoordinator.importMediaFromUri(uri)

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


}
