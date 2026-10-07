package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.viewmodel.ScreenshotFilter
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// CI verification: exercise the current gallery defaults on the release branch.
class GalleryOrganizeTest {

    private lateinit var viewModel: ScreenshotsViewModel

    @Before
    fun setUp() {
        viewModel = ScreenshotsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testViewModeOptions() {
        assertEquals(GalleryViewMode.GRID, viewModel.viewMode.value)
        viewModel.setViewMode(GalleryViewMode.MASONRY)
        assertEquals(GalleryViewMode.GRID, viewModel.viewMode.value)
        viewModel.setViewMode(GalleryViewMode.FEED)
        assertEquals(GalleryViewMode.FEED, viewModel.viewMode.value)
        viewModel.setViewMode(GalleryViewMode.LIST)
        assertEquals(GalleryViewMode.LIST, viewModel.viewMode.value)
    }

    @Test
    fun testSortOptions() {
        assertEquals(MediaSortOption.NEWEST, viewModel.sortOption.value)
        viewModel.setSortOption(MediaSortOption.TITLE_AZ)
        assertEquals(MediaSortOption.TITLE_AZ, viewModel.sortOption.value)
        viewModel.setSortOption(MediaSortOption.SIZE_DESC)
        assertEquals(MediaSortOption.SIZE_DESC, viewModel.sortOption.value)
    }

    @Test
    fun testGroupByOptions() {
        assertEquals(MediaGroupBy.NONE, viewModel.groupByOption.value)
        viewModel.setGroupByOption(MediaGroupBy.DATE)
        assertEquals(MediaGroupBy.DATE, viewModel.groupByOption.value)
        viewModel.setGroupByOption(MediaGroupBy.TYPE)
        assertEquals(MediaGroupBy.TYPE, viewModel.groupByOption.value)
        viewModel.setGroupByOption(MediaGroupBy.AI_STATUS)
        assertEquals(MediaGroupBy.AI_STATUS, viewModel.groupByOption.value)
    }

    @Test
    fun testGroupedScreenshotsNotNull() {
        val grouped = viewModel.groupedScreenshots.value
        assertNotNull(grouped)
    }

    @Test
    fun testFilterTogglesAndUnselects() {
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
        viewModel.setFilter(ScreenshotFilter.PHOTOS)
        assertEquals(ScreenshotFilter.PHOTOS, viewModel.selectedFilter.value)
        // Clicking same filter again unselects it (reverts to ALL)
        viewModel.setFilter(ScreenshotFilter.PHOTOS)
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)

        // Switching between different filters
        viewModel.setFilter(ScreenshotFilter.VIDEOS)
        assertEquals(ScreenshotFilter.VIDEOS, viewModel.selectedFilter.value)
        viewModel.setFilter(ScreenshotFilter.SCREENSHOTS)
        assertEquals(ScreenshotFilter.SCREENSHOTS, viewModel.selectedFilter.value)
        // Re-clicking unselects
        viewModel.setFilter(ScreenshotFilter.SCREENSHOTS)
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    @Test
    fun testOcrSyncState() {
        assertTrue(viewModel.ocrEnabled.value)
        viewModel.setOcrEnabled(false)
        assertFalse(viewModel.ocrEnabled.value)
        viewModel.setOcrEnabled(true)
        assertTrue(viewModel.ocrEnabled.value)
    }
}
