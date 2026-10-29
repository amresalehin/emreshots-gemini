package com.amresalehin.emreshots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ViewerDrawerStructureTest {
    @Test
    fun viewerDrawerHasOverviewAndActionsAndNoPeopleCarousel() {
        val source = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt")
            .readText()

        assertTrue(source.contains("viewer_details_drawer"))
        assertTrue(source.contains("drawer_tab_${tab.lowercase()}"))
        assertTrue(source.contains("Overview"))
        assertTrue(source.contains("Actions"))
        assertTrue(source.contains("Metadata"))
        assertTrue(source.contains("Extract text"))
        assertFalse(source.contains("People carousel"))
        assertFalse(source.contains("Face recognition"))
    }
}
