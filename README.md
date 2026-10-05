# EmreShots

EmreShots is a local-first Android media organizer for photos, videos, and screenshots. It combines a Jetpack Compose gallery with Room persistence, EXIF tools, OCR, and pluggable AI providers.

## Build and run

Open the project in Android Studio with a compatible Android SDK/Gradle toolchain and run the `app` debug variant.

For Gemini, create a local `.env` from `.env.example` and never commit real credentials.

Debug builds permit cleartext HTTP for local development endpoints such as an emulator-hosted Ollama service. Release builds explicitly disable cleartext traffic.

## Cleanup revision

This revision hardens release network configuration, persists settings with DataStore, removes unused build/dependency configuration and dead provider/AI Studio routes, removes the unused notification permission, and adds safer local signing ignores.

Known production-hardening items remain: Keystore-backed provider secrets, real Room migrations, stable package identity, and a complete Gradle wrapper.
