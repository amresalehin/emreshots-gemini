package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SecureApiKeyStore
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
import com.example.service.backup.BackupData
import com.example.service.backup.BackupRestoreManager
import com.example.service.backup.RestoreMode
import com.example.service.backup.RestoreResult
import com.example.service.exif.ExifMetadataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private val _hasMediaLocationPermission = MutableStateFlow(com.example.service.media.DeviceMediaScanner.hasMediaLocationPermission(application))
    val hasMediaLocationPermission: StateFlow<Boolean> = _hasMediaLocationPermission.asStateFlow()

    private val _hasAllMetadataPermissions = MutableStateFlow(com.example.service.media.DeviceMediaScanner.hasAllMetadataPermissions(application))
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
        .map { provider -> provider?.copy(apiKey = secureApiKeyStore.get(provider.id) ?: "") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStatusText = MutableStateFlow<String?>(null)
    val analysisStatusText: StateFlow<String?> = _analysisStatusText.asStateFlow()

    private val _indexingState = MutableStateFlow(IndexingState())
    val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()

    private var indexingJob: kotlinx.coroutines.Job? = null

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _exifDataState = MutableStateFlow<Map<String, ExifData>>(emptyMap())
    val exifDataState: StateFlow<Map<String, ExifData>> = _exifDataState.asStateFlow()

    private val prefs = application.getSharedPreferences("emreshots_settings", Context.MODE_PRIVATE)
    private val appPreferences = com.example.data.local.AppPreferences(application)

    // Settings are persisted in DataStore so they survive process death and are not tied to SharedPreferences.
    val ocrEnabled = appPreferences.ocrEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val linksDetectionEnabled = appPreferences.linksDetectionEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val smartTagsEnabled = appPreferences.smartTagsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val remindersDetectionEnabled = appPreferences.remindersDetectionEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoSyncDeviceMedia = appPreferences.autoSyncDeviceMedia.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val aiQualityPreset = appPreferences.aiQualityPreset.stateIn(viewModelScope, SharingStarted.Eagerly, "Balanced")
    val autoWriteExifSetting = appPreferences.autoWriteExif.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isLowEndDevice = com.example.service.perf.PerformanceManager.isLowEndDevice(application)
    val isLowRamDevice = com.example.service.perf.PerformanceManager.isLowRamDevice(application)
    val gridColumns = appPreferences.gridColumns.stateIn(viewModelScope, SharingStarted.Eagerly, if (isLowEndDevice) 2 else 2)

    private val _lastBackupInfo = MutableStateFlow(prefs.getString("last_backup_info", null))
    val lastBackupInfo: StateFlow<String?> = _lastBackupInfo.asStateFlow()

    val isExtractingOcr = MutableStateFlow(false)
    val ocrStatusText = MutableStateFlow<String?>(null)

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setOcrEnabled(enabled) }
    }
    fun setLinksDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLinksDetectionEnabled(enabled) }
    }
    fun setSmartTagsEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setSmartTagsEnabled(enabled) }
    }
    fun setRemindersDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setRemindersDetectionEnabled(enabled) }
    }
    fun setAutoSyncDeviceMedia(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setAutoSyncDeviceMedia(enabled) }
    }
    fun setAiQualityPreset(preset: String) {
        viewModelScope.launch { appPreferences.setAiQualityPreset(preset) }
    }
    fun setAutoWriteExifSetting(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setAutoWriteExif(enabled) }
    }
    fun setGridColumns(cols: Int) {
        viewModelScope.launch { appPreferences.setGridColumns(cols) }
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
        viewModelScope.launch(Dispatchers.IO) {
            seedInitialData(database, getApplication())
            migrateProviderApiKeysToKeystore()
            ensureDefaultProviderIfNeeded()
            // Ensure that if providers exist but none is currently marked active, activate the first one
            val active = providerRepository.getActiveProviderSync()
            if (active == null) {
                val all = providerRepository.getAllProvidersSync()
                if (all.isNotEmpty()) {
                    providerRepository.setActiveProvider(all.first().id)
                }
            }
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

    private suspend fun ensureDefaultProviderIfNeeded() {
        val existing = providerRepository.getAllProvidersSync()
        if (existing.isEmpty()) {
            val defaultProvider = CustomCloudProvider(
                id = "default-gemini",
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com",
                selectedModel = "gemini-2.5-flash",
                isActive = true,
                isDefaultGemini = true
            )
            providerRepository.saveProvider(defaultProvider, makeActive = true)
        }
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        _hasMediaPermissions.value = com.example.service.media.DeviceMediaScanner.hasPermissions(app)
        _hasMediaLocationPermission.value = com.example.service.media.DeviceMediaScanner.hasMediaLocationPermission(app)
        _hasAllMetadataPermissions.value = com.example.service.media.DeviceMediaScanner.hasAllMetadataPermissions(app)
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
        autoWriteExif: Boolean
    ): AiAnalysisResult {
        val provider = activeProvider.value ?: return AiAnalysisResult(
            isSuccess = false,
            errorMessage = "No AI provider configured. Please set up your endpoint in Settings."
        )

        val file = resolveImageFile(screenshot)
        val geminiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
        } catch (_: Exception) { "" }

        val result = aiService.analyzeScreenshot(
            imageFile = file,
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
        }
        return result
    }

    fun analyzeScreenshot(
        screenshot: ScreenshotItem,
        autoWriteExif: Boolean = autoWriteExifSetting.value,
        onComplete: ((AiAnalysisResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = activeProvider.value
            if (provider == null) {
                _snackbarMessage.value = "No AI provider configured. Please set up your endpoint in Settings."
                val errorResult = AiAnalysisResult(isSuccess = false, errorMessage = "No AI provider configured. Please set up your endpoint in Settings.")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(errorResult)
                }
                return@launch
            }

            _isAnalyzing.value = true
            _analysisStatusText.value = "Analyzing with ${provider.name}..."

            val result = analyzeScreenshotInternal(screenshot, autoWriteExif)

            if (result.isSuccess) {
                _snackbarMessage.value = "AI Analysis complete via ${result.modelUsed} (${result.processingTimeMs}ms)!"
            } else {
                _snackbarMessage.value = "AI Analysis failed: ${result.errorMessage ?: "Unknown error"}"
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
            val provider = activeProvider.value
            if (provider == null) {
                _snackbarMessage.value = "No AI provider configured. Please set up your endpoint in Settings."
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(null)
                }
                return@launch
            }

            isExtractingOcr.value = true
            ocrStatusText.value = "Extracting text from image..."

            val file = resolveImageFile(screenshot)
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
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(ocr)
                }
            } else {
                _snackbarMessage.value = "OCR failed: ${result.exceptionOrNull()?.message}"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(null)
                }
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
            val provider = activeProvider.value
            if (provider == null) {
                _snackbarMessage.value = "No AI provider configured. Please set up your endpoint in Settings."
                onComplete?.invoke(AiAnalysisResult(isSuccess = false, errorMessage = "No AI provider configured."))
                return@launch
            }

            _isAnalyzing.value = true
            _analysisStatusText.value = "Processing OCR text with AI..."

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

        val provider = activeProvider.value
        if (provider == null) {
            _snackbarMessage.value = "No AI provider configured. Please set up your endpoint in Settings."
            return
        }

        indexingJob?.cancel()
        indexingJob = viewModelScope.launch(Dispatchers.IO) {
            val total = items.size
            val modelName = provider.selectedModel.ifBlank { provider.name }
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
                }

                _indexingState.value = _indexingState.value.copy(
                    progress = (index + 1).toFloat() / total,
                    successCount = successes,
                    failureCount = failures
                )
            }

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
            val updated = provider.copy(
                isDefaultGemini = isGemini || provider.isDefaultGemini
            )
            if (provider.apiKey.isNotBlank()) {
                secureApiKeyStore.put(provider.id, provider.apiKey)
            }
            providerRepository.saveProvider(updated.copy(apiKey = ""), makeActive)
            val action = if (makeActive) "saved & activated" else "saved"
            _snackbarMessage.value = "AI Provider '${updated.name}' $action!"
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
            providerRepository.setActiveProvider(providerId)
            val p = providers.value.find { it.id == providerId }
            _snackbarMessage.value = "Active AI Provider set to: ${p?.name}"
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

    fun fetchProviderModels(provider: CustomCloudProvider, onResult: (com.example.service.ai.FetchModelsResult) -> Unit) {
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
            "reminders_detection_enabled" to remindersDetectionEnabled.value.toString(),
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
                        _lastBackupInfo.value = info
                        prefs.edit().putString("last_backup_info", info).apply()
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
                    _lastBackupInfo.value = info
                    prefs.edit().putString("last_backup_info", info).apply()
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

            // Restore providers
            if (data.providers.isNotEmpty()) {
                data.providers.forEach { provider ->
                    if (provider.apiKey.isNotBlank()) {
                        secureApiKeyStore.put(provider.id, provider.apiKey)
                    }
                }
                providerRepository.insertAll(data.providers.map { it.copy(apiKey = "") })
                val active = providerRepository.getActiveProviderSync()
                if (active == null) {
                    providerRepository.setActiveProvider(data.providers.first().id)
                }
            } else {
                ensureDefaultProviderIfNeeded()
            }

            // Restore settings
            var settingsRestored = 0
            data.settings["ocr_enabled"]?.toBooleanStrictOrNull()?.let { setOcrEnabled(it); settingsRestored++ }
            data.settings["links_detection_enabled"]?.toBooleanStrictOrNull()?.let { setLinksDetectionEnabled(it); settingsRestored++ }
            data.settings["smart_tags_enabled"]?.toBooleanStrictOrNull()?.let { setSmartTagsEnabled(it); settingsRestored++ }
            data.settings["reminders_detection_enabled"]?.toBooleanStrictOrNull()?.let { setRemindersDetectionEnabled(it); settingsRestored++ }
            data.settings["auto_sync_device_media"]?.toBooleanStrictOrNull()?.let { setAutoSyncDeviceMedia(it); settingsRestored++ }
            data.settings["ai_quality_preset"]?.let { setAiQualityPreset(it); settingsRestored++ }
            data.settings["auto_write_exif"]?.toBooleanStrictOrNull()?.let { setAutoWriteExifSetting(it); settingsRestored++ }
            data.settings["grid_columns"]?.toIntOrNull()?.let { setGridColumns(it); settingsRestored++ }

            val totalRestored = data.screenshots.size
            val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
            val info = "Restored: $timeStr ($totalRestored items)"
            _lastBackupInfo.value = info
            prefs.edit().putString("last_backup_info", info).apply()

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
