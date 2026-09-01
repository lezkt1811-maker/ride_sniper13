# Ride Sniper

A fully offline Android app that watches the Uber Driver app's on-screen ride
offer, reads it with on-device OCR, and tells you — in under a second — whether
the ride is worth taking based on **your** vehicle costs and profitability
thresholds. It never touches Uber's UI and never sends data anywhere.

- No AccessibilityService, no auto-accept/auto-decline. You always make the final call.
- No login, no server, no analytics, no ads, no internet permission at all.
- All screen capture is processed in memory and discarded by default.

---

## Get an APK without installing Android Studio

Push this repo to GitHub, then GitHub Actions → **Build Ride Sniper APK** → Run workflow (or just push to `main`). It builds on GitHub's servers using a real Gradle/Android SDK install (`.github/workflows/build-apk.yml`), then attaches `ride-sniper-debug-apk` as a downloadable artifact on that run — no local Gradle wrapper jar needed. Download it to your phone and install it (you'll need to allow installs from your file manager / browser once, since it's unsigned debug build, not from the Play Store).

## Architecture Plan

```
UI (Compose/Material3)
  ├─ MainActivity — permission flow (overlay, MediaProjection, notifications), nav host
  ├─ HomeScreen — HUD, quick gas entry, strategy switch, latest result
  ├─ HistoryScreen — Room-backed ride list, accept/decline marking, CSV export
  ├─ StatsScreen — aggregate dashboard with Today/7d/30d/All-time filters
  ├─ SettingsScreen — every threshold from Core Feature 10, DataStore-backed
  ├─ DebugScreen — raw OCR text + matched-rule audit trail
  └─ CorrectionSheet — manual fallback bottom sheet

RideSniperViewModel — single source of truth for the UI, backed by:
  ├─ SettingsDataStore (Jetpack DataStore Preferences)
  ├─ RideRepository (Room) — history + stats aggregation + CSV export
  └─ DestinationRiskStore (SharedPreferences) — manual zone ratings

RideSniperForegroundService — the always-on pipeline while Uber is open:
  ScreenCaptureManager (MediaProjection)
        │  captureFrame() → in-memory Bitmap
        ▼
  OcrEngine (ML Kit Text Recognition, on-device)
        │  raw text + heuristic confidence
        ▼
  OcrParser (regex + heuristics)
        │  OcrParseResult (nullable per-field)
        ▼
  RideCalculator (pure function, no Android deps)
        │  RideCalculationResult (recommendation + all derived numbers)
        ▼
  OverlayController → floating bubble + result card (WindowManager + Compose)
        │
        ▼
  RideRepository.saveResult() → Room history

If OcrParseResult is missing a field or confidence is below threshold, the
service notifies MainActivity, which shows CorrectionSheet pre-filled with
whatever OCR did find.
```

## Project Tree

```
ride-sniper/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── .gitignore
├── README.md
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   ├── values/themes.xml
        │   │   ├── drawable/ic_launcher_background.xml
        │   │   ├── drawable/ic_launcher_foreground.xml
        │   │   ├── mipmap-anydpi-v26/ic_launcher.xml
        │   │   ├── mipmap-anydpi-v26/ic_launcher_round.xml
        │   │   └── xml/file_paths.xml
        │   └── java/com/ridesniper/app/
        │       ├── RideSniperApp.kt
        │       ├── model/
        │       │   ├── StrategyPreset.kt
        │       │   ├── RideOffer.kt
        │       │   └── AppSettings.kt
        │       ├── calculator/
        │       │   └── RideCalculator.kt
        │       ├── ocr/
        │       │   ├── OcrEngine.kt
        │       │   └── OcrParser.kt
        │       ├── capture/
        │       │   └── ScreenCaptureManager.kt
        │       ├── data/
        │       │   ├── database/
        │       │   │   ├── RideEntity.kt
        │       │   │   ├── RideDao.kt
        │       │   │   └── RideDatabase.kt
        │       │   └── repository/
        │       │       └── RideRepository.kt
        │       ├── settings/
        │       │   └── SettingsDataStore.kt
        │       ├── util/
        │       │   ├── DestinationRiskStore.kt
        │       │   ├── VibrationHelper.kt
        │       │   └── CsvExporter.kt
        │       ├── overlay/
        │       │   ├── OverlayLifecycleOwner.kt
        │       │   ├── OverlayContent.kt
        │       │   └── OverlayController.kt
        │       ├── service/
        │       │   ├── RideSniperForegroundService.kt
        │       │   └── OverlayResultHolder.kt
        │       └── ui/
        │           ├── MainActivity.kt
        │           ├── RideSniperViewModel.kt
        │           ├── theme/{Color,Type,Theme}.kt
        │           └── screens/
        │               ├── HomeScreen.kt
        │               ├── HistoryScreen.kt
        │               ├── StatsScreen.kt
        │               ├── SettingsScreen.kt
        │               ├── DebugScreen.kt
        │               └── CorrectionSheet.kt
        └── test/java/com/ridesniper/app/
            ├── calculator/RideCalculatorTest.kt
            ├── ocr/OcrParserTest.kt
            └── model/StrategyPresetTest.kt
```

