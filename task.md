# Tasks

## Deferred

- [ ] **Integrate ExifTool for advanced metadata handling**
  - Evaluate ExifTool as a complement or replacement for AndroidX `ExifInterface` where it provides better metadata coverage or write reliability.
  - Design an Android-compatible packaging/invocation approach rather than assuming ExifTool is a normal Gradle dependency.
  - Review ExifTool licensing (GPL) and the implications for EmreShots distribution before bundling it.
  - Preserve the current `ExifInterface` path as a fallback while evaluating the integration.
  - Add tests covering EXIF/IPTC/XMP read/write behavior and failure/fallback cases.
