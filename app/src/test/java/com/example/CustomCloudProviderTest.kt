package com.amresalehin.emreshots

import com.amresalehin.emreshots.service.ai.CloudAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomCloudProviderTest {

    @Test
    fun testNormalizeBaseUrl() {
        assertEquals("http://10.0.2.2:11434", CloudAiService.normalizeBaseUrl(""))
        assertEquals("https://api.openai.com/v1", CloudAiService.normalizeBaseUrl("https://api.openai.com/v1/"))
        assertEquals("http://localhost:8080/v1", CloudAiService.normalizeBaseUrl("http://localhost:8080/v1/"))
        assertEquals("https://my-cloud.com", CloudAiService.normalizeBaseUrl("my-cloud.com"))
    }

    @Test
    fun testBuildModelsAndChatUrl() {
        val baseUrl = "https://api.openai.com/v1"
        assertEquals("https://api.openai.com/v1/models", CloudAiService.buildModelsUrl(baseUrl))
        assertEquals("https://api.openai.com/v1/chat/completions", CloudAiService.buildChatCompletionsUrl(baseUrl))

        val ollamaUrl = "http://10.0.2.2:11434"
        assertEquals("http://10.0.2.2:11434/v1/models", CloudAiService.buildModelsUrl(ollamaUrl))
        assertEquals("http://10.0.2.2:11434/v1/chat/completions", CloudAiService.buildChatCompletionsUrl(ollamaUrl))
    }

    @Test
    fun testParseAiJsonOutputWithMarkdownFences() {
        val service = CloudAiService()
        val markdownJson = """
            Here is the analysis:
            ```json
            {
              "title": "Google Cloud Console Bill",
              "description": "Invoice summary showing compute engine charges.",
              "tags": ["Cloud", "Invoice", "DevOps"],
              "links": ["https://console.cloud.google.com/billing"],
              "suggestedCollection": "Work & Receipts",
              "exifUserComment": "Shots Studio Cloud Analysis"
            }
            ```
        """.trimIndent()

        val result = service.parseAiJsonOutput(markdownJson, "gpt-4o-mini", 120L)

        assertTrue(result.isSuccess)
        assertEquals("Google Cloud Console Bill", result.title)
        assertEquals("Invoice summary showing compute engine charges.", result.description)
        assertEquals(3, result.tags.size)
        assertTrue(result.tags.contains("DevOps"))
        assertEquals("https://console.cloud.google.com/billing", result.detectedLinks.first())
        assertEquals("Work & Receipts", result.suggestedCollection)
        assertEquals("gpt-4o-mini", result.modelUsed)
    }

    @Test
    fun testExtractJsonSubstringFromRawString() {
        val service = CloudAiService()
        val raw = "Sure! {\"title\":\"Test Title\",\"description\":\"Desc\"} Hope that helps!"
        val extracted = service.extractJsonSubstring(raw)
        assertEquals("{\"title\":\"Test Title\",\"description\":\"Desc\"}", extracted)
    }
}
