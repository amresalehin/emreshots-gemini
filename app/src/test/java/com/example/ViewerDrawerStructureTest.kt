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
        assertFalse(source.contains("if (!showImageViewer)"))
        assertTrue(source.contains("ZoomableImageViewer("))
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
        assertEquals(42, AppIcons.all.size)
    }

    @Test
    fun screenTransitionsFollowNativeAndroidStyling() {
        val mainSource = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(mainSource.contains("enterTransition = {"))
        assertTrue(mainSource.contains("exitTransition = {"))
        assertTrue(mainSource.contains("popEnterTransition = {"))
        assertTrue(mainSource.contains("popExitTransition = {"))
        assertTrue(mainSource.contains("slideIntoContainer"))
        assertTrue(mainSource.contains("slideOutOfContainer"))
        assertTrue(mainSource.contains("FastOutSlowInEasing"))
    }

    @Test
    fun pressingBackWhileViewingDetailsReturnsToFullscreenViewer() {
        val detailSource = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt").readText()
        assertTrue(detailSource.contains("btn_detail_back"))
        assertFalse(detailSource.contains("if (!showImageViewer)"))

        val viewerSource = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        // BackHandler and Dialog dismiss collapses drawer back to viewer
        assertTrue(viewerSource.contains("BackHandler(enabled = drawerHeightFraction > 0.04f)"))
        assertTrue(viewerSource.contains("if (drawerHeightFraction > 0.04f) {"))
        assertTrue(viewerSource.contains("animateDrawerTo(0f)"))
    }

    @Test
    fun mediaViewerProvidesThreeDotMenuWithoutOpeningOtherContainer() {
        val viewerSource = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        val screenSource = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt").readText()
        assertTrue(viewerSource.contains("btn_detail_overflow"))
        assertTrue(viewerSource.contains("DropdownMenu"))
        assertTrue(viewerSource.contains("overflowMenuContent"))
        assertTrue(screenSource.contains("overflowMenuContent = {"))
        assertTrue(screenSource.contains("btn_detail_share"))
        assertTrue(screenSource.contains("btn_detail_delete"))
        assertFalse(screenSource.contains("showImageViewer = false"))
    }

    @Test
    fun drawerFollowsNativeAndroidConventions() {
        val viewerSource = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        // Spring physics animation
        assertTrue(viewerSource.contains("animateDrawerTo"))
        assertTrue(viewerSource.contains("settleDrawer"))
        assertTrue(viewerSource.contains("Spring.DampingRatioLowBouncy"))
        // Dimming backdrop scrim
        assertTrue(viewerSource.contains("Color.Black.copy(alpha = (drawerHeightFraction * 0.65f)"))
        // Top rounded shape for bottom sheet
        assertTrue(viewerSource.contains("RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)"))
    }

    @Test
    fun mediaViewerUnderCoverIsNotEnclosedInBorderedContainer() {
        val screen = File("src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt").readText()
        assertFalse(screen.contains("MaterialTheme.shapes.extraLarge"))
        assertFalse(screen.contains("padding(horizontal = 12.dp, vertical = 12.dp)"))
    }

    @Test
    fun mediaViewerContainerIsSimplifiedAndUnboxed() {
        val viewerSource = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        val videoSource = File("src/main/java/com/example/ui/components/InAppVideoPlayer.kt").readText()

        // No blurred duplicate image box framing the media
        assertFalse(viewerSource.contains("blur(30.dp)"))
        assertFalse(viewerSource.contains("Soft-focus backdrop"))

        // Video container uses black background instead of white letterboxing box
        assertFalse(videoSource.contains(".background(Color.White)"))
        assertTrue(videoSource.contains(".background(Color.Black)"))

        // Provides unboxed fill vs fit screen toggle
        assertTrue(viewerSource.contains("btn_detail_aspect_toggle"))
        assertTrue(viewerSource.contains("isFillMode"))
    }

    @Test
    fun sidewaysPagingGesturesArePreservedInMediaViewer() {
        val viewerSource = File("src/main/java/com/example/ui/components/ZoomableImageViewer.kt").readText()
        val videoSource = File("src/main/java/com/example/ui/components/InAppVideoPlayer.kt").readText()

        // Pager is enabled when not zoomed
        assertTrue(viewerSource.contains("val canPage = scaleAnim.value <= 1.05f && drawerHeightFraction <= 0.04f && dismissOffsetY.value == 0f"))
        assertTrue(viewerSource.contains("HorizontalPager("))
        assertTrue(viewerSource.contains("userScrollEnabled = canPage"))

        // Uses smart multi-touch check so single-touch horizontal swipes pass to HorizontalPager
        assertTrue(viewerSource.contains("pointerCount >= 2"))
        assertTrue(viewerSource.contains("pointerCount == 1 && isZoomed"))
        assertFalse(viewerSource.contains("detectTransformGestures {"))

        // Native video view does not consume touch events
        assertTrue(videoSource.contains("isClickable = false"))
        assertTrue(videoSource.contains("isFocusable = false"))
    }
}
