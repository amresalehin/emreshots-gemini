package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CollectionItem
import com.example.data.model.CustomCloudProvider
import com.example.data.model.ScreenshotItem
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

    // 2. Seed Custom Cloud Providers
    val providers = listOf(
        CustomCloudProvider(
            id = "prov-gemini-cloud",
            name = "Google Gemini (Official)",
            baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = "",
            selectedModel = "gemini-2.5-flash",
            customHeadersJson = "{}",
            timeoutSeconds = 60,
            isActive = true,
            isDefaultGemini = true,
            lastTestStatus = "Active Google AI Studio endpoint"
        ),
        CustomCloudProvider(
            id = "prov-ollama-local",
            name = "Ollama Local (vLLM)",
            baseUrl = "http://10.0.2.2:11434/v1",
            apiKey = "",
            selectedModel = "llama3.2-vision",
            customHeadersJson = "{}",
            timeoutSeconds = 90,
            isActive = false,
            isDefaultGemini = false,
            lastTestStatus = "Local emulator loopback"
        ),
        CustomCloudProvider(
            id = "prov-groq-cloud",
            name = "Groq Cloud (Fast Vision)",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "",
            selectedModel = "llama-3.2-11b-vision-preview",
            customHeadersJson = "{}",
            timeoutSeconds = 30,
            isActive = false,
            isDefaultGemini = false,
            lastTestStatus = "Ultra low-latency LPU backend"
        ),
        CustomCloudProvider(
            id = "prov-openrouter",
            name = "OpenRouter Gateway",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "",
            selectedModel = "google/gemini-2.5-flash",
            customHeadersJson = "{\"HTTP-Referer\":\"https://shotsstudio.app\"}",
            timeoutSeconds = 60,
            isActive = false,
            isDefaultGemini = false,
            lastTestStatus = "Unified multi-model aggregator"
        )
    )
    database.providerDao().insertAll(providers)

    // 3. Create sample images with real files in internal storage so EXIF reading and writing can be tested & used immediately
    try {
        val sampleImagesDir = File(context.filesDir, "sample_screenshots").apply { mkdirs() }

        val sample1File = File(sampleImagesDir, "receipt_stripe_payment.jpg")
        val sample2File = File(sampleImagesDir, "compose_ui_redesign.jpg")
        val sample3File = File(sampleImagesDir, "flight_boarding_pass.jpg")

        createSampleBitmapFile(sample1File, 1080, 1920, 0xFF1E293B.toInt())
        createSampleBitmapFile(sample2File, 1080, 2400, 0xFF312E81.toInt())
        createSampleBitmapFile(sample3File, 1080, 1920, 0xFF065F46.toInt())

        val now = System.currentTimeMillis()
        val sampleScreenshots = listOf(
            ScreenshotItem(
                id = "sample-shot-1",
                filePath = sample1File.absolutePath,
                title = "Stripe Cloud Services Invoice #4092",
                description = "Monthly invoice statement for Cloud API backend server cluster with breakdown of usage tier and billing details.",
                tags = listOf("Invoice", "Receipt", "Stripe", "Finance", "Cloud"),
                links = listOf("https://dashboard.stripe.com/invoices/inv_4092", "+1-800-555-0199"),
                collectionIds = listOf("col-work-receipts"),
                aiProcessed = true,
                addedOn = now - 3600000 * 2,
                fileSize = sample1File.length(),
                isFavorite = true,
                aiModelUsed = "gemini-2.5-flash",
                width = 1080,
                height = 1920
            ),
            ScreenshotItem(
                id = "sample-shot-2",
                filePath = sample2File.absolutePath,
                title = "Modern Material 3 Navigation Spec",
                description = "Jetpack Compose design system reference showcasing glassmorphic navigation bars, cards, and expressive typography.",
                tags = listOf("Design", "UI/UX", "Material3", "Android", "Inspiration"),
                links = listOf("https://m3.material.io/components/navigation-bar/overview"),
                collectionIds = listOf("col-design-inspo", "col-code-dev"),
                aiProcessed = true,
                addedOn = now - 3600000 * 5,
                fileSize = sample2File.length(),
                isFavorite = true,
                reminderTime = now + 86400000L,
                reminderText = "Review Compose adaptive layout implementation",
                aiModelUsed = "gemini-2.5-flash",
                width = 1080,
                height = 2400
            ),
            ScreenshotItem(
                id = "sample-shot-3",
                filePath = sample3File.absolutePath,
                title = "SFO to HND Flight Boarding Pass",
                description = "Flight reservation confirmation and gate departure terminal pass with flight code NH107 departing at 11:45 AM.",
                tags = listOf("Travel", "Flight", "Tickets", "Airport", "Tokyo"),
                links = listOf("https://ana.co.jp/booking/NH107"),
                collectionIds = listOf("col-travel-tickets"),
                aiProcessed = true,
                addedOn = now - 3600000 * 24,
                fileSize = sample3File.length(),
                isFavorite = false,
                aiModelUsed = "gemini-2.5-flash",
                width = 1080,
                height = 1920
            ),
            ScreenshotItem(
                id = "sample-shot-4",
                filePath = sample1File.absolutePath,
                mediaType = "VIDEO",
                durationMs = 42000L, // 42 seconds
                title = "App Architecture Demo Recording",
                description = "Screen recording walkthrough demonstrating Jetpack Compose state management and offline Room syncing.",
                tags = listOf("Video", "Demo", "Android", "Tutorial"),
                links = listOf("https://developer.android.com/jetpack/compose"),
                collectionIds = listOf("col-code-dev"),
                aiProcessed = true,
                addedOn = now - 3600000 * 8,
                fileSize = 14500000L,
                isFavorite = true,
                aiModelUsed = "gemini-2.5-flash",
                width = 1080,
                height = 1920
            )
        )

        database.screenshotDao().insertAll(sampleScreenshots)
    } catch (_: Exception) {}
}

fun createSampleBitmapFile(file: File, width: Int, height: Int, color: Int) {
    if (file.exists() && file.length() > 0) return
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(color)

    // Draw some stylized placeholder lines to make it look like a real document/screenshot
    val paint = android.graphics.Paint().apply {
        this.color = 0xFFFFFFFF.toInt()
        this.isAntiAlias = true
        this.textSize = 48f
    }
    canvas.drawText("Shots Studio - " + file.nameWithoutExtension, 60f, 150f, paint)

    FileOutputStream(file).use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
    }
}
