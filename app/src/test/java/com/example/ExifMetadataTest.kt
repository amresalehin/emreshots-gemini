package com.example

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExifData
import com.example.service.exif.ExifMetadataManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Before
    fun setup() {
        manager = ExifMetadataManager()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        testImageFile = File(context.cacheDir, "test_exif_image.jpg")

        // Create a dummy JPEG image
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(testImageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
            dateTaken = "2026:10:03 12:00:00"
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
        assertTrue(readData.software?.contains("Shots Studio AI") == true)
    }
}
