package com.amresalehin.emreshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.ui.components.DrawerPosition
import com.amresalehin.emreshots.ui.components.ViewerDetailsDrawer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewerDetailsDrawerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDrawerPositionEnum() {
        assertEquals(3, DrawerPosition.entries.size)
        assertEquals(DrawerPosition.PEEK, DrawerPosition.entries[0])
        assertEquals(DrawerPosition.MEDIUM, DrawerPosition.entries[1])
        assertEquals(DrawerPosition.EXPANDED, DrawerPosition.entries[2])
    }

    @Test
    fun testDrawerDisplaysTitleAndMediaBadgeInPeek() {
        val testItem = ScreenshotItem(
            id = "test-1",
            title = "Mountain Sunset",
            mediaType = "PHOTO",
            fileSize = 2048576L,
            width = 1920,
            height = 1080
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = testItem,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.PEEK
                )
            }
        }

        composeTestRule.onNodeWithTag("overlay_details_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mountain Sunset").assertIsDisplayed()
        composeTestRule.onNodeWithText("PHOTO").assertIsDisplayed()
        composeTestRule.onNodeWithText("1920 × 1080").assertIsDisplayed()
    }

    @Test
    fun testCollectionIdResolvesToHumanReadableName() {
        val colId = "col-vacation-2026"
        val testItem = ScreenshotItem(
            id = "test-2",
            title = "Beach Picnic",
            mediaType = "PHOTO",
            collectionIds = listOf(colId)
        )
        val collections = listOf(
            CollectionItem(
                id = colId,
                name = "Summer Vacation",
                description = "Family photos from summer"
            )
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = testItem,
                    exifData = ExifData(),
                    collections = collections,
                    currentPosition = DrawerPosition.EXPANDED
                )
            }
        }

        // Human-readable collection name MUST be displayed, NOT raw internal ID
        composeTestRule.onNodeWithText("Summer Vacation").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(colId).assertDoesNotExist()
    }

    @Test
    fun testLocationOmittedWhenGpsNotPresent() {
        val noGpsItem = ScreenshotItem(id = "no-gps", title = "Indoor Document")
        val noGpsExif = ExifData(latitude = null, longitude = null)

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = noGpsItem,
                    exifData = noGpsExif,
                    collections = emptyList(),
                    currentPosition = DrawerPosition.EXPANDED
                )
            }
        }

        composeTestRule.onNodeWithText("LOCATION").assertDoesNotExist()
    }

    @Test
    fun testLocationDisplayedWhenRealGpsPresent() {
        val gpsItem = ScreenshotItem(id = "gps-photo", title = "Golden Gate")
        val gpsExif = ExifData(latitude = 37.7749, longitude = -122.4194)

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = gpsItem,
                    exifData = gpsExif,
                    collections = emptyList(),
                    currentPosition = DrawerPosition.EXPANDED
                )
            }
        }

        composeTestRule.onNodeWithText("LOCATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("37.77490, -122.41940").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testDeleteRequiresConfirmationCallback() {
        var deleteRequested = false
        val item = ScreenshotItem(id = "del-test", title = "Temporary Memo")

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = item,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.EXPANDED,
                    initialTab = 1, // Open directly in Actions tab
                    onDeleteRequest = { deleteRequested = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Scroll to and click delete button
        composeTestRule.onNodeWithTag("btn_drawer_delete")
            .performScrollTo()
            .performClick()

        // Must invoke onDeleteRequest (which launches confirmation dialog)
        assertTrue("Delete must route through confirmation request", deleteRequested)
    }

    @Test
    fun testEditDetailsSaveAndCancelFlow() {
        var savedTitle: String? = null
        var savedDesc: String? = null
        var savedNotes: String? = null

        val item = ScreenshotItem(
            id = "edit-test",
            title = "Original Title",
            description = "Original Description",
            notes = "Original Notes"
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = item,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.EXPANDED,
                    initialTab = 0,
                    onSaveDetails = { title, desc, notes ->
                        savedTitle = title
                        savedDesc = desc
                        savedNotes = notes
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Tap edit button to enter edit mode
        composeTestRule.onNodeWithTag("btn_drawer_edit_details")
            .performScrollTo()
            .performClick()

        // Modify title
        composeTestRule.onNodeWithTag("input_drawer_title")
            .performScrollTo()
            .performTextInput("Prefix ")

        // Scroll to Save button and click
        composeTestRule.onNodeWithTag("btn_drawer_save_edit")
            .performScrollTo()
            .performClick()

        assertEquals("Prefix Original Title", savedTitle)
        assertEquals("Original Description", savedDesc)
        assertEquals("Original Notes", savedNotes)
    }

    @Test
    fun testVideoSpecsDisplayInTechnicalTab() {
        val videoItem = ScreenshotItem(
            id = "video-test",
            title = "Drone Footage",
            mediaType = "VIDEO",
            durationMs = 125000L, // 2 mins 5 secs
            width = 3840,
            height = 2160,
            fileSize = 52428800L // 50 MB
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = videoItem,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.EXPANDED,
                    initialTab = 2 // Technical tab
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("VIDEO SPECIFICATIONS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2:05").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("3840 × 2160 px").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPrimaryActionsRenderedInMediumPosition() {
        var analyzeClicked = false
        var ocrClicked = false
        var exifClicked = false

        val testItem = ScreenshotItem(
            id = "action-test",
            title = "Architecture Plan",
            mediaType = "PHOTO",
            width = 1080,
            height = 2400,
            fileSize = 761856L
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = testItem,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.MEDIUM,
                    onAnalyze = { analyzeClicked = true },
                    onExtractOcr = { ocrClicked = true },
                    onOpenExifEditor = { exifClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify primary action buttons are visible
        composeTestRule.onNodeWithTag("btn_drawer_analyze").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_drawer_ocr").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_drawer_open_exif").assertIsDisplayed()

        // Verify callbacks fire
        composeTestRule.onNodeWithTag("btn_drawer_analyze").performClick()
        assertTrue("Analyze callback must be triggered", analyzeClicked)

        composeTestRule.onNodeWithTag("btn_drawer_ocr").performClick()
        assertTrue("OCR callback must be triggered", ocrClicked)

        composeTestRule.onNodeWithTag("btn_drawer_open_exif").performClick()
        assertTrue("EXIF callback must be triggered", exifClicked)
    }

    @Test
    fun testOverviewCardsRenderedInMediumPosition() {
        val testItem = ScreenshotItem(
            id = "cards-test",
            title = "Receipt Scan",
            description = "Expense receipt for office equipment",
            mediaType = "PHOTO",
            tags = listOf("finance", "receipt")
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ViewerDetailsDrawer(
                    screenshot = testItem,
                    exifData = ExifData(),
                    collections = emptyList(),
                    currentPosition = DrawerPosition.MEDIUM
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify overview cards are displayed
        composeTestRule.onNodeWithTag("card_drawer_description").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Expense receipt for office equipment").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("card_drawer_tags").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("#finance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("#receipt").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("card_drawer_location").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Location").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("card_drawer_metadata").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Metadata").performScrollTo().assertIsDisplayed()
    }
}

