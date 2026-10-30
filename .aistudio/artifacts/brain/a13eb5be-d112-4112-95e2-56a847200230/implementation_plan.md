# Fullscreen Media Viewer Overlay Details Drawer

An architectural and UX blueprint to implement a genuine, 3-state bottom-anchored overlay details drawer inside the fullscreen media viewer (`ZoomableImageViewer.kt`). The drawer overlays media without disrupting the underlying image or video playback, eliminates gesture conflicts, keeps top-level controls fully accessible at every drawer position, and exposes rich metadata, AI insights, OCR tools, and collections using Emre Gallery's calm design language.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Key architectural decisions incorporated into this revised plan based on codebase inspection:
> - **Fullscreen Host Identification**: The fullscreen dialog viewer (`ZoomableImageViewer.kt`) is the dedicated host for this feature. The image/video remains full-bleed background content. The drawer is rendered as a genuine overlay (`Box` layout layering), NOT placed beneath the image in a vertically stacked scrolling page.
> - **Three Distinct Anchored States**:
>   1. **Peek** (~76dp above system navigation bar): Displays the pill drag handle, title/type badge, date or file size, and drag-up indicator. Leaves >85% of media visible.
>   2. **Medium / Default** (~48% screen height): Reveals the primary Overview section (title, date, dimensions, file size, description, notes, tags, collection badges, quick action row).
>   3. **Expanded** (~88% screen height, stopping safely below the top toolbar): Reveals full scrollable tabs (Overview, Actions, Hardware & EXIF), extracted OCR text, detected links with Copy/Open actions, and reminder management.
> - **Gesture Ownership & Conflict Resolution**:
>   - Background image area owns transform gestures (pinch-to-zoom, pan) and double-tap to zoom without interference from the drawer.
>   - The drawer drag handle and header own vertical sheet dragging across Peek, Medium, and Expanded states.
>   - Nested content scrolling only delegates downward dragging to collapse the sheet when the inner scroll offset is strictly at `0`.
> - **Top Toolbar Z-Order & Accessibility**: The top HUD bar (Close/Back, Title, Zoom Scale %, Favorite, Share, and Overflow) is rendered at the highest z-index (`zIndex(2f)`) so controls are never occluded, disabled, or tap-intercepted, even when the drawer is fully expanded.
> - **Media Preservation**: Full parity for both Photos (pan/pinch-to-zoom) and Videos (`InAppVideoPlayer` with full playback controls and duration metrics).
> - **Confirmation & Lifecycles**: Deletion always triggers the verified `AlertDialog` confirmation; editing preserves explicit Save and Cancel (restoring uncommitted changes); collection names are resolved from `viewModel.collections` (no raw UUIDs); no fake people/face carousels.

- **Confirmed Decision 1**: Keep `ZoomableImageViewer` backward-compatible by retaining all existing default parameters while adding optional ViewModel and action callbacks.
- **Confirmed Decision 2**: Wire `ScreenshotDetailScreen` media tap (`onTap`) to open the fullscreen `ZoomableImageViewer` with the overlay drawer.
- **Confirmed Decision 3**: Use a robust Compose-compatible anchored draggable / swipeable bottom-anchored sheet layout with spring physics to guarantee exact 3-state positioning across all Android device heights.

---

## 1. Overview & Core Concept

### What It Does
When users view any photo, video, or screenshot in fullscreen:
1. The media remains the primary focal element, filling the background in edge-to-edge resolution with pinch-to-zoom, double-tap zoom, and pan.
2. A sleek, semi-translucent bottom drawer rests at the bottom in **Peek** mode with a tactile drag handle.
3. Swiping up (or tapping the handle) smoothly slides the drawer into **Medium** (half-screen) or **Expanded** (near full-screen) mode.
4. The drawer organizes metadata into clean, legible sections:
   - **Overview Tab**: Title, capture timestamp, resolution, file size, video duration, description, personal notes, tags cloud (with add/remove), human-readable collection chips, and GPS location (when present).
   - **Actions Tab**: AI analysis & reanalyze trigger, OCR extraction & text transcription card, EXIF metadata editor launch, detected URLs (with copy and open in browser), and reminder scheduling.
   - **EXIF & Hardware Specs**: Camera make/model, ISO, aperture, shutter speed, software, and color space in a clean, space-efficient table.

### Target Audience & Persona
- **Mobile Photographers & Archive Curators**: Need to inspect high-resolution details and technical camera EXIF while staying immersed in the photo.
- **Productivity & Note Takers**: Rapidly extract OCR text, copy detected links, or organize items into collections without jumping back and forth between screens.
- **Motor & Touch Accessibility**: Users who prefer clear tap/drag affordances with minimum 48 × 48dp targets and on-screen zoom indicators.

