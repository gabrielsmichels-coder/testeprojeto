# 0.5.0 — Free My TV Edition: validation

## Verified locally

- Full JDK 17 (Temurin), Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, SDK 35 / min API 28.
- `assembleDebug`, `testDebugUnitTest`, `lintDebug`: exit 0, including a build with `CI=true`.
- **15 unit tests passed, 0 failed, 0 skipped**: existing themes/favorites, mappings/image bounds, adapted spring settling/interruption, safe TMDB paths/regions/cache budget, nonzero and bounded transient decode sampling.
- Lint: **0 errors, 3 warnings**. Remaining warnings: landscape, Android 12+ backup rules, intentional layered FrameLayout. No errors suppressed.
- Package unchanged: `com.example.projectorlauncher`; versionCode 5, versionName 0.5.0; metadata checked with AAPT.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 3,181,209 bytes, verified by apksigner.
- SHA-256: `30dcc5e48bb4b3b107ef292ea4d154588c58f8dc52d79c1afc0d3be98e679167`.
- CI unsigned input: `app/build/outputs/apk/debug/app-debug-unsigned.apk`.
- Exact signing shell from the workflow executed locally twice with the existing private development keystore, never written into the repository. Same certificate both runs; optional SHA-256 certificate pin passed; temporary key removed. Missing-secret case fails explicitly before distribution. This is a test of the signing path, not configuration of GitHub Secrets.
- MIT copyright/license, Inter OFL notice and Inter font verified inside the APK. Adaptations documented in FREE_MY_TV_ADAPTATION.md.
- XML, workflow YAML, secret-pattern scan and `git diff --check` passed.

The machine's original Java runtime lacked jlink when Java source ports were introduced. A full official Temurin JDK 17 was downloaded through the supported HTTPS route and checked against its published SHA-256, then extracted outside the repository. No verification was disabled or SDK files committed.

## Not yet observed / external prerequisites

No physical AC1041-X7 or emulator test was performed. Composition/typography are adapted from the upstream screenshot and source, not verified by a screenshot of this app running on hardware. D-pad/layout, shadows/springs, Android Keystore UI, TMDB authenticated requests, cache/network failures and 1 GB device frame-time/heap require device testing. No private TMDB credential was supplied; no authenticated catalog query was claimed as passed. Offline UI is implemented without a required network request.

Pipeline installable output requires four protected repository signing secrets (SIGNING.md). Their current availability cannot be checked through the blocked GitHub API in this session. No unstable runner certificate is distributed as fallback. PR builds publish unsigned inspection artifacts, which must be signed before installing. Successful local signing does not prove a GitHub-hosted run completed or uploaded its artifact.

A development branch is used; main must remain unchanged until review. PR creation and remote run status are separate GitHub operations and require API access; final delivery reports their observed outcomes.

Factory ZEASN (`com.zeasn.whale.saas`), default HOME and projector system configuration were not touched. Native HDMI, keystone, zoom, source and orientation Intents remain in the app with graceful errors.