---

## Scoring rules implemented (exactly as specified)

- `totalMiles = pickupMiles + tripMiles`, `totalMinutes = pickupMinutes + tripMinutes` — every threshold judges the **total**, never trip-only.
- **TAKE**: `$/mile >= preferredPerMile` **and** `$/minute >= preferredPerMinute`.
- **MAYBE**: anything at or above the minimum but not meeting TAKE.
- **DECLINE**: `$/mile < minimumPerMile` **or** `$/minute < minimumPerMinute`.
- **HARD DECLINE**: `$/mile < hardDeclinePerMile` (1.25 default), overrides everything else.
- **Long ride** (`totalMiles > 10`): required `$/mile` raised to `longRidePreferredPerMile` (1.75 default).
- **Airport** (destination text contains MCI / Kansas City International Airport / Terminal A / Kansas City International): required `$/mile` raised to `airportMinimumPerMile` (1.75), preferred raised to `airportPreferredPerMile` (2.00).
- **Bad return zone** (manually tagged): +$0.25/mile on both required and preferred.
- Strategy presets (Normal 1.50/0.40, Picky 2.00/0.50 — default, Extreme 2.50/0.60) set the baseline `preferredPerMile`/`preferredPerMinute`; every number remains individually editable afterward in Settings.

All of this lives in `calculator/RideCalculator.kt`, which has zero Android
dependencies — it's a pure function you can unit test in isolation, which is
exactly what `RideCalculatorTest.kt` does.

---

## Build Checklist

**Before anything else: complete the Gradle wrapper.** This repo includes the real
`gradlew` / `gradlew.bat` launcher scripts and `gradle/wrapper/gradle-wrapper.properties`
(pinned to Gradle 8.9), but **not** the binary `gradle/wrapper/gradle-wrapper.jar` —
that file could not be generated in the sandboxed, network-isolated environment
this project was built in, and it should never be faked or hand-written. Opening
the project in Android Studio does **not** reliably regenerate a missing wrapper
jar by itself — don't rely on that. Do one of the following once, from a machine
with internet access:

- **If you have Gradle installed locally** (via SDKMAN, Homebrew, apt, or a manual install): run `gradle wrapper --gradle-version 8.9` from the project root. This regenerates `gradle/wrapper/gradle-wrapper.jar` to match the existing `gradle-wrapper.properties` — no download of a full Gradle distribution needed beyond what you already have installed.
- **If you don't have Gradle installed:** installing it (e.g. `brew install gradle`, `sdk install gradle 8.9`, or the manual install from gradle.org) and running the command above is the most reliable path. Avoid copying a `gradle-wrapper.jar` from an unrelated project or the internet at large — verify its provenance, since this file executes with build-time privileges.
- **In Android Studio:** open the project, then use the "Gradle" tool window → refresh/sync action. Recent Android Studio versions can offer to regenerate a missing wrapper using their bundled Gradle, but this is version-dependent and not guaranteed — after syncing, confirm `gradle/wrapper/gradle-wrapper.jar` actually exists on disk before trusting the build.

Once the jar is in place:

