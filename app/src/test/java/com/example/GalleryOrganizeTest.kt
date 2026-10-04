package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GalleryViewMode
import com.example.data.model.MediaGroupBy
import com.example.data.model.MediaSortOption
import com.example.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GalleryOrganizeTest {

    private lateinit var viewModel: ScreenshotsViewModel

    @Before
    fun setUp() {
        viewModel = ScreenshotsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testViewModeOptions() {
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
}
