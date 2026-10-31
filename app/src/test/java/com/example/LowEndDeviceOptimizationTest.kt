package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.service.perf.PerformanceManager
import com.amresalehin.emreshots.ui.util.DateUtils
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LowEndDeviceOptimizationTest {

    private lateinit var viewModel: ScreenshotsViewModel

    @Before
    fun setUp() {
        viewModel = ScreenshotsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testPerformanceManagerDefaults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val maxDim = PerformanceManager.getRecommendedMaxImageDimension(context)
        assertTrue(maxDim in 720..1024)

        // Nonexistent file should safely return null without throwing
        val result = PerformanceManager.decodeSampledBitmapFromFile(File("/does/not/exist.jpg"))
        assertEquals(null, result)
    }

    @Test
    fun testDateUtilsReusability() {
        val now = 1727956800000L
        val formattedFull = DateUtils.formatFullDate(now)
        val formattedShort = DateUtils.formatShortDate(now)
        val formattedTime = DateUtils.formatTimeOnly(now)
        val durationFormatted = DateUtils.formatDuration(65000L)

        assertNotNull(formattedFull)
        assertNotNull(formattedShort)
        assertNotNull(formattedTime)
        assertEquals("1:05", durationFormatted)
    }

    @Test
    fun testViewModelMemoryTrimming() {
        viewModel.trimMemory()
        assertNotNull(viewModel.snackbarMessage.value)
    }
}