1. Install **Android Studio Ladybug (2024.2)** or newer, with the Android 16 (API 36) SDK Platform and Build-Tools installed via SDK Manager. (AGP 8.7.3, pinned here, is the first widely-available stable AGP release with reliable API 36 support — if your SDK Manager doesn't yet offer API 36, targetSdk/compileSdk will need to drop to 35 until it does.)
2. Open the project root in Android Studio and let Gradle sync, or from a terminal:
   ```
   ./gradlew --version        # confirms the wrapper works end-to-end
   ./gradlew assembleDebug    # builds app/build/outputs/apk/debug/app-debug.apk
   ./gradlew testDebugUnitTest  # runs all JVM unit tests
   ```
   On Windows use `gradlew.bat` instead of `./gradlew`.
3. Dependency versions in `app/build.gradle.kts` are pinned as a mutually-compatible set: AGP 8.7.3, Kotlin 2.0.20 (with the `org.jetbrains.kotlin.plugin.compose` Compose Compiler Gradle plugin — the old `composeOptions.kotlinCompilerExtensionVersion` approach is obsolete under Kotlin 2.x and has been removed), Compose BOM 2024.09.03, Room 2.6.1 via KSP 2.0.20-1.0.25, ML Kit text-recognition 16.0.1. If you bump the Kotlin version, bump the Compose Compiler plugin version to match — they're now versioned together.
4. If you plan to publish a signed release, generate a keystore and fill in `signingConfigs.release` in `app/build.gradle.kts` (left commented out by default — never commit a real keystore; `.gitignore` already excludes `*.keystore`/`*.jks`).
5. Generate a release APK/AAB with `./gradlew assembleRelease` or `./gradlew bundleRelease` once signing is configured.

## Install Checklist (Samsung Galaxy S23, Android 16)

1. On the S23: Settings → Developer options → enable **USB debugging** (if Developer options is hidden: Settings → About phone → tap "Build number" 7 times).
2. Connect the phone via USB, accept the RSA debugging prompt.
3. In Android Studio: select the S23 as the run target, press Run. Or build a debug APK (`./gradlew assembleDebug`) and `adb install app/build/outputs/apk/debug/app-debug.apk`.
4. First launch will show two permission cards on the Home screen — follow them in order:
   - **"Grant overlay permission"** → takes you to Settings → Apps → Ride Sniper → "Display over other apps" → toggle **on**. Samsung's One UI sometimes nests this under Settings → Apps → Special access → Display over other apps.
   - **"Start Ride Sniper"** → this both requests the notification permission (Android 13+) and the one-time screen-capture consent dialog (MediaProjection). Tap **Start now** on the system dialog.
5. You'll see a persistent "Ride Sniper active" notification and a small floating bubble.

## First Run Checklist

1. Open Uber Driver and go online.
2. When a ride offer appears on screen, tap the Ride Sniper bubble once.
3. Within ~1 second you should see a colored result card (green/yellow/red) with the recommendation and full numeric breakdown.
4. If the card doesn't appear or fields look wrong, long-press the bubble to open the full app and check the **Debug** tab — it shows the raw OCR text and exactly which regex/heuristic matched each field, which is what you'll use to refine `OcrParser.kt` for your specific phone/Uber version.
5. If OCR confidence was too low, a correction sheet pops up instead — fill in the blanks and tap CALCULATE.
6. Set today's gas price on the Home screen (top card) — it has no default internet lookup by design.
7. Check Settings and adjust MPG/wear reserve/thresholds if you don't want the Ford Escape defaults baked into this build.

## Test Checklist

- `./gradlew testDebugUnitTest` runs all JVM unit tests:
  - `RideCalculatorTest` — formulas, TAKE/MAYBE/DECLINE/HARD_DECLINE boundaries, long-ride threshold bump, airport threshold bump (including keyword-only detection), bad-return-zone adjustment, warning flags, strategy preset behavior.
  - `OcrParserTest` — clean input, comma-decimal tolerance, letter/digit misread tolerance, airport keyword detection, surge/reservation detection, missing-field reporting, label-absent positional fallback, largest-dollar-figure payout selection.
  - `StrategyPresetTest` — preset values match spec, label resolution, default settings match your vehicle spec.
- Manually verify on-device: overlay permission flow, MediaProjection consent flow (note: Android requires re-consent after the app process or the device restarts — this is an OS-level restriction on all apps, not specific to Ride Sniper), bubble drag, tap-to-analyze, long-press-to-open, vibration patterns per recommendation, CSV export via share sheet, notification action buttons (Pause/Analyze/Open/Stop).

---

## Troubleshooting

**Overlay bubble doesn't appear after granting permission.**
Force-stop and reopen the app once after granting "Display over other apps" — Android doesn't always propagate the permission to an already-running process.

**Screen capture consent dialog reappears every time I open the app.**
This is expected Android behavior, not a bug: MediaProjection consent is scoped to the capture session and is revoked whenever the app process dies or the device sleeps/restarts for long enough. There's no persistent-grant API for this permission.

**OCR misses a field consistently.**
Open Debug tab after a failed analysis, copy the raw text, and add/adjust a regex in `ocr/OcrParser.kt`. The parser is intentionally isolated from Android APIs so you can unit test new patterns quickly with `OcrParserTest.kt` before deploying.

**Notification actions (Pause/Analyze/Stop) don't respond.**
Some OEM battery optimizers (Samsung's "Put unused apps to sleep" / "Deep sleeping apps") can kill the foreground service. Go to Settings → Apps → Ride Sniper → Battery → set to "Unrestricted".

**Build fails on Compose compiler / Kotlin version mismatch.**
This project uses the Kotlin Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`), applied with the same version as the Kotlin plugin itself in both `build.gradle.kts` (root) and `app/build.gradle.kts`. If you upgrade Kotlin, upgrade both plugin declarations to the same new version — they're designed to move together as of Kotlin 2.0. There is no separate `kotlinCompilerExtensionVersion` to set anymore; that mechanism is obsolete under Kotlin 2.x and has been removed from this project's `composeOptions` block entirely.

**App crashes immediately on a specific Android version.**
File-provider paths, foreground service types, and permission flows in this project target API 29 (minSdk) through API 36 (compileSdk/targetSdk). If you lower minSdk, re-check every `Build.VERSION.SDK_INT` guard in `VibrationHelper.kt`, `OverlayController.kt`, and `MainActivity.kt` — they exist specifically to branch old/new API behavior correctly.

---

## Privacy & scope, by design

- Screenshots are decoded to an in-memory `Bitmap`, OCR'd, and recycled — never written to disk unless you flip "Auto-delete screenshots" off in Settings (which currently still keeps everything local; there is no upload path anywhere in this codebase).
- No `INTERNET` permission is declared at all — the app cannot make network calls even if some future code tried to.
- Ride Sniper reads pixels on screen; it never sends synthetic taps, key events, or Accessibility actions to Uber Driver or any other app.

---

## Maintenance pass: issues found and fixed

This section documents a full audit pass on the project, exactly as found and exactly what changed. No environment was available to run an actual compiler or Gradle build during this pass (no network access, no local JDK/Android SDK/Gradle installation) — every item below was found and fixed by manual inspection, not by a build log. Treat the "compile-check" items as carefully-reviewed but **not build-verified**; run `./gradlew assembleDebug` yourself once the wrapper jar is in place (see Build Checklist) and report anything that still fails.

1. **Junk directories from a shell brace-expansion bug.** The project's initial scaffolding ran `mkdir -p .../{data/database,data/repository,...}` under a shell that doesn't perform brace expansion (`/bin/sh`, not `/bin/bash`), which created literal directories named things like `{data/database,data/repository,...}` alongside the real ones. Deleted; verified the real package directories (`data/database`, `data/repository`, `model`, `ocr`, etc.) already existed independently with their actual files in them, so nothing was lost.

2. **Gradle wrapper was incomplete.** `gradle-wrapper.properties` existed but `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` did not. Added the standard `gradlew`/`gradlew.bat` launcher scripts (the real, unmodified Gradle-authored scripts). **Could not add `gradle-wrapper.jar`** — it's a binary and this environment has neither internet access nor a local Gradle install to generate it from. See the Build Checklist above for the exact command to complete this yourself; I chose not to fabricate a placeholder binary since that would fail silently and confusingly rather than obviously.

3. **Obsolete Compose Compiler configuration under Kotlin 2.x.** The project declared `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }`, which is the pre-Kotlin-2.0 mechanism and conflicts with/is ignored by Kotlin 2.x's Compose Compiler Gradle plugin. Removed that block; added `id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"` to the root `build.gradle.kts` plugin management block and applied `id("org.jetbrains.kotlin.plugin.compose")` (no version) in `app/build.gradle.kts`. Also removed the unused `org.jetbrains.kotlin.kapt` plugin declaration — Room uses KSP here, not kapt, so it was dead weight.

4. **AGP/Gradle version mismatch risk.** AGP 8.6.0 was declared alongside a Gradle 8.9 wrapper and `compileSdk = 36` (Android 16). Bumped AGP to 8.7.3, which requires Gradle 8.9+ (matching the wrapper) and has materially better chances of recognizing API 36 cleanly. I do not have network access to check Google's current AGP/compileSdk compatibility table as of today, so if Android Studio's SDK Manager doesn't yet offer the API 36 platform on your machine, either install it there or drop `compileSdk`/`targetSdk` to 35 as a fallback — the app code itself has no API-36-only calls, so this is a safe, reversible fallback.

5. **MediaProjection: missing callback registration (the critical bug).** `ScreenCaptureManager.start()` called `createVirtualDisplay()` without ever calling `mediaProjection.registerCallback(...)` first. On Android 14+ (API 34+), this throws `IllegalStateException` immediately — the app would crash the instant a driver granted screen-capture consent. Fixed by registering a `MediaProjection.Callback` (on a main-thread `Handler`) before creating the virtual display, on every Android version this app supports (harmless pre-14, required 14+).

6. **MediaProjection: no reaction to the OS revoking capture.** If the user tapped the system "Stop sharing" chip, or the OS otherwise ended the session, the app had no way of knowing — the next analyze tap would have silently failed against a dead `ImageReader`. The new `MediaProjection.Callback.onStop()` now tears down the virtual display/reader and calls a new `onProjectionStopped` hook, which the foreground service uses to stop itself cleanly (the user re-grants via the Home screen's "Start Ride Sniper" button, exactly like a fresh launch).

7. **MediaProjection: cleanup ordering.** The original `stop()` released the virtual display, closed the reader, and stopped the projection, but never unregistered the callback, which can leak a reference and cause a spurious callback invocation during teardown. Fixed ordering: release display → close reader → unregister callback → stop projection.

8. **MediaProjection: first-frame race.** `captureFrame()` called `acquireLatestImage()` exactly once and returned `null` if no frame was ready yet — likely on the very first analyze tap right after starting capture, since the virtual display needs a moment to render its first frame. Changed to a short bounded retry (5 attempts, 60ms apart) before giving up.

9. **Deprecated display-metrics API.** `windowManager.defaultDisplay.getRealMetrics()` is deprecated since API 30. Since this project's `minSdk` is 29, replaced with a version-gated helper: `windowManager.currentWindowMetrics.bounds` on API 30+, falling back to the deprecated call (explicitly `@Suppress`-annotated, not silently ignored) only below that.

10. **Deprecated single-argument `Intent.getParcelableExtra`.** Deprecated since API 33 with a type-unsafe warning. Replaced with `androidx.core.content.IntentCompat.getParcelableExtra(intent, key, Intent::class.java)`, which is safe across every supported API level.

11. **Foreground service `specialUse` subtype string.** The manifest's `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value was a full sentence; Google's guidance is a short, concise identifier. Shortened to `floating_ride_offer_analysis_overlay`.

12. **Naming collision risk in `MainActivity.kt`.** A `@Composable fun RideSniperApp(...)` shared its simple name with the `RideSniperApp` `Application` subclass used elsewhere in the same file (`application as RideSniperApp`). Kotlin's type/value namespaces mean this technically compiles, but it's a real footgun for anyone editing this file later and a plausible source of "why is my composable not showing" confusion. Renamed the composable to `RideSniperRoot`.

13. **Test coverage.** Confirmed and extended `OcrParserTest` — the original suite tested O/0 misreads but not l/I misreads independently. Added a dedicated test for that case, using inputs that are actually solvable by the normalization regex (a misread letter must sit immediately next to a real digit on at least one side — two adjacent misread letters with no real digit touching either one, e.g. a fully-misread "11" typed as "ll", is a real limitation of the current heuristic and is not claimed to work). All other requested coverage (calculator boundaries, airport rides, long rides, deadhead/bad-return-zone, strategy presets, payout extraction, comma decimals, positional fallback, surge, reservations, airport keywords) was already present and required no changes to keep passing, since no public API signatures changed.

14. **No functionality was removed.** Every feature from the original spec — TAKE/MAYBE/DECLINE/HARD DECLINE logic, airport detection, long-ride adjustment, deadhead/bad-return-zone logic, all ten warning flags, ML Kit OCR, the heuristic parser, MediaProjection capture, the draggable overlay, tap-to-analyze, foreground notification actions, Room history, DataStore settings, destination zone tagging, the stats dashboard, CSV export, the OCR debug screen, the manual correction sheet, and strategy presets — is unchanged in behavior; only the items above were touched, and only to fix a real defect or an outdated/deprecated pattern.
