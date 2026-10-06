package com.amresalehin.emreshots.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    private object Keys {
        val ocrEnabled = booleanPreferencesKey("ocr_enabled")
        val linksDetectionEnabled = booleanPreferencesKey("links_detection_enabled")
        val smartTagsEnabled = booleanPreferencesKey("smart_tags_enabled")
        val autoSyncDeviceMedia = booleanPreferencesKey("auto_sync_device_media")
        val aiQualityPreset = stringPreferencesKey("ai_quality_preset")
        val autoWriteExif = booleanPreferencesKey("auto_write_exif")
        val gridColumns = intPreferencesKey("grid_columns")
        val lastBackupInfo = stringPreferencesKey("last_backup_info")
        val onDeviceVisionMode = stringPreferencesKey("on_device_vision_mode")
        val onDeviceVisionModel = stringPreferencesKey("on_device_vision_model")
        val showFileNames = booleanPreferencesKey("gallery_show_file_names")
        val showTags = booleanPreferencesKey("gallery_show_tags")
    }

    val ocrEnabled: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.ocrEnabled] ?: true }
    val linksDetectionEnabled: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.linksDetectionEnabled] ?: true }
    val smartTagsEnabled: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.smartTagsEnabled] ?: true }
    val autoSyncDeviceMedia: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.autoSyncDeviceMedia] ?: true }
    val aiQualityPreset: Flow<String> = context.appPreferencesDataStore.data.map { it[Keys.aiQualityPreset] ?: "Balanced" }
    val autoWriteExif: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.autoWriteExif] ?: false }
    val gridColumns: Flow<Int> = context.appPreferencesDataStore.data.map { it[Keys.gridColumns] ?: 2 }
    val lastBackupInfo: Flow<String?> = context.appPreferencesDataStore.data.map { it[Keys.lastBackupInfo] }
    val onDeviceVisionMode: Flow<String> = context.appPreferencesDataStore.data.map { it[Keys.onDeviceVisionMode] ?: "Automatic" }
    val onDeviceVisionModel: Flow<String> = context.appPreferencesDataStore.data.map { it[Keys.onDeviceVisionModel] ?: "auto" }
    val showFileNames: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.showFileNames] ?: true }
    val showTags: Flow<Boolean> = context.appPreferencesDataStore.data.map { it[Keys.showTags] ?: true }

    suspend fun setOcrEnabled(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.ocrEnabled] = value }
    suspend fun setLinksDetectionEnabled(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.linksDetectionEnabled] = value }
    suspend fun setSmartTagsEnabled(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.smartTagsEnabled] = value }
    suspend fun setAutoSyncDeviceMedia(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.autoSyncDeviceMedia] = value }
    suspend fun setAiQualityPreset(value: String) = context.appPreferencesDataStore.edit { it[Keys.aiQualityPreset] = value }
    suspend fun setAutoWriteExif(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.autoWriteExif] = value }
    suspend fun setGridColumns(value: Int) = context.appPreferencesDataStore.edit { it[Keys.gridColumns] = value.coerceIn(2, 5) }
    suspend fun setShowFileNames(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.showFileNames] = value }
    suspend fun setShowTags(value: Boolean) = context.appPreferencesDataStore.edit { it[Keys.showTags] = value }
    suspend fun setOnDeviceVisionMode(value: String) = context.appPreferencesDataStore.edit { it[Keys.onDeviceVisionMode] = value }
    suspend fun setOnDeviceVisionModel(value: String) = context.appPreferencesDataStore.edit { it[Keys.onDeviceVisionModel] = value }
    suspend fun setLastBackupInfo(value: String?) = context.appPreferencesDataStore.edit { preferences ->
        if (value == null) preferences.remove(Keys.lastBackupInfo) else preferences[Keys.lastBackupInfo] = value
    }
}
