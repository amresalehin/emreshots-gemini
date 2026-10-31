# EmreShots UI/UX Audit — `main`

**Review date:** 2026-10-29  
**Repository:** https://github.com/amresalehin/emreshots-gemini  
**Audited branch:** `main`  
**Audited commit:** `44e393cc1b532b27d2fb8594ffd995d0373da5e8`

## Scope and comparison note

The repository has two branches: `main` and `main-archive`. The default branch is `main-archive`, but `main` is 300 commits ahead of it. This report audits source code from `main`; it does not assume that findings from an older uploaded archive remain true.

The uploaded ZIP was not byte-for-byte compared against the GitHub tree in this pass. This document is therefore a fresh audit of the current `main` branch, not a claim that the ZIP is identical.

## Executive assessment

EmreShots already has meaningful product foundations: Compose/Material 3, a configurable gallery with Grid/Masonry/Feed/List modes, search, collection/file-type filters, batch AI/OCR actions, media detail editing, OCR preview, tag editing, EXIF handling, reminders, and dedicated processing and settings destinations.

The next UX step should not be a visual redesign for its own sake. It should reduce hidden actions, simplify the first-use and everyday gallery experience, improve metadata hierarchy, and clarify the difference between reviewing media and operating AI/indexing workflows.

## Confirmed implementation findings

### 1. OCR status message is displayed as source text — P1

**File:** `app/src/main/java/com/example/ui/screens/GalleryHeader.kt`, around line 332.

The OCR menu item's status branch is written as a quoted Kotlin string:

```kotlin
ocrPendingCount > 0 -> "stringResource(R.string.media_waiting_for_ocr, ocrPendingCount)"
```

This displays the expression literally instead of resolving the string resource. Replace it with:

```kotlin
ocrPendingCount > 0 -> stringResource(R.string.media_waiting_for_ocr, ocrPendingCount)
```

Add a UI test for the pending OCR branch and a separate test for the disabled/completed states.

### 2. The gallery's All filter is implicit rather than visible — P1

**File:** `app/src/main/java/com/example/ui/screens/GalleryFilters.kt`, around line 144.

The visible filter chips are created from `ScreenshotFilter.entries.filter { it != ScreenshotFilter.ALL }`. The row itself has no All chip/reset action. Collection and file-type filters can be reset individually, but the overall active filter/search/collection/type state is not exposed as one obvious reset action.

**Recommendation:** Add an explicit All/reset state and a compact “Clear filters” affordance only while any filter is active. Define reset semantics carefully:
- selected filter → `ALL`
- selected collection → `null`
- selected file type → `null`
- selected folder → `null`
- optionally clear search only if the UI label explicitly says “Clear all”; otherwise keep search reset separate.

This is a discoverability recommendation; it is not a claim that the data model cannot represent All.

### 3. Gallery view modes exist, but are nested in the overflow menu — P1 UX

**File:** `app/src/main/java/com/example/ui/screens/GalleryHeader.kt`, around lines 219–360.

Grid/Masonry/Feed/List choices are implemented via `GalleryMenuChoiceRow` in the More menu. Each choice is 48dp tall with 11sp text. This is functionally available, so do not treat it as a missing feature. However, changing a frequently used presentation mode requires opening More and navigating a relatively dense menu.

**Recommendation:** Surface the current view mode as a small, direct toolbar control (for example, an icon button that opens a dedicated view selector) while keeping secondary display toggles in More. Do not put every mode inline if it makes the toolbar crowded. Preserve the accessible labelled menu as a fallback.

### 4. Settings is a navigation hub labelled “Workspace” — P2 information architecture

**File:** `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`.

The current screen has seven destination cards: AI Studio, Cloud Providers, On-device Vision, OCR & Text, Library Processing, Backup & Restore, and Collections. Several are feature-management destinations rather than settings, and the top label is “Workspace.”

**Recommendation:** Keep the simple hub but group routes by intent:
- **Library:** Processing, Collections, Backup & Restore
- **AI & Search:** AI Studio, On-device Vision, OCR & Text, Cloud Providers

Longer-term, consider naming the screen “Settings & tools” or splitting the most-used processing action out of Settings, while preserving the route for less frequent configuration. Avoid adding more cards without grouping or prioritization.

