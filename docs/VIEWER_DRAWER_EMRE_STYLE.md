# Viewer details drawer — Emre-style refactor

This branch refactors `ScreenshotDetailScreen` in place to provide a restrained, monochrome details drawer.

## Scope
- Replaces the prominent nested detail card with a bottom-rounded-top sheet surface and drag-handle visual.
- Adds **Overview** and **Actions** tabs.
- Overview contains album membership, tags, location, and collapsible technical metadata.
- Actions contains AI description analysis, OCR extraction, EXIF editing, detected link utilities, and deletion.
- Keeps existing ViewModel methods and their persistence paths.
- Does not introduce a People carousel or face-recognition controls. EmreShots functionality remains focused on screenshot/media details and OCR/AI utilities.

## Notes
- This is a refactor of the existing detail route, not a new navigation destination; the existing gallery -> detail navigation remains intact.
- The details content and actions are native Compose UI. OCR, AI analysis, EXIF editing, and deletion still use the existing ViewModel/services.
