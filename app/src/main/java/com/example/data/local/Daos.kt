package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CollectionItem
import com.example.data.model.CustomCloudProvider
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots ORDER BY addedOn DESC")
    fun getAllScreenshots(): Flow<List<ScreenshotItem>>

    @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
    fun getScreenshotById(id: String): Flow<ScreenshotItem?>

    @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
    suspend fun getScreenshotByIdSync(id: String): ScreenshotItem?

    @Query("SELECT * FROM screenshots WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY addedOn DESC")
    fun searchScreenshots(query: String): Flow<List<ScreenshotItem>>

    @Query("SELECT * FROM screenshots WHERE reminderTime IS NOT NULL ORDER BY reminderTime ASC")
    fun getScreenshotsWithReminders(): Flow<List<ScreenshotItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screenshot: ScreenshotItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(screenshots: List<ScreenshotItem>)

    @Update
    suspend fun update(screenshot: ScreenshotItem)

    @Delete
    suspend fun delete(screenshot: ScreenshotItem)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt ASC")
    fun getAllCollections(): Flow<List<CollectionItem>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): Flow<CollectionItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionItem>)

    @Update
    suspend fun update(collection: CollectionItem)

    @Delete
    suspend fun delete(collection: CollectionItem)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM cloud_providers ORDER BY isDefaultGemini DESC, name ASC")
    fun getAllProviders(): Flow<List<CustomCloudProvider>>

    @Query("SELECT * FROM cloud_providers WHERE isActive = 1 LIMIT 1")
    fun getActiveProvider(): Flow<CustomCloudProvider?>

    @Query("SELECT * FROM cloud_providers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProviderSync(): CustomCloudProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: CustomCloudProvider)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<CustomCloudProvider>)

    @Update
    suspend fun update(provider: CustomCloudProvider)

    @Delete
    suspend fun delete(provider: CustomCloudProvider)

    @Query("UPDATE cloud_providers SET isActive = 0")
    suspend fun clearActiveProviders()

    @Query("UPDATE cloud_providers SET isActive = 1 WHERE id = :providerId")
    suspend fun setActiveProvider(providerId: String)
}
