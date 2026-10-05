package com.amresalehin.emreshots

import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.service.media.DeviceMediaScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceMediaTest {

    @Test
    fun testMediaTypeProperty() {
        val photo = ScreenshotItem(
            title = "Family Vacation Photo",
            mediaType = "PHOTO"
        )
        assertFalse(photo.isVideo)

        val video = ScreenshotItem(
            title = "Sunset Clip",
            mediaType = "VIDEO",
            durationMs = 15000L
        )
        assertTrue(video.isVideo)
        assertEquals(15000L, video.durationMs)
    }

    @Test
    fun testRequiredPermissionsArray() {
        val permissions = DeviceMediaScanner.getRequiredPermissions()
        assertTrue(permissions.isNotEmpty())
        val permissionList = permissions.toList()
        assertTrue(
            permissionList.contains("android.permission.READ_MEDIA_IMAGES") ||
                    permissionList.contains("android.permission.READ_EXTERNAL_STORAGE")
        )
    }

    @Test
    fun testFilterMediaTypes() {
        val list = listOf(
            ScreenshotItem(id = "1", title = "Photo 1", mediaType = "PHOTO", tags = listOf("Photo")),
            ScreenshotItem(id = "2", title = "Shot 1", mediaType = "PHOTO", tags = listOf("Screenshot")),
            ScreenshotItem(id = "3", title = "Video 1", mediaType = "VIDEO", tags = listOf("Video"))
        )

        val videos = list.filter { it.isVideo }
        assertEquals(1, videos.size)
        assertEquals("Video 1", videos.first().title)

        val photos = list.filter { !it.isVideo && !it.tags.contains("Screenshot") }
        assertEquals(1, photos.size)
        assertEquals("Photo 1", photos.first().title)

        val screenshots = list.filter { it.tags.contains("Screenshot") }
        assertEquals(1, screenshots.size)
        assertEquals("Shot 1", screenshots.first().title)
    }
}
