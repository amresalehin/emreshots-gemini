package com.amresalehin.emreshots

import com.amresalehin.emreshots.data.local.Converters
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.CustomCloudProvider
import com.amresalehin.emreshots.data.model.ScreenshotItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabasePersistenceTest {

    @Test
    fun testConverters() {
        val converters = Converters()
        val originalList = listOf("Receipt", "Stripe", "Finance", "Invoice")
        val json = converters.fromStringList(originalList)
        val deserialized = converters.toStringList(json)

        assertEquals(originalList, deserialized)
    }

    @Test
    fun testEmptyConverter() {
        val converters = Converters()
        val emptyJson = converters.fromStringList(emptyList())
        val deserialized = converters.toStringList(emptyJson)

        assertTrue(deserialized.isEmpty())
    }

    @Test
    fun testCustomCloudProviderModel() {
        val provider = CustomCloudProvider(
            name = "Local Ollama",
            baseUrl = "http://10.0.2.2:11434/v1",
            apiKey = "test-token",
            selectedModel = "llama3.2-vision",
            customHeadersJson = "{\"X-Custom\":\"Value\"}",
            timeoutSeconds = 45,
            isActive = true
        )

        assertEquals("Local Ollama", provider.name)
        assertEquals("llama3.2-vision", provider.selectedModel)
        assertTrue(provider.isActive)
    }

    @Test
    fun testScreenshotModel() {
        val item = ScreenshotItem(
            title = "Boarding Pass",
            description = "Flight SFO to HND",
            tags = listOf("Travel", "Flight"),
            links = listOf("https://ana.co.jp"),
            collectionIds = listOf("col-travel"),
            aiProcessed = true,
            isFavorite = true,
            reminderTime = 1735689600000L,
            reminderText = "Check flight gate"
        )

        assertEquals("Boarding Pass", item.title)
        assertTrue(item.isFavorite)
        assertEquals(1, item.links.size)
        assertEquals(2, item.tags.size)
        assertEquals("Check flight gate", item.reminderText)
    }
}
