package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.service.ai.CloudAiService
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrMetadataTest {

    private lateinit var viewModel: ScreenshotsViewModel
    private val aiService = CloudAiService()

    @Before
    fun setUp() {
        viewModel = ScreenshotsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testDefaultAutoWriteExifIsFalse() {
        // Requirement: "By default not write directly."
        assertFalse(
            "autoWriteExifSetting must be false by default",
            viewModel.autoWriteExifSetting.value
        )
    }

    @Test
    fun testParseOcrTextInAiJsonOutput() {
        val sampleJson = """
            {
              "title": "Hotel Reservation Receipt",
              "description": "Booking confirmation for Grand Hotel downtown with confirmation number 98765.",
              "ocrText": "Grand Hotel Confirmation #98765 Total Paid: $340.00 Check-in: Nov 12",
              "tags": ["Receipt", "Hotel", "Travel"],
              "links": ["https://grandhotel.com/confirm"],
              "suggestedCollection": "Travel & Tickets",
              "exifUserComment": "Hotel Reservation Receipt: Conf #98765"
            }
        """.trimIndent()

        val parsed = aiService.parseAiJsonOutput(sampleJson, "gemini-2.5-flash", 120L)

        assertTrue(parsed.isSuccess)
        assertEquals("Hotel Reservation Receipt", parsed.title)
        assertNotNull(parsed.ocrText)
        assertTrue(parsed.ocrText!!.contains("Grand Hotel"))
        assertTrue(parsed.ocrText!!.contains("Confirmation #98765"))
        assertEquals("Travel & Tickets", parsed.suggestedCollection)
        assertEquals(3, parsed.tags.size)
    }
}
