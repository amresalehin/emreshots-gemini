package com.amresalehin.emreshots.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Database(
    entities = [ScreenshotItem::class, CollectionItem::class, CustomCloudProvider::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun screenshotDao(): ScreenshotDao
    abstract fun collectionDao(): CollectionDao
    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shots_studio.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getInstance(context)
                seedInitialData(database, context)
            }
        }
    }
}

suspend fun seedInitialData(database: AppDatabase, context: Context) {
    // 1. Seed Collections
    val collections = listOf(
        CollectionItem(
            id = "col-work-receipts",
            name = "Work & Receipts",
            description = "Expense receipts, invoices, and business purchases",
            iconName = "receipt",
            colorHex = "#3B82F6"
        ),
        CollectionItem(
            id = "col-design-inspo",
            name = "Design Inspiration",
            description = "UI patterns, color palettes, typography, and graphic layouts",
            iconName = "palette",
            colorHex = "#8B5CF6"
        ),
        CollectionItem(
            id = "col-code-dev",
            name = "Code & Dev",
            description = "Terminal logs, code snippets, API responses, and architecture diagrams",
            iconName = "code",
            colorHex = "#10B981"
        ),
        CollectionItem(
            id = "col-travel-tickets",
            name = "Travel & Tickets",
            description = "Flight bookings, hotel reservations, transit passes, and QR codes",
            iconName = "flight",
            colorHex = "#F59E0B"
        ),
        CollectionItem(
            id = "col-social-chat",
            name = "Social & Chat",
            description = "Memes, memorable conversation quotes, and social media posts",
            iconName = "chat",
            colorHex = "#EC4899"
        )
    )
    database.collectionDao().insertAll(collections)

    // Clean initial state: No preset AI providers. Users set up their own providers.

    // Remove any preset images and videos so the app starts completely clean
    try {
        database.screenshotDao().deleteById("sample-shot-1")
        database.screenshotDao().deleteById("sample-shot-2")
        database.screenshotDao().deleteById("sample-shot-3")
        database.screenshotDao().deleteById("sample-shot-4")

        val sampleImagesDir = File(context.filesDir, "sample_screenshots")
        if (sampleImagesDir.exists()) {
            sampleImagesDir.deleteRecursively()
        }
    } catch (_: Exception) {}
}