---

## 2. User Experience & Visual Design

### Theming & Emre Gallery Design Language
In keeping with Emre Gallery's restrained, calm aesthetic:
- **Palette**: Neutral monochrome foundation with subtle tonal elevations:
  - Drawer Container: Dark semi-translucent surface in fullscreen dark mode (`Color(0xFF16181D).copy(alpha = 0.95f)`) with a subtle 1dp border (`Color.White.copy(alpha = 0.12f)`).
  - Drag Handle: Rounded capsule (36 × 4dp, `Color.White.copy(alpha = 0.4f)`).
  - Accent Tokens: Restrained indigo/violet (`MaterialTheme.colorScheme.primary`) used strictly for active states, tags, and action icons. Never loud or distracting.
- **Typography**:
  - Headings: 16–18sp SemiBold for media titles and drawer section headers.
  - Informative Badges: 12sp Medium for dimensions, file size, and camera specs.
  - Body & Notes: 14sp Regular with comfortable line-height (20sp).
  - Strictly no 9–10sp illegible micro-text.
- **Touch Targets**: Minimum 48 × 48dp for all interactive buttons, including close, favorite, share, tag remove, link copy, and drag handles.

### 3-State Drawer Behavior Matrix

```
┌────────────────────────────────────────────────────────────────────────┐
│ State     │ Height           │ Content Visible        │ Media Preview  │
├───────────┼──────────────────┼────────────────────────┼────────────────┤
│ PEEK      │ ~76dp + insets   │ Handle, Title, Size/MB │ >85% Uncovered │
│ MEDIUM    │ ~48% viewport    │ Overview, Tags, Quick  │ ~52% Visible   │
│ EXPANDED  │ ~88% viewport    │ Full Scrollable Tabs   │ Background Dim │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Custom Anchored Overlay vs. ModalBottomSheet vs. Stacked Page
- **Chosen Approach**: Custom bottom-anchored overlay box utilizing Compose `pointerInput` vertical drag gestures with spring-snapping between three predefined anchor offsets (`Peek`, `Medium`, `Expanded`).
- **Why**: Standard `ModalBottomSheet` dismisses completely on downward drag and blocks the underlying image with a scrim. A custom anchored overlay allows the media to remain interactive in Peek and Medium positions, supports 3 distinct heights, and prevents the image from being pushed off-screen as occurs in vertical scrolling pages.
- **Alternatives Considered**: 
  - *Vertically stacked page*: Pushes the image out of view on scroll; rejected per task requirements.
  - *Standard ModalBottomSheet*: Forces a darkened scrim, lacks a persistent Peek state, and dismisses entirely when dragged down.

### Decision 2: Gesture Isolation Architecture
- **Chosen Approach**:
  1. *Background Layer*: Consumes `detectTransformGestures` (zoom/pan) and `detectTapGestures` (tap to hide HUD, double tap to zoom). When scale > 1f, pointer events stay locked to the canvas.
  2. *Drawer Handle & Header*: Dedicated vertical drag listener with velocity tracking to trigger smooth spring transitions between `Peek` (0), `Medium` (1), and `Expanded` (2).
  3. *Inner Content*: Uses standard Compose `verticalScroll(rememberScrollState())`. An intercepted drag only propagates to the drawer state if `scrollState.value == 0` and the user drags downward.

### Decision 3: Toolbar Layering & Z-Order
- **Chosen Approach**: The Top HUD Bar is rendered inside the top-level `Box` after the Drawer Sheet and given `zIndex(2f)` with `statusBarsPadding()`.
- **Why**: Ensures that Back/Close, Title, Scale %, Favorite, Share, and Overflow remain visible and fully clickable regardless of whether the drawer is in Peek, Medium, or Expanded state.

### Decision 4: Preserving State Lifecycles & Dialogs
- **Delete Confirmation**: Uses `AlertDialog` with explicit Cancel and Delete buttons; never deletes silently from the drawer.
- **Editing Metadata**: Uses an explicit Edit mode with Save and Cancel; Cancel restores the original title, description, and notes.
- **Collection Resolution**: Maps `screenshot.collectionIds` against `viewModel.collections` to display human-readable collection names.
- **EXIF & OCR Integrations**: Employs existing `ExifEditorDialog` and `OcrAiSheet` components.

---

## 4. Technical Architecture & Component Hierarchy

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ZoomableImageViewer                              │
│                (Fullscreen Dialog with decorFitsSystemWindows=false)        │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. MEDIA BACKGROUND LAYER [zIndex = 0f]                                     │
│    ┌──────────────────────────────────────────────────────────────────┐     │
│    │ IF isVideo: InAppVideoPlayer(screenshot)                         │     │
│    │ ELSE: AsyncImage + pointerInput(detectTransformGestures)         │     │
│    │       - Scale (1f - 6f), OffsetX/Y Pan, Double-Tap Zoom          │     │
│    └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│ 2. OVERLAY DETAILS DRAWER [zIndex = 1f] (Bottom Anchored)                   │
│    ┌──────────────────────────────────────────────────────────────────┐     │
│    │ ┌──────────────────────────────────────────────────────────────┐ │     │
│    │ │ Drag Handle Pill (48dp Touch Area, Capsule Indicator)         │ │     │
│    │ └──────────────────────────────────────────────────────────────┘ │     │
│    │ ┌──────────────────────────────────────────────────────────────┐ │     │
│    │ │ Header Row: Quick Title, Media Type Badge, Expand/Collapse   │ │     │
│    │ └──────────────────────────────────────────────────────────────┘ │     │
│    │ ┌──────────────────────────────────────────────────────────────┐ │     │
│    │ │ Tab Selector (Overview | Actions | Technical Specs)          │ │     │
│    │ └──────────────────────────────────────────────────────────────┘ │     │
│    │ ┌──────────────────────────────────────────────────────────────┐ │     │
│    │ │ Scrollable Content Container                                 │ │     │
│    │ │  - Overview: Description, Notes, Tags, Collections, GPS      │ │     │
│    │ │  - Actions: AI Analyze, OCR Text Preview, EXIF Editor, Links │ │     │
│    │ │  - Specs: Camera, ISO, Aperture, Dimensions, File Size       │ │     │
│    │ └──────────────────────────────────────────────────────────────┘ │     │
│    └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│ 3. TOP HUD BAR [zIndex = 2f] (Always Accessible)                            │
│    ┌──────────────────────────────────────────────────────────────────┐     │
│    │ [Back / Close]  Title & Res    [Scale %] [Fav] [Share] [Overflow]│     │
│    └──────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component & Data Model Mapping

| Component | File / Location | Responsibility |
| :--- | :--- | :--- |
| `ZoomableImageViewer` | `ui/components/ZoomableImageViewer.kt` | Fullscreen Dialog host, media background, top HUD bar, and overlay drawer integration |
| `ViewerDetailsDrawer` | `ui/components/ViewerDetailsDrawer.kt` | Reusable 3-position bottom overlay sheet with drag handle, tabs, and scrollable content |
| `ScreenshotDetailScreen` | `ui/screens/ScreenshotDetailScreen.kt` | Wires thumbnail/image tap to launch `ZoomableImageViewer` with current `screenshotId` |
| `ScreenshotsViewModel` | `viewmodel/ScreenshotsViewModel.kt` | Manages state flows: `allScreenshots`, `collections`, `exifDataState`, `toggleFavorite`, `addTag`, `removeTag`, `updateScreenshot`, `deleteScreenshot` |
| `ExifEditorDialog` | `ui/components/ExifEditorDialog.kt` | Modal dialog for editing camera metadata and binary EXIF headers |
| `OcrAiSheet` | `ui/components/OcrAiSheet.kt` | Modal bottom sheet for OCR text extraction, LLM artifact cleanup, and metadata writing |

---

## 5. Verification Plan

### Automated & Unit Tests
1. **Robolectric Component & Logic Tests**:
   - Run existing unit test suite: `gradle :app:testDebugUnitTest`.
   - Add/verify `ViewerDetailsDrawerTest` testing:
     - Initial drawer state defaults to `Peek`.
     - Snapping between `Peek`, `Medium`, and `Expanded`.
     - Collection ID to collection name resolution.
     - Save vs Cancel editing flows.
     - Delete dialog confirmation guard.
2. **Build Compilation**:
   - Verify zero compiler warnings/errors: `compile_applet`.
   - Verify Android packaging: `gradle assembleDebug`.

### Manual & Gesture Verification
- Verify image pan and zoom gestures at 100%, 250%, and 600% zoom levels.
- Verify dragging drawer handle smoothly snaps between all 3 heights.
- Verify scrolling inner list at Expanded height scrolls content smoothly without collapsing sheet.
- Verify Top HUD Bar (Close, Favorite, Share) remains clickable when drawer is Expanded.
- Verify video playback continues uninterrupted in Peek and Medium drawer positions.
