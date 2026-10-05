EmreShots Tasks

Production Hardening (Blockers)





Finalize package / application identity





Replace com.example package and application ID with a stable production identity.



Update namespaces, manifests, BuildConfig references, and any hard-coded paths.



Decide on final branding (EmreShots / emreshots) before first public release.



Implement real Room migrations





Remove fallbackToDestructiveMigration(true).



Enable schema export and maintain versioned migration paths.



Add tests that exercise upgrade paths from current schema versions.



Complete Keystore-backed secret storage





Ensure all provider API keys (Gemini, custom/OpenAI-compatible) are stored exclusively via Android Keystore / EncryptedSharedPreferences or equivalent.



Migrate any residual plaintext Room storage of secrets.



Verify that keys never appear in logs, backups, or debug builds unintentionally.



Restore complete Gradle wrapper





Commit the full Gradle wrapper scripts and JAR so the repository builds cleanly from a fresh clone without local toolchain assumptions.



Release readiness





Produce signed release builds with proper ProGuard/R8 rules.



Add basic privacy policy / data-safety notes suitable for Play Store or F-Droid.



Publish at least one public APK (GitHub Releases) and consider F-Droid submission.

Deferred / Feature Work





Integrate ExifTool for advanced metadata handling





Evaluate ExifTool as a complement or replacement for AndroidX ExifInterface where it provides better metadata coverage or write reliability.



Design an Android-compatible packaging/invocation approach rather than assuming ExifTool is a normal Gradle dependency.



Review ExifTool licensing (GPL) and the implications for EmreShots distribution before bundling it.



Preserve the current ExifInterface path as a fallback while evaluating the integration.



Add tests covering EXIF/IPTC/XMP read/write behavior and failure/fallback cases.



Add a pluggable on-device vision model framework alongside cloud models





Add a provider-neutral on-device vision abstraction with model-family metadata, device capability detection, recommendation, persistence, and runtime adapters.



Offer multiple vision families/profiles instead of locking the app to Gemma (SmolVLM, Gemma, Qwen-VL, MiniCPM-V, plus future adapters).



Keep cloud providers available as an optional alternative, with an explicit model/provider selection in the UI.



Define capability, model-download, storage, performance, battery, and offline behavior for on-device inference; runtime/model binaries remain optional so the base APK stays small.



Ensure sensitive screenshot content can be processed locally without requiring a cloud API key.



Add tests for device-tier model selection and no-compatible-model behavior; add runtime/download/privacy tests as concrete adapters are integrated.



Add local OCR + cloud text-only AI





Implement on-device OCR for extracting screenshot text without uploading the image.



Add a cloud text-only AI path that sends extracted OCR text/metadata rather than the screenshot image itself.



Make the OCR and cloud-text stages independently configurable so users can choose fully local, local OCR + cloud text AI, or existing cloud image analysis.



Clearly disclose what data leaves the device and avoid sending the original screenshot when using the text-only path.



Add tests for OCR extraction, text-only request payloads, privacy routing, and failure/fallback behavior.

Architecture & Code Quality





Split large ViewModel and Compose screens by use case





Break ScreenshotsViewModel and monolithic screens into focused, testable units (gallery, detail, AI indexing, settings, EXIF, etc.).



Improve separation of concerns between media scanning, AI analysis, and UI state.



Expand instrumentation / integration tests





Cover MediaStore URI access and content:// → cache materialization paths.



Test provider HTTP behavior (success, quota, network errors, cleartext restrictions).



Add coverage for background sync worker, duplicate detection, and backup/restore flows.



Performance & low-end device path





Validate and document behavior on low-RAM / low-storage devices.



Ensure on-device model selection and background work respect battery and thermal constraints.

Product Differentiation





Strengthen multi-media identity





Lean into photos + videos + screenshots rather than pure screenshot cloning.



Surface advanced EXIF/metadata tools as a clear differentiator versus Shots Studio and Pixel Screenshots.



Privacy-first defaults & disclosure





Make fully-local processing the recommended default where possible.



Improve in-app messaging about what data (if any) leaves the device for each AI mode.



Collections & organization polish





Improve collection auto-add, filtering, and bulk operations to approach Shots Studio usability.



Consider smart filters (date range, source app, content type) inspired by Pixel Screenshots.

Distribution & Community





Public presence





Write a proper README with screenshots, feature matrix, privacy notes, and build instructions.



Add CONTRIBUTING.md and clear issue templates.



Publish first release and track feedback against Shots Studio / Pixel Screenshots feature gaps.