### 5. Detail view contains dense, mixed-priority content — P1 UX

**Files:** `ScreenshotDetailScreen.kt` and `ScreenshotDetailContent.kt`.

Confirmed detail functions include title/description/notes editing, tags, OCR preview and extraction, AI analysis, EXIF editing, collections, reminders, sharing, deleting, and navigation between adjacent media. The screen uses a scrollable column; the detail content has generous spacing in some sections (24dp section spacing) but uses small section labels and 11–12sp action labels in places.

**Recommendation:** Establish a clear hierarchy:
1. Media viewer and primary navigation.
2. Title and description.
3. Primary actions: Analyze/Re-analyze, OCR, EXIF (EXIF only for supported media).
4. Tags and useful extracted content.
5. Collapsible “Metadata” section for technical EXIF, file path, file size, device details, timestamps, and advanced operations.
6. Overflow for rare/destructive actions, with destructive actions visually separated.

Do not move OCR/EXIF functionality out of reach; the goal is layered disclosure, not feature removal. Consider using tabs/segmented sections only after validating available screen height and how often users switch between AI content and metadata.

### 6. Typography has a 10sp labelSmall token — P2 accessibility/readability

**File:** `app/src/main/java/com/example/ui/theme/Type.kt`, around line 98.

The theme defines `labelSmall` as 10sp/14sp line height. The gallery also uses 11sp view selector text and 11sp OCR action text in some locations. These values can be used for tertiary metadata but should not carry important instructions, state, or frequently used actions.

**Recommendation:** Prefer 12sp as the minimum for meaningful secondary text, and use 14sp body text for explanatory content. Keep small type for compact non-interactive metadata only. Check display scaling, accessibility font size, truncation, localization, and TalkBack traversal; do not simply increase every label and break the gallery density.

### 7. Several gallery concerns are concentrated into one overflow menu — P2 information scent

**File:** `GalleryHeader.kt`.

The overflow menu includes Sort, Group by, view mode, show file names, show tags, batch AI Vision, batch OCR, batch rename, and Settings. The available actions are powerful but mixed in frequency and conceptual category.

**Recommendation:** Organize into two levels:
- **View controls:** view mode, display names/tags, sort, grouping.
- **Library operations:** AI analysis, OCR, batch rename, Settings.

The menu should communicate processing state and pending counts with concise, localized labels. Keep destructive operations (if added later) away from routine view controls.

## Design direction

EmreShots should feel like a focused personal media library with intelligent enrichment—not a configuration dashboard.

### Visual language
- Keep Material 3, but use a restrained semantic palette. Avoid making every section a separate filled card.
- Use consistent 8dp spacing increments, 16dp standard screen gutters, and larger gaps only between meaningful semantic sections.
- Reserve strong primary-container surfaces for one main status/action area, such as active processing.
- Keep rounded corners consistent with the existing shape scale; avoid introducing multiple competing corner radii.
- Use media itself as the visual focus. Reduce decorative outlines and card elevation where they compete with thumbnails.
- Use tonal surfaces to group secondary metadata, not to create nested cards within cards.

### Gallery information hierarchy
1. Header: current library/context, item count, search, view control.
2. Primary filters: a few stable, high-value filters.
3. Optional secondary filter row: collection, file type, folder, with active state and a clear reset path.
4. Media surface: Grid/Masonry/Feed/List.
5. Processing state: a compact, dismissible but re-openable status that never blocks media browsing.

Keep batch AI/OCR reachable but avoid placing processing status, settings, sorting, grouping, and every batch action at equal visual priority.

### Media detail hierarchy
- Keep the image/video large and stable; navigation gestures must not conflict with zoom or editing gestures.
- Make the title readable and editable without forcing users into a long technical sheet.
- Display AI description and tags as useful content rather than a wall of fields.
- Show OCR preview with clear “View / Extract” actions and explicit processing/error state.
- Surface location/date when reliable, while clearly distinguishing inferred/generated values from original metadata.
- Put raw EXIF and file diagnostics under a collapsible metadata disclosure.
- Use a confirmation step for deletion and explain whether removing a library record also deletes original device media.

