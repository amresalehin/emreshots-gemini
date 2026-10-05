package com.amresalehin.emreshots

import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.ai.CloudAiService
import com.amresalehin.emreshots.service.backup.BackupRestoreManager
import com.amresalehin.emreshots.service.backup.RestoreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreAndModelDiscoveryTest {

    @Test
    fun testBackupJsonCreationAndParsing() {
        val screenshots = listOf(
            ScreenshotItem(
                id = "shot-1",
                filePath = "/storage/emulated/0/Pictures/Screenshots/test.png",
                uriString = "content://media/1",
                title = "Order Confirmation",
                description = "Receipt for item",
                tags = listOf("Receipt", "Finance"),
                links = listOf("https://store.example.com/order/123"),
                ocrText = "Total: $99.99",
                aiProcessed = true,
                collectionIds = listOf("col-1")
            )
        )
        val collections = listOf(
            CollectionItem(
                id = "col-1",
                name = "Finances",
                description = "Bank & purchase screenshots",
                iconName = "Receipt"
            )
        )
        val providers = listOf(
            CustomCloudProvider(
                id = "prov-groq",
                name = "Groq Cloud",
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = "gsk_test123",
                selectedModel = "llama-3.2-11b-vision-preview",
                isActive = true
            )
        )
        val settings = mapOf(
            "ocr_enabled" to "true",
            "ai_quality_preset" to "Ultra (Max Detail)",
            "grid_columns" to "3"
        )

        val json = BackupRestoreManager.createBackupJson(
            screenshots = screenshots,
            collections = collections,
            providers = providers,
            settings = settings
        )

        assertNotNull(json)
        assertTrue(json.contains("Order Confirmation"))
        assertTrue(json.contains("Finances"))
        assertTrue(json.contains("Groq Cloud"))
        assertTrue(json.contains("gsk_test123"))

        val parsed = BackupRestoreManager.parseBackupJson(json)
        assertEquals("EmreShots", parsed.app)
        assertEquals(1, parsed.schemaVersion)
        assertEquals(1, parsed.screenshots.size)
        assertEquals("shot-1", parsed.screenshots[0].id)
        assertEquals("Order Confirmation", parsed.screenshots[0].title)
        assertEquals(listOf("Receipt", "Finance"), parsed.screenshots[0].tags)
        assertEquals(1, parsed.collections.size)
        assertEquals("col-1", parsed.collections[0].id)
        assertEquals("Finances", parsed.collections[0].name)
        assertEquals(1, parsed.providers.size)
        assertEquals("prov-groq", parsed.providers[0].id)
        assertEquals("Groq Cloud", parsed.providers[0].name)
        assertEquals("gsk_test123", parsed.providers[0].apiKey)
        assertEquals("true", parsed.settings["ocr_enabled"])
        assertEquals("Ultra (Max Detail)", parsed.settings["ai_quality_preset"])
    }

    @Test
    fun testParseOpenAiModelsList() {
        val openAiJson = """
            {
              "object": "list",
              "data": [
                { "id": "gpt-4o", "object": "model" },
                { "id": "gpt-4o-mini", "object": "model" },
                { "id": "text-embedding-3-small", "object": "model" }
              ]
            }
        """.trimIndent()

        val service = CloudAiService()
        val models = service.parseModelsList(openAiJson)
        assertTrue(models.contains("gpt-4o"))
        assertTrue(models.contains("gpt-4o-mini"))
        assertTrue(models.contains("text-embedding-3-small"))
    }

    @Test
    fun testParseGeminiModelsList() {
        val geminiJson = """
            {
              "models": [
                { "name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash" },
                { "name": "models/gemini-2.5-pro", "displayName": "Gemini 2.5 Pro" },
                { "name": "models/gemini-2.0-flash", "displayName": "Gemini 2.0 Flash" }
              ]
            }
        """.trimIndent()

        val service = CloudAiService()
        val models = service.parseModelsList(geminiJson)
        assertTrue(models.contains("gemini-2.5-flash"))
        assertTrue(models.contains("gemini-2.5-pro"))
        assertTrue(models.contains("gemini-2.0-flash"))
    }

    @Test
    fun testParseOllamaModelsList() {
        val ollamaJson = """
            {
              "models": [
                { "name": "llama3.2-vision:latest" },
                { "name": "llava:latest" },
                { "name": "mistral:latest" }
              ]
            }
        """.trimIndent()

        val service = CloudAiService()
        val models = service.parseModelsList(ollamaJson)
        assertTrue(models.contains("llama3.2-vision:latest"))
        assertTrue(models.contains("llava:latest"))
    }

    @Test
    fun testParseDirectArrayModelsList() {
        val arrayJson = """
            ["claude-3-7-sonnet-20250219", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"]
        """.trimIndent()

        val service = CloudAiService()
        val models = service.parseModelsList(arrayJson)
        assertEquals(3, models.size)
        assertEquals("claude-3-7-sonnet-20250219", models[0])
    }

    @Test
    fun testBuildModelsUrl() {
        val openAiUrl = CloudAiService.buildModelsUrl("https://api.openai.com/v1")
        assertEquals("https://api.openai.com/v1/models", openAiUrl)

        val groqUrl = CloudAiService.buildModelsUrl("https://api.groq.com/openai/v1")
        assertEquals("https://api.groq.com/openai/v1/models", groqUrl)

        val genericUrl = CloudAiService.buildModelsUrl("https://api.custom.ai")
        assertEquals("https://api.custom.ai/v1/models", genericUrl)
    }

    @Test
    fun testConvertersWithMalformedEntries() {
        val converters = com.amresalehin.emreshots.data.local.Converters()
        val mixedJson = """["Receipt", "", null, "Finance"]"""
        val parsed = converters.toStringList(mixedJson)
        assertEquals(2, parsed.size)
        assertEquals("Receipt", parsed[0])
        assertEquals("Finance", parsed[1])
    }

    @Test
    fun testAnthropicProviderDetection() {
        val claudeProvider = CustomCloudProvider(
            name = "Anthropic Claude 3.5",
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant-123"
        )
        assertTrue(CloudAiService.isAnthropicProvider(claudeProvider))

        val errorBody = """{"type":"error","error":{"type":"invalid_request_error","message":"Invalid API key provided"}}"""
        val errorMsg = CloudAiService.parseErrorMessage(errorBody)
        assertEquals("Invalid API key provided", errorMsg)
    }
}
