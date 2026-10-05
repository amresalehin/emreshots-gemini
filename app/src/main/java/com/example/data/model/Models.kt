package com.amresalehin.emreshots.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "screenshots")
data class ScreenshotItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val filePath: String = "",
    val uriString: String? = null,
    val mediaType: String = "PHOTO", // "PHOTO" or "VIDEO"
    val durationMs: Long = 0L,
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val collectionIds: List<String> = emptyList(),
    val aiProcessed: Boolean = false,
    val addedOn: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,
    val isFavorite: Boolean = false,
    val reminderTime: Long? = null,
    val reminderText: String? = null,
    val notes: String? = null,
    val ocrText: String? = null,
    val aiModelUsed: String? = null,
    val width: Int = 0,
    val height: Int = 0
) {
    val isVideo: Boolean get() = mediaType.equals("VIDEO", ignoreCase = true)
}

@Entity(tableName = "collections")
data class CollectionItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconName: String = "folder",
    val colorHex: String = "#6366F1",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cloud_providers")
data class CustomCloudProvider(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val selectedModel: String = "gpt-4o-mini",
    val customHeadersJson: String = "{}",
    val timeoutSeconds: Int = 60,
    val isActive: Boolean = false,
    val isDefaultGemini: Boolean = false,
    val lastTestedTime: Long? = null,
    val lastTestStatus: String? = null
)
