# Tasks

## Deferred

- [ ] **Integrate ExifTool for advanced metadata handling**
  - Evaluate ExifTool as a complement or replacement for AndroidX `ExifInterface` where it provides better metadata coverage or write reliability.
  - Design an Android-compatible packaging/invocation approach rather than assuming ExifTool is a normal Gradle dependency.
  - Review ExifTool licensing (GPL) and the implications for EmreShots distribution before bundling it.
  - Preserve the current `ExifInterface` path as a fallback while evaluating the integration.
  - Add tests covering EXIF/IPTC/XMP read/write behavior and failure/fallback cases.

- [ ] **Add on-device Gemma/ML inference alongside cloud models**
  - Add a local/on-device Gemma or equivalent ML inference backend for screenshot analysis and organization.
  - Keep cloud providers available as an optional alternative, with an explicit model/provider selection in the UI.
  - Define capability, model-download, storage, performance, battery, and offline behavior for on-device inference.
  - Ensure sensitive screenshot content can be processed locally without requiring a cloud API key.
  - Add tests for model selection, offline/local execution, fallback/error states, and cloud-vs-local routing.

- [ ] **Add local OCR + cloud text-only AI**
  - Implement on-device OCR for extracting screenshot text without uploading the image.
  - Add a cloud text-only AI path that sends extracted OCR text/metadata rather than the screenshot image itself.
  - Make the OCR and cloud-text stages independently configurable so users can choose fully local, local OCR + cloud text AI, or existing cloud image analysis.
  - Clearly disclose what data leaves the device and avoid sending the original screenshot when using the text-only path.
  - Add tests for OCR extraction, text-only request payloads, privacy routing, and failure/fallback behavior.
