package com.amresalehin.emreshots

// CI verification for P2 preference plumbing.

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
    fun testGalleryDefaultsToThreeColumns() {
        assertEquals(3, viewModel.gridColumns.value)
    }

    @Test
    fun testFavoriteButtonOverlayToggle() {
        assertFalse(viewModel.showFavoriteButton.value)
        viewModel.setShowFavoriteButton(true)
        assertTrue(viewModel.showFavoriteButton.value)
        viewModel.setShowFavoriteButton(false)
        assertFalse(viewModel.showFavoriteButton.value)

        // Test hide helper
        viewModel.setHideFavoriteButton(false)
        assertTrue(viewModel.showFavoriteButton.value)
        viewModel.setHideFavoriteButton(true)
        assertFalse(viewModel.showFavoriteButton.value)
    }

    @Test
    fun testThumbnailOverlayToggles() {
        assertFalse(viewModel.showFileNames.value)
        viewModel.setShowFileNames(true)
        assertTrue(viewModel.showFileNames.value)
        viewModel.setShowFileNames(false)
        assertFalse(viewModel.showFileNames.value)

        assertFalse(viewModel.showTags.value)
        viewModel.setShowTags(true)
        assertTrue(viewModel.showTags.value)
        viewModel.setShowTags(false)
        assertFalse(viewModel.showTags.value)
    }
}
