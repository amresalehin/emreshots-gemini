package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExifData
import com.example.data.model.ScreenshotItem
import com.example.service.exif.ExifMetadataManager
import com.example.service.media.DeviceMediaScanner
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
    }
}
