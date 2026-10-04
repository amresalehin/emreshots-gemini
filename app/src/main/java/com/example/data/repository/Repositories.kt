package com.example.data.repository

import com.example.data.local.CollectionDao
import com.example.data.local.ProviderDao
import com.example.data.local.ScreenshotDao
import com.example.data.model.CollectionItem
import com.example.data.model.CustomCloudProvider
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.flow.Flow

class ScreenshotRepository(private val dao: ScreenshotDao) {
    val allScreenshots: Flow<List<ScreenshotItem>> = dao.getAllScreenshots()
    val reminders: Flow<List<ScreenshotItem>> = dao.getScreenshotsWithReminders()

    fun getScreenshot(id: String): Flow<ScreenshotItem?> = dao.getScreenshotById(id)
    suspend fun getScreenshotSync(id: String): ScreenshotItem? = dao.getScreenshotByIdSync(id)

    fun searchScreenshots(query: String): Flow<List<ScreenshotItem>> = dao.searchScreenshots(query)

    suspend fun insert(item: ScreenshotItem) = dao.insert(item)
    suspend fun insertAll(items: List<ScreenshotItem>) = dao.insertAll(items)
    suspend fun update(item: ScreenshotItem) = dao.update(item)
    suspend fun delete(item: ScreenshotItem) = dao.delete(item)
    suspend fun deleteById(id: String) = dao.deleteById(id)
}

class CollectionRepository(private val dao: CollectionDao) {
    val allCollections: Flow<List<CollectionItem>> = dao.getAllCollections()

    fun getCollection(id: String): Flow<CollectionItem?> = dao.getCollectionById(id)

    suspend fun insert(collection: CollectionItem) = dao.insert(collection)
    suspend fun update(collection: CollectionItem) = dao.update(collection)
    suspend fun delete(collection: CollectionItem) = dao.delete(collection)
    suspend fun deleteById(id: String) = dao.deleteById(id)
}

class ProviderRepository(private val dao: ProviderDao) {
    val allProviders: Flow<List<CustomCloudProvider>> = dao.getAllProviders()
    val activeProvider: Flow<CustomCloudProvider?> = dao.getActiveProvider()

    suspend fun getActiveProviderSync(): CustomCloudProvider? = dao.getActiveProviderSync()

    suspend fun insert(provider: CustomCloudProvider) = dao.insert(provider)
    suspend fun update(provider: CustomCloudProvider) = dao.update(provider)
    suspend fun delete(provider: CustomCloudProvider) = dao.delete(provider)

    suspend fun setActiveProvider(providerId: String) {
        dao.clearActiveProviders()
        dao.setActiveProvider(providerId)
    }
}
