package com.amresalehin.emreshots

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.exif.ExifMetadataManager
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
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
@Config(sdk = [35])
class MetadataPermissionTest {

    private lateinit var context: Context
    private lateinit var exifManager: ExifMetadataManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exifManager = ExifMetadataManager()
    }

    @Test
    fun testRequiredPermissionsIncludesAccessMediaLocation() {
        val perms = DeviceMediaScanner.getRequiredPermissions().toList()
        assertTrue(perms.contains(Manifest.permission.ACCESS_MEDIA_LOCATION))
    }

    @Test
    fun testHasPermissionsHelperFunctions() {
        // Under Robolectric, ContextCompat.checkSelfPermission can be queried
        val hasLoc = DeviceMediaScanner.hasMediaLocationPermission(context)
        val hasAll = DeviceMediaScanner.hasAllMetadataPermissions(context)
        // Verify functions evaluate safely without crash
        assertNotNull(hasLoc)
        assertNotNull(hasAll)
    }

    @Test
    fun testSafeWriteExifCreatesWorkingFileAndSavesAttributes() {
        val testDir = File(context.filesDir, "test_images").apply { mkdirs() }
        val testFile = File(testDir, "sample.jpg")

        // Create a valid JPEG file
        val bitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        FileOutputStream(testFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }

        val item = ScreenshotItem(
            id = "test-meta-1",
            filePath = testFile.absolutePath,
            title = "Test Image",
            description = "Sample Description",
            addedOn = System.currentTimeMillis()
        )

        val exifData = ExifData(
            cameraModel = "Pixel 8 Pro",
            cameraMake = "Google",
            imageDescription = "Sunset at the beach",
            userComment = "Shots Studio AI [gemini-2.5-flash]"
        )

        val result = exifManager.writeExifSafe(context, item, exifData)
        assertTrue(result.isSuccess)
        val writtenFile = result.getOrNull()
        assertNotNull(writtenFile)
        assertTrue(writtenFile!!.exists())

        // Verify that readExif reads back the attributes accurately
        val readBack = exifManager.readExif(context, item.copy(filePath = writtenFile.absolutePath))
        assertEquals("Pixel 8 Pro", readBack.cameraModel)
        assertEquals("Google", readBack.cameraMake)
        assertEquals("Sunset at the beach", readBack.imageDescription)
    }

    @Test
    fun testExtensionDetectionForPng() {
        val testDir = File(context.filesDir, "test_images").apply { mkdirs() }
        val testPngFile = File(testDir, "screenshot_1.png")
        val bitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        FileOutputStream(testPngFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }

        val item = ScreenshotItem(
            id = "test-png-item",
            filePath = testPngFile.absolutePath,
            title = "Screenshot PNG",
            description = "PNG format test",
            addedOn = System.currentTimeMillis()
        )

        val extension = exifManager.determineImageExtension(context, item, testPngFile)
        assertEquals("png", extension)
    }

    @Test
    fun testDateNormalization() {
        val inputDashed = "2026-10-03 14:30:00"
        val normalized = exifManager.normalizeExifDate(inputDashed)
        assertEquals("2026:10:03 14:30:00", normalized)

        val inputSlashed = "2026/10/03 14:30:00"
        val normalizedSlash = exifManager.normalizeExifDate(inputSlashed)
        assertEquals("2026:10:03 14:30:00", normalizedSlash)
    }
}
