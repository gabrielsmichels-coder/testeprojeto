# Version 0.4.0 — validation

## Local build

`assembleDebug`, `testDebugUnitTest`, and `lintDebug` completed with exit code 0.

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 2,571,944 bytes.
- Package `com.example.projectorlauncher`, version 0.4.0, versionCode 4, min API 28, target/compile API 35.
- `apksigner verify`: passed; `aapt dump badging`: package/version/API checked.
- SHA-256: `9ec59b23d711c3882a7a3ff1c50127c0d66f03e085d4bcc76d509fddf5233a50`.
- Unit tests: **9 passed, 0 failed, 0 skipped**. Tests cover existing favorite rules, all three palettes, translucent glass over black/white backdrops, supported/unknown package mappings, bounded resolution/aspect ratio and opaque high-contrast surfaces.
- Lint: **0 errors, 3 warnings**. Warnings are landscape orientation, Android 12+ backup configuration and the intentional layered root FrameLayout. No error suppression/baseline was added.
- XML parsed successfully; GitHub Actions workflow and application manifest are unchanged.

## Delivered behavior

Glass panels use translucent native gradients, highlights and layered borders, without blur or RenderEffect. App focus scales to 1.05× without changing layout. Home omits repeated favorite badges. Original installed app icons, app launching, favorites, menus, sounds, themes, settings and projector Intents are retained.

Home focus selects original service-themed cinematic landscapes (not promotional imagery) for Netflix, Prime Video, Max/HBO, Disney+, Globoplay, YouTube, Apple TV and Paramount. Unknown packages/controls select neutral art. Artwork provenance/license is in ARTWORK.md. No images are downloaded or scraped, and user-photo import is not part of this version.

Wallpaper requests debounce for 180 ms, cancel stale work/results and crossfade for 380 ms. A worker rasterizes static artwork to actual view dimensions capped at 1920×1080. Current and incoming RGB_565 frames are the only retained wallpapers (at most approximately 8 MiB at 1080p, separate from bounded visible app icons). Layers are cleared on stop/rebuild; no continuous effects run. Dynamic wallpapers can be disabled in Appearance. Reduced motion eliminates fades/scaling; High Contrast uses solid opaque surfaces and no wallpaper images.

The contrast test initially caught insufficient dark secondary-text contrast over bright glass. Secondary text was brightened and the unchanged contrast threshold now passes.

## CI and physical testing

The existing Java 17 GitHub Actions workflow remains intact: push-main/manual triggers, unit tests, lint, debug APK build and `projector-launcher-debug` upload. Local build success does not establish remote completion; check the current Actions run separately if API access is unavailable.

No emulator or physical projector run was performed. On the Bettdow AC1041-X7, verify fast D-pad focus changes/cancelled fades, artwork colors and projector text contrast, sounds/reduced motion, favorite management, Wi-Fi status, all native projector Intents, app-return lifecycle and RAM/frame pacing on the 1 GB device. Memory bounds above are calculated allocation limits, not measured total application RAM.

ZEASN, factory HOME configuration and projector state were not changed. Updating an installed debug build requires the same signing key; different cloud/Windows/CI debug keys may prevent an in-place update even with the same package ID.
