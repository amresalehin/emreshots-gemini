package com.amresalehin.emreshots.data.repository

import com.amresalehin.emreshots.data.local.CollectionDao
import com.amresalehin.emreshots.data.local.ProviderDao
import com.amresalehin.emreshots.data.local.ScreenshotDao
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
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
    suspend fun getAllScreenshotsSync(): List<ScreenshotItem> = dao.getAllScreenshotsSync()
    suspend fun deleteAll() = dao.deleteAll()
}

class CollectionRepository(private val dao: CollectionDao) {
    val allCollections: Flow<List<CollectionItem>> = dao.getAllCollections()

    fun getCollection(id: String): Flow<CollectionItem?> = dao.getCollectionById(id)

    suspend fun insert(collection: CollectionItem) = dao.insert(collection)
    suspend fun insertAll(collections: List<CollectionItem>) = dao.insertAll(collections)
    suspend fun update(collection: CollectionItem) = dao.update(collection)
    suspend fun delete(collection: CollectionItem) = dao.delete(collection)
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun getAllCollectionsSync(): List<CollectionItem> = dao.getAllCollectionsSync()
    suspend fun deleteAll() = dao.deleteAll()
}

class ProviderRepository(private val dao: ProviderDao) {
    val allProviders: Flow<List<CustomCloudProvider>> = dao.getAllProviders()
    val activeProvider: Flow<CustomCloudProvider?> = dao.getActiveProvider()

    suspend fun getAllProvidersSync(): List<CustomCloudProvider> = dao.getAllProvidersList()
    suspend fun getActiveProviderSync(): CustomCloudProvider? = dao.getActiveProviderSync()

    suspend fun insert(provider: CustomCloudProvider) = dao.insert(provider)
    suspend fun insertAll(providers: List<CustomCloudProvider>) = dao.insertAll(providers)
    suspend fun update(provider: CustomCloudProvider) = dao.update(provider)
    suspend fun delete(provider: CustomCloudProvider) = dao.delete(provider)
    suspend fun deleteAll() = dao.deleteAll()

    suspend fun saveProvider(provider: CustomCloudProvider, makeActive: Boolean = true) {
        val currentActive = dao.getActiveProviderSync()
        val shouldBeActive = makeActive || provider.isActive || currentActive == null || currentActive.id == provider.id
        if (shouldBeActive) {
            dao.clearActiveProviders()
            dao.insert(provider.copy(isActive = true))
        } else {
            dao.insert(provider.copy(isActive = false))
        }
    }

    suspend fun setActiveProvider(providerId: String) {
        dao.clearActiveProviders()
        dao.setActiveProvider(providerId)
    }

    suspend fun deleteProviderWithFallback(provider: CustomCloudProvider) {
        val wasActive = provider.isActive
        dao.delete(provider)
        if (wasActive) {
            val remaining = dao.getAllProvidersList()
            if (remaining.isNotEmpty()) {
                dao.setActiveProvider(remaining.first().id)
            }
        }
    }
}