### Processing experience
- Show completed, failed, pending, and currently running counts together.
- Show the active model and current item without overwhelming the screen with telemetry.
- Label stop/cancel semantics clearly. If a run can be resumed, describe what happens when stopped.
- Retry must act only on the failed-item set; preserve failure reasons where possible.
- Distinguish “not yet processed” from “processing disabled / model unavailable / permission missing.”
- Make processing status accessible from the gallery and from Settings/tools.

### Settings and advanced tools
- Organize settings by user intent, not implementation subsystem.
- Show current provider/model and health/availability near the action that uses them.
- Keep secrets masked and avoid exposing raw keys.
- Use progressive disclosure for advanced endpoint parameters, model imports, language packs, and debug details.
- Explain storage/network/privacy consequences before enabling cloud processing or downloading a model.

## Suggested implementation roadmap

### Phase 0 — Correctness and clarity
- Fix the OCR status string resource bug.
- Add a clear active-filter indicator and a well-defined reset action.
- Verify the failure retry path and feedback states end-to-end.
- Add tests for empty state versus filtered-empty state.
- Verify status/navigation bar contrast in light and dark themes.

### Phase 1 — Gallery usability
- Provide direct access to the current view mode without relying exclusively on More.
- Group view settings and library operations in the overflow.
- Make filter state visible and reversible.
- Preserve selected view/columns/filter state predictably across navigation.
- Ensure the item count reflects all active filtering conditions.

### Phase 2 — Detail hierarchy
- Restructure the content into “Overview,” “AI & text,” and “Metadata” areas (collapsible sections are the lowest-risk first implementation).
- Promote the most common actions while keeping advanced functions accessible.
- Improve empty, loading, unavailable, permission-denied, and failed states.
- Validate adjacent-item navigation against zoom/pan gestures.

### Phase 3 — Design-system refinement
- Audit all hard-coded font sizes and colors.
- Standardize typography, spacing, control heights, icon sizes, and surfaces.
- Test larger system font scales and narrow devices.
- Validate TalkBack labels and focus order, especially on chips, icon buttons, tags, and menu choices.

### Phase 4 — Quality and performance
- Test gallery rendering with large libraries and frequent filtering.
- Keep expensive AI, OCR, metadata and thumbnail work away from UI-critical paths.
- Ensure active indexing updates the content without resetting a user's scroll position or unexpectedly changing the gallery layout.
- Exercise state restoration after process death, rotation, and return from settings.

## Acceptance criteria

- OCR pending-state text is localized and never displays source-code expressions.
- All active filters can be identified and cleared without trial-and-error.
- The selected gallery view mode is visible or discoverable in one obvious action.
- A user can identify a media item, read its description, inspect OCR, edit tags, and open technical metadata without encountering an undifferentiated wall of fields.
- Every processing state has a clear label and actionable recovery path where possible.
- No important action depends on text smaller than 12sp; smaller metadata is tertiary and nonessential.
- Theme contrast remains correct on all routes, including dialogs and sheets.
- Gallery browsing remains stable while indexing or OCR updates item content.
- UI tests cover filter reset, view-mode selection, OCR status branches, processing retry, and empty-state behavior.

## Source paths reviewed

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/screens/ScreenshotsScreen.kt`
- `app/src/main/java/com/example/ui/screens/GalleryHeader.kt`
- `app/src/main/java/com/example/ui/screens/GalleryFilters.kt`
- `app/src/main/java/com/example/ui/screens/GalleryContent.kt`
- `app/src/main/java/com/example/ui/screens/GalleryStatusSection.kt`
- `app/src/main/java/com/example/ui/screens/ScreenshotDetailScreen.kt`
- `app/src/main/java/com/example/ui/screens/ScreenshotDetailContent.kt`
- `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProcessingScreen.kt`
- `app/src/main/java/com/example/ui/theme/Type.kt`
- `app/src/main/java/com/example/ui/theme/Theme.kt`

## Important limitations

This is a source-level UX audit, not a rendered-device usability study. No APK was built or launched in a device emulator during this pass. Some items (notably filter reset behavior, real retry semantics, gesture conflicts, actual visual density, and talkback order) need interaction tests before being classified as implementation defects.
