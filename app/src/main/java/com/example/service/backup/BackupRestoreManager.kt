package com.amresalehin.emreshots.service.backup

import android.content.Context
import android.net.Uri
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class RestoreMode {
    MERGE,
    REPLACE
}

data class BackupData(
    val app: String = "EmreShots",
    val schemaVersion: Int = 1,
    val backupDate: Long = System.currentTimeMillis(),
    val screenshots: List<ScreenshotItem> = emptyList(),
    val collections: List<CollectionItem> = emptyList(),
    val providers: List<CustomCloudProvider> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

data class RestoreResult(
    val isSuccess: Boolean,
    val screenshotsRestored: Int = 0,
    val collectionsRestored: Int = 0,
    val providersRestored: Int = 0,
    val settingsRestored: Int = 0,
    val message: String = ""
)

object BackupRestoreManager {

    fun createBackupJson(
        screenshots: List<ScreenshotItem>,
        collections: List<CollectionItem>,
        providers: List<CustomCloudProvider>,
        settings: Map<String, String>
    ): String {
        val root = JSONObject()
        root.put("app", "EmreShots")
        root.put("schemaVersion", 1)
        root.put("backupDate", System.currentTimeMillis())
        root.put("backupDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Collections
        val collectionsArray = JSONArray()
        collections.forEach { col ->
            val obj = JSONObject().apply {
                put("id", col.id)
                put("name", col.name)
                put("description", col.description)
                put("iconName", col.iconName)
                put("colorHex", col.colorHex)
                put("createdAt", col.createdAt)
            }
            collectionsArray.put(obj)
        }
        root.put("collections", collectionsArray)

        // Screenshots
        val screenshotsArray = JSONArray()
        screenshots.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("filePath", item.filePath)
                put("uriString", item.uriString ?: "")
                put("mediaType", item.mediaType)
                put("durationMs", item.durationMs)
                put("title", item.title)
                put("description", item.description)
                put("tags", JSONArray(item.tags))
                put("links", JSONArray(item.links))
                put("collectionIds", JSONArray(item.collectionIds))
                put("aiProcessed", item.aiProcessed)
                put("addedOn", item.addedOn)
                put("fileSize", item.fileSize)
                put("isFavorite", item.isFavorite)
                if (item.reminderTime != null) put("reminderTime", item.reminderTime)
                if (item.reminderText != null) put("reminderText", item.reminderText)
                if (item.notes != null) put("notes", item.notes)
                if (item.ocrText != null) put("ocrText", item.ocrText)
                if (item.aiModelUsed != null) put("aiModelUsed", item.aiModelUsed)
                put("width", item.width)
                put("height", item.height)
            }
            screenshotsArray.put(obj)
        }
        root.put("screenshots", screenshotsArray)

        // Providers
        val providersArray = JSONArray()
        providers.forEach { prov ->
            val obj = JSONObject().apply {
                put("id", prov.id)
                put("name", prov.name)
                put("baseUrl", prov.baseUrl)
                put("apiKey", prov.apiKey)
                put("selectedModel", prov.selectedModel)
                put("customHeadersJson", prov.customHeadersJson)
                put("timeoutSeconds", prov.timeoutSeconds)
                put("isActive", prov.isActive)
                put("isDefaultGemini", prov.isDefaultGemini)
                if (prov.lastTestedTime != null) put("lastTestedTime", prov.lastTestedTime)
                if (prov.lastTestStatus != null) put("lastTestStatus", prov.lastTestStatus)
            }
            providersArray.put(obj)
        }
        root.put("providers", providersArray)

        // Settings
        val settingsObj = JSONObject()
        settings.forEach { (k, v) ->
            settingsObj.put(k, v)
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schemaVersion", 1)
        val backupDate = root.optLong("backupDate", System.currentTimeMillis())

        val collections = mutableListOf<CollectionItem>()
        val colArray = root.optJSONArray("collections")
        if (colArray != null) {
            for (i in 0 until colArray.length()) {
                val obj = colArray.optJSONObject(i) ?: continue
                collections.add(
                    CollectionItem(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name", "Restored Collection"),
                        description = obj.optString("description", ""),
                        iconName = obj.optString("iconName", "folder"),
                        colorHex = obj.optString("colorHex", "#4A4641"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val screenshots = mutableListOf<ScreenshotItem>()
        val shotArray = root.optJSONArray("screenshots")
        if (shotArray != null) {
            for (i in 0 until shotArray.length()) {
                val obj = shotArray.optJSONObject(i) ?: continue

                val tags = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(j)
                        if (!t.isNullOrBlank()) tags.add(t)
                    }
                }

                val links = mutableListOf<String>()
                val linksArr = obj.optJSONArray("links")
                if (linksArr != null) {
                    for (j in 0 until linksArr.length()) {
                        val l = linksArr.optString(j)
                        if (!l.isNullOrBlank()) links.add(l)
                    }
                }

                val colIds = mutableListOf<String>()
                val colIdsArr = obj.optJSONArray("collectionIds")
                if (colIdsArr != null) {
                    for (j in 0 until colIdsArr.length()) {
                        val c = colIdsArr.optString(j)
                        if (!c.isNullOrBlank()) colIds.add(c)
                    }
                }

                screenshots.add(
                    ScreenshotItem(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        filePath = obj.optString("filePath", ""),
                        uriString = obj.optString("uriString").takeIf { it.isNotBlank() },
                        mediaType = obj.optString("mediaType", "PHOTO"),
                        durationMs = obj.optLong("durationMs", 0L),
                        title = obj.optString("title", "Restored Item"),
                        description = obj.optString("description", ""),
                        tags = tags,
                        links = links,
                        collectionIds = colIds,
                        aiProcessed = obj.optBoolean("aiProcessed", false),
                        addedOn = obj.optLong("addedOn", System.currentTimeMillis()),
                        fileSize = obj.optLong("fileSize", 0L),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        reminderTime = if (obj.has("reminderTime")) obj.optLong("reminderTime") else null,
                        reminderText = if (obj.has("reminderText")) obj.optString("reminderText") else null,
                        notes = if (obj.has("notes")) obj.optString("notes") else null,
                        ocrText = if (obj.has("ocrText")) obj.optString("ocrText") else null,
                        aiModelUsed = if (obj.has("aiModelUsed")) obj.optString("aiModelUsed") else null,
                        width = obj.optInt("width", 0),
                        height = obj.optInt("height", 0)
                    )
                )
            }
        }

        val providers = mutableListOf<CustomCloudProvider>()
        val provArray = root.optJSONArray("providers")
        if (provArray != null) {
            for (i in 0 until provArray.length()) {
                val obj = provArray.optJSONObject(i) ?: continue
                providers.add(
                    CustomCloudProvider(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name", "Restored Provider"),
                        baseUrl = obj.optString("baseUrl", "https://api.openai.com/v1"),
                        apiKey = obj.optString("apiKey", ""),
                        selectedModel = obj.optString("selectedModel", "gpt-4o-mini"),
                        customHeadersJson = obj.optString("customHeadersJson", "{}"),
                        timeoutSeconds = obj.optInt("timeoutSeconds", 60),
                        isActive = obj.optBoolean("isActive", false),
                        isDefaultGemini = obj.optBoolean("isDefaultGemini", false),
                        lastTestedTime = if (obj.has("lastTestedTime")) obj.optLong("lastTestedTime") else null,
                        lastTestStatus = if (obj.has("lastTestStatus")) obj.optString("lastTestStatus") else null
                    )
                )
            }
        }

        val settings = mutableMapOf<String, String>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                settings[key] = settingsObj.optString(key, "")
            }
        }

        return BackupData(
            schemaVersion = schemaVersion,
            backupDate = backupDate,
            screenshots = screenshots,
            collections = collections,
            providers = providers,
            settings = settings
        )
    }

    fun writeBackupToUri(context: Context, uri: Uri, backupJson: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write(backupJson)
                    writer.flush()
                }
            } ?: return Result.failure(IllegalStateException("Could not open output stream for URI"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readBackupFromUri(context: Context, uri: Uri): Result<BackupData> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: return Result.failure(IllegalStateException("Could not open input stream for URI"))
            Result.success(parseBackupJson(jsonString))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createLocalBackupFile(context: Context, backupJson: String): Result<File> {
        return try {
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "EmreShots_Backup_${dateFormat.format(Date())}.json"
            val file = File(backupDir, filename)
            file.writeText(backupJson, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
