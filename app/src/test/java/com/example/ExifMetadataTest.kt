package com.example

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExifData
import com.example.service.exif.ExifMetadataManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExifMetadataTest {

    private lateinit var manager: ExifMetadataManager
    private lateinit var testImageFile: File
    private lateinit var testPngFile: File

    @Before
    fun setup() {
        manager = ExifMetadataManager()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        testImageFile = File(context.cacheDir, "test_exif_image.jpg")
        testPngFile = File(context.cacheDir, "test_exif_screenshot.png")

        // Create a dummy JPEG image
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(testImageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Create a dummy PNG image
        FileOutputStream(testPngFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun testWriteAndReadExif() {
        val exifData = ExifData(
            cameraMake = "Google",
            cameraModel = "Pixel 9 Pro",
            software = "Shots Studio Test",
            artist = "QA Engineer",
            imageDescription = "Test Screenshot Title",
            userComment = "AI Index Note",
            dateTaken = "2026:10:03 12:00:00",
            latitude = 37.7749,
            longitude = -122.4194
        )

        val writeResult = manager.writeExif(testImageFile, exifData)
        assertTrue(writeResult.isSuccess)

        val readData = manager.readExif(testImageFile)
        assertNotNull(readData)
        assertEquals("Google", readData.cameraMake)
        assertEquals("Pixel 9 Pro", readData.cameraModel)
        assertEquals("Shots Studio Test", readData.software)
        assertEquals("QA Engineer", readData.artist)
        assertEquals("Test Screenshot Title", readData.imageDescription)
        assertEquals("AI Index Note", readData.userComment)
        assertEquals("2026:10:03 12:00:00", readData.dateTaken)
        assertNotNull(readData.latitude)
        assertNotNull(readData.longitude)
        assertEquals(37.7749, readData.latitude!!, 0.001)
        assertEquals(-122.4194, readData.longitude!!, 0.001)
    }

    @Test
    fun testClearExifAttributes() {
        // First write data with GPS and comments
        val initialData = ExifData(
            cameraMake = "Samsung",
            cameraModel = "Galaxy S24",
            artist = "Photographer",
            userComment = "Personal Secret Note",
            latitude = 40.7128,
            longitude = -74.0060
        )
        val firstWrite = manager.writeExif(testImageFile, initialData)
        assertTrue(firstWrite.isSuccess)

        // Now clear artist, comment, and GPS coordinates (passing null)
        val clearedData = ExifData(
            cameraMake = "Samsung",
            cameraModel = "Galaxy S24",
            artist = null,
            userComment = null,
            latitude = null,
            longitude = null
        )
        val secondWrite = manager.writeExif(testImageFile, clearedData)
        assertTrue(secondWrite.isSuccess)

        val readData = manager.readExif(testImageFile)
        assertEquals("Samsung", readData.cameraMake)
        assertEquals("Galaxy S24", readData.cameraModel)
        assertNull(readData.artist)
        assertNull(readData.userComment)
        assertNull(readData.latitude)
        assertNull(readData.longitude)
    }

    @Test
    fun testApplyAiMetadataToExif() {
        val result = manager.applyAiMetadataToExif(
            file = testImageFile,
            title = "Receipt for AWS Cloud",
            description = "Monthly invoice summary",
            tags = listOf("AWS", "Cloud", "Invoice"),
            modelName = "gemini-2.5-flash"
        )

        assertTrue(result.isSuccess)
        val readData = manager.readExif(testImageFile)
        assertEquals("Receipt for AWS Cloud", readData.imageDescription)
        assertTrue(readData.userComment?.contains("gemini-2.5-flash") == true)
        assertTrue(readData.userComment?.contains("AWS, Cloud, Invoice") == true)
        assertTrue(readData.software?.contains("EmreShots AI") == true)
    }

    @Test
    fun testWriteExifOnPng() {
        val exifData = ExifData(
            cameraMake = "Android",
            cameraModel = "Screenshot Tool",
            software = "Shots Studio PNG",
            imageDescription = "Captured Dashboard"
        )
        val writeResult = manager.writeExif(testPngFile, exifData)
        assertTrue(writeResult.isSuccess)

        val readData = manager.readExif(testPngFile)
        assertNotNull(readData)
        assertEquals("Captured Dashboard", readData.imageDescription)
    }
}
