# EmreShots audit

## Scope

Static audit of the supplied source archive, followed by a conservative cleanup pass intended to preserve product behavior.

## Findings

| Severity | Finding | Status |
|---|---|---|
| High | Global cleartext HTTP was enabled in the main manifest | Fixed: release defaults to cleartext-disabled; debug-only manifest permits local development HTTP. |
| High | AI analysis assumed every media item had a real filesystem path | Fixed: AI/OCR now materialize `content://` media into app cache on demand. |
| High | Custom provider API keys are stored in the Room entity as plaintext | Not fixed yet; requires a deliberate Keystore-backed secret migration. |
| High | Gemini API key is supplied to a client-side app build | Not fixed: this is an architectural credential issue, not a safe local refactor. |
| High | Room uses `fallbackToDestructiveMigration(true)` | Not fixed: safe migrations require schema history not present in the supplied archive. |
| Medium | Settings reset on process recreation | Fixed with DataStore-backed preferences. |
| Medium | Default collection seeding was destructive | Fixed: initialization is centralized and seeding is non-destructive. |
| Medium | Custom debug signing depended on an ignored keystore | Fixed: standard Android/AGP debug signing is used. |
| Medium | Unused Firebase, Retrofit/Moshi, logging, CameraX, location, and identity configuration increased build surface | Fixed. |
| Medium | Dead AI Studio/Cloud Providers screens and routes remained | Fixed: provider management remains in Settings. |
| Low | `POST_NOTIFICATIONS` was unused | Fixed: permission removed. |
| Low | The archive lacks the Gradle wrapper JAR/scripts | Not fixed because they were not supplied and could not be safely invented. |

## Recommended next pass

1. Add real Room migrations and enable schema export.
2. Move provider secrets behind Android Keystore.
3. Decide on the final package/application ID before a production release.
4. Split the large ViewModel and Compose screens by use case.
5. Add instrumentation coverage for MediaStore URI access and provider HTTP behavior.
6. Restore a complete Gradle wrapper before distributing the repository.
