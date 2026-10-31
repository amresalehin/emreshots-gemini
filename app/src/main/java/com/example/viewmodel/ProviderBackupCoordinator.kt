package com.amresalehin.emreshots.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.amresalehin.emreshots.data.local.AppPreferences
import com.amresalehin.emreshots.data.local.SecureApiKeyStore
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.ProviderRepository
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import com.amresalehin.emreshots.data.repository.CollectionRepository
import com.amresalehin.emreshots.service.ai.CloudAiService
import com.amresalehin.emreshots.service.ai.ConnectionTestResult
import com.amresalehin.emreshots.service.backup.BackupData
import com.amresalehin.emreshots.service.backup.BackupRestoreManager
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.service.backup.RestoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProviderBackupCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val providerRepository: ProviderRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val collectionRepository: CollectionRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val appPreferences: AppPreferences,
    private val aiService: CloudAiService,
    private val allScreenshots: kotlinx.coroutines.flow.StateFlow<List<ScreenshotItem>>,
    private val collections: kotlinx.coroutines.flow.StateFlow<List<CollectionItem>>,
    private val providers: kotlinx.coroutines.flow.StateFlow<List<CustomCloudProvider>>,
    private val ocrEnabled: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val linksDetectionEnabled: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val smartTagsEnabled: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val autoSyncDeviceMedia: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val aiQualityPreset: kotlinx.coroutines.flow.StateFlow<String>,
    private val autoWriteExifSetting: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val gridColumns: kotlinx.coroutines.flow.StateFlow<Int>,
    private val setOcrEnabled: (Boolean) -> Unit,
    private val setLinksDetectionEnabled: (Boolean) -> Unit,
    private val setSmartTagsEnabled: (Boolean) -> Unit,
    private val setAutoSyncDeviceMedia: (Boolean) -> Unit,
    private val setAiQualityPreset: (String) -> Unit,
    private val setAutoWriteExifSetting: (Boolean) -> Unit,
    private val setGridColumns: (Int) -> Unit,
    private val ensureConfiguredProviderActiveIfNeeded: suspend () -> Unit,
    private val onMessage: (String) -> Unit
) {    private val _snackbarMessage = object {
        var value: String? = null
            set(newValue) {
                field = newValue
                if (!newValue.isNullOrBlank()) onMessage(newValue)
            }
    }


    fun saveProvider(provider: CustomCloudProvider, makeActive: Boolean = true) {
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
            providerRepository.deleteProviderWithFallback(provider)
            secureApiKeyStore.remove(provider.id)
            _snackbarMessage.value = "Provider '${provider.name}' removed"
        }
    }

    fun setActiveProvider(providerId: String) {
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                val writeResult = BackupRestoreManager.writeBackupToUri(context, uri, json)
                withContext(Dispatchers.Main) {
                    if (writeResult.isSuccess) {
                        val count = allScreenshots.value.size
                        val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
                        val info = "Last export: $timeStr ($count items)"
                        scope.launch { appPreferences.setLastBackupInfo(info) }
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
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                val result = BackupRestoreManager.createLocalBackupFile(application, json)
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val count = allScreenshots.value.size
                    val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
                    val info = "Last backup: $timeStr ($count items)"
                    scope.launch { appPreferences.setLastBackupInfo(info) }
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
        scope.launch(Dispatchers.IO) {
            val readResult = BackupRestoreManager.readBackupFromUri(application, uri)
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
        scope.launch(Dispatchers.IO) {
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
            data.settings["ocr_enabled"]?.toBooleanStrictOrNull()?.let { this.setOcrEnabled(it); settingsRestored++ }
            data.settings["links_detection_enabled"]?.toBooleanStrictOrNull()?.let { this.setLinksDetectionEnabled(it); settingsRestored++ }
            data.settings["smart_tags_enabled"]?.toBooleanStrictOrNull()?.let { this.setSmartTagsEnabled(it); settingsRestored++ }
            data.settings["auto_sync_device_media"]?.toBooleanStrictOrNull()?.let { this.setAutoSyncDeviceMedia(it); settingsRestored++ }
            data.settings["ai_quality_preset"]?.let { this.setAiQualityPreset(it); settingsRestored++ }
            data.settings["auto_write_exif"]?.toBooleanStrictOrNull()?.let { this.setAutoWriteExifSetting(it); settingsRestored++ }
            data.settings["grid_columns"]?.toIntOrNull()?.let { this.setGridColumns(it); settingsRestored++ }

            val totalRestored = data.screenshots.size
            val timeStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
            val info = "Restored: $timeStr ($totalRestored items)"
            scope.launch { appPreferences.setLastBackupInfo(info) }

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
