package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsControlsTest {

    private lateinit var viewModel: ScreenshotsViewModel

    @Before
    fun setUp() {
        viewModel = ScreenshotsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testAiQualityPresets() {
        assertEquals("Balanced", viewModel.aiQualityPreset.value)
        viewModel.setAiQualityPreset("Deep")
        assertEquals("Deep", viewModel.aiQualityPreset.value)
        viewModel.setAiQualityPreset("Fast")
        assertEquals("Fast", viewModel.aiQualityPreset.value)
    }

    @Test
    fun testVisionCapabilityToggles() {
        assertTrue(viewModel.ocrEnabled.value)
        viewModel.setOcrEnabled(false)
        assertFalse(viewModel.ocrEnabled.value)

        assertTrue(viewModel.linksDetectionEnabled.value)
        viewModel.setLinksDetectionEnabled(false)
        assertFalse(viewModel.linksDetectionEnabled.value)

        assertTrue(viewModel.smartTagsEnabled.value)
        viewModel.setSmartTagsEnabled(false)
        assertFalse(viewModel.smartTagsEnabled.value)

    }

    @Test
    fun testGridColumnsAdjustment() {
        assertEquals(2, viewModel.gridColumns.value)
        viewModel.setGridColumns(3)
        assertEquals(3, viewModel.gridColumns.value)
    }
}
