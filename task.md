# Tasks

## Deferred

- [ ] **Integrate ExifTool for advanced metadata handling**
  - Evaluate ExifTool as a complement or replacement for AndroidX `ExifInterface` where it provides better metadata coverage or write reliability.
  - Design an Android-compatible packaging/invocation approach rather than assuming ExifTool is a normal Gradle dependency.
  - Review ExifTool licensing (GPL) and the implications for EmreShots distribution before bundling it.
  - Preserve the current `ExifInterface` path as a fallback while evaluating the integration.
  - Add tests covering EXIF/IPTC/XMP read/write behavior and failure/fallback cases.

- [x] **Add a pluggable on-device vision model framework alongside cloud models**
  - Add a provider-neutral on-device vision abstraction with model-family metadata, device capability detection, recommendation, persistence, and runtime adapters.
  - Offer multiple vision families/profiles instead of locking the app to Gemma (SmolVLM, Gemma, Qwen-VL, MiniCPM-V, plus future adapters).
  - Keep cloud providers available as an optional alternative, with an explicit model/provider selection in the UI.
  - Define capability, model-download, storage, performance, battery, and offline behavior for on-device inference; runtime/model binaries remain optional so the base APK stays small.
  - Ensure sensitive screenshot content can be processed locally without requiring a cloud API key.
  - Add tests for device-tier model selection and no-compatible-model behavior; add runtime/download/privacy tests as concrete adapters are integrated.

- [ ] **Add local OCR + cloud text-only AI**
  - Implement on-device OCR for extracting screenshot text without uploading the image.
  - Add a cloud text-only AI path that sends extracted OCR text/metadata rather than the screenshot image itself.
  - Make the OCR and cloud-text stages independently configurable so users can choose fully local, local OCR + cloud text AI, or existing cloud image analysis.
  - Clearly disclose what data leaves the device and avoid sending the original screenshot when using the text-only path.
  - Add tests for OCR extraction, text-only request payloads, privacy routing, and failure/fallback behavior.
