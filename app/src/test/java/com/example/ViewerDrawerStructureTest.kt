package com.amresalehin.emreshots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.example.ui.icons.AppIcons

class ViewerDrawerStructureTest {
    @Test
    fun fullscreenViewerProvidesSwipeAndBottomToolbarAccess() {
        val source = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        assertTrue(source.contains("detectVerticalDragGestures"))
        assertTrue(source.contains("visible = showControls && drawerHeightFraction <= 0.04f"))
        assertTrue(source.contains("viewer_bottom_toolbar"))
        assertTrue(source.contains("Text(\"Details\""))
        assertTrue(source.contains(".navigationBarsPadding()"))
    }

    @Test
    fun fullscreenViewerReplacesTheUnderlyingDetailScaffold() {
        val source = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt").readText()
        assertTrue(source.contains("if (!showImageViewer)"))
    }

    @Test
    fun drawerAvoidsApplyingSystemInsetsTwice() {
        val source = File("src/main/java/com/example/ui/screens/ScreenshotDetailContent.kt").readText()
        assertTrue(source.contains("if (showDrawerHandle) Modifier.navigationBarsPadding() else Modifier"))
    }

    @Test
    fun drawerHasOverviewAndActionsWithoutPeopleCarousel() {
        val source = File("src/main/java/com/example/ui/screens/ScreenshotDetailContent.kt").readText()
        assertTrue(source.contains("viewer_details_drawer"))
        assertTrue(source.contains("Overview"))
        assertTrue(source.contains("Actions"))
        assertTrue(source.contains("Metadata"))
        assertTrue(source.contains("Extract text"))
        assertFalse(source.contains("People carousel"))
        assertFalse(source.contains("Face recognition"))
    }
    @Test
    fun fullscreenDrawerUsesInlineOcrAndExifEditors() {
        val content = File("src/main/java/com/example/ui/screens/ScreenshotDetailContent.kt").readText()
        val screen = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt").readText()
        assertTrue(content.contains("inlineTools: Boolean = false"))
        assertTrue(content.contains("inline_ocr_editor"))
        assertTrue(content.contains("inline_exif_editor"))
        assertTrue(content.contains("if (inlineTools) showInlineOcr"))
        assertTrue(content.contains("if (inlineTools) showInlineExif"))
        assertTrue(screen.contains("inlineTools = true"))
    }

    @Test
    fun localVectorIconsAreAllConstructible() {
        assertEquals(41, AppIcons.all.size)
    }
}
