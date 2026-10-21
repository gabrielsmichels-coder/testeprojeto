# Version 0.3.0 — validation

## Current-instance result

`assembleDebug`, `testDebugUnitTest`, and `lintDebug` completed successfully (exit 0).

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 2,541,712 bytes.
- Package: `com.example.projectorlauncher` (unchanged).
- Version: 0.3.0, versionCode 3, minimum API 28, compile/target API 35.
- Debug signature verified with Android Build-Tools `apksigner`.
- APK package/version/permission metadata checked with `aapt dump badging`.
- SHA-256: `66204e1b4d8a1a130cf313539f806e8287ce437c258475aa62fa9b72f2066119`.
- Four unit tests executed: 4 passed, 0 failed, 0 skipped. They check readable text/focus contrast in all themes, favorite removal at capacity, maximum size, ordering, deduplication and preservation of empty selections.
- Android Lint: 0 errors, 2 warnings (intentional landscape orientation; Android 12+ backup configuration). No suppressed errors or baseline.
- XML/YAML, required CI packages, artifact name and obsolete SDK package exclusion checked.

The SDK, Gradle and proxy configuration from the prepared cloud workspace were reused; no TLS/checksum verification was disabled. The SDK analytics write warning in the read-only home directory is nonfatal. Some APIs needed for Android 9 compatibility produce deprecation warnings against SDK 35.

## UI and behavior implemented

Home contains at most six favorite apps; all apps live in a dedicated grid. The full-screen internal Settings dashboard contains eight categories, with preserved projector Intents inside Picture/Projection and Inputs. Appearance applies Light, Dark or High Contrast to cards, text, menus and focus. Favorite choices, themes, sounds and reduced motion persist in SharedPreferences. Native sound effects and short property/window animations are optional. No mascot code or artwork remains.

Real package icons are loaded only for visible cards, on one worker thread, rasterized proportionally with transparency at a maximum of 144×144 pixels, and released offscreen. No network image or audio assets, continuous animation, runtime UI framework dependency or service is added. ACCESS_NETWORK_STATE is used only to read connection status.

## CI and limits

The GitHub Actions workflow retains Java 17, manual/push-main triggers, current SDK packages and the `projector-launcher-debug` artifact (14-day retention). It now runs the same unit tests and lint before uploading the APK. Local validation does not establish that a GitHub-hosted run completed; remote results must be observed separately.

No emulator or physical projector test was performed. Physical testing remains necessary for D-pad scrolling/long-press, all three themes and menus, audio behavior and disabling sounds, reduced motion, Wi-Fi status changes, proprietary intent authorization and RAM/frame-time measurements on the 1 GB device. The API-28 compatibility claim here is based on build configuration/API analysis, not an emulator run.

The factory ZEASN package, device, HOME preference and projector configuration were not changed. Updates of our launcher also require the same signing certificate as the previously installed APK; debug keys from separate machines/runners may differ. See README.md.
