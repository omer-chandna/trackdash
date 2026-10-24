# Testing

Use JDK 17, the committed wrapper and Android SDK 37. First dependency resolution
requires network. Local checks are not substitutes for physical validation.

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Linux/macOS use `./gradlew`. GitHub Actions builds/tests/lints and runs an API 29
emulator job on main pushes and PRs. Remote CI status must be checked separately.

## Integrated coverage

The JVM suite contains 61 tests (46 core, 15 app): telemetry validation, replay/fault/lifecycle
behavior, repository cancellation, alert hysteresis/holds/missing values, finite
geometry, monotonic timestamp/gap handling, lap/sector validity, track JSON,
standard PID formulas and discovery, CAN VIN sequence/length, bounded fragmented
responses, read-only command policy, CSV escaping and spreadsheet text handling.

Android tests run on the API 29 x86_64 Pixel 2 emulator with WHPX. Coverage includes:

- Room track/session/sample paging, recovery, guarded deletion of non-recording
  sessions, sample cascades, and legacy preset cleanup preserving custom vehicles.
- Combined recording in background, valid simulated lap and low-pressure warning,
  drained writes on stop, and exported source/timestamp metadata.
- Long GPS-only analysis: exact maxima and bounded chart/path storage.
- Track editing/reuse, navigation/recreation, simulation controls and warning edits.
- Active recording landscape layout and selecting modes by their full row.
- Three-pulse engine warnings presented individually, queue order and priority,
  saved-state restoration without replay, SHIFT pulses/steady prompt/hysteresis,
  and non-flashing connectivity/data-quality messages.
- Icon-only landscape/portrait navigation, advanced Connection Status, detail Back
  controls, confirmation/cancellation for session deletion, and continuing active recording after Back.

## Final rally-refresh verification (2026-10-24)

On `codex/rally-dashboard-warning-manager`, the complete command
`:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`
passed using the pinned local JDK/SDK, Gradle wrapper, and the API 29 x86_64 emulator.
All 61 JVM tests and 18 Android tests passed, with no failures or skipped Android
tests. Lint reported zero errors and one Compose modifier-parameter style warning
in `StatusChip`. The debug APK assembled successfully, and `git diff --check` passed.

Landscape screenshots were inspected for readable values, complete gauge rows,
and the full-screen/steady SHIFT treatments. See the
[refresh review](reviews/rally-dashboard-refresh.md) for captured previews and scope.
Remote PR CI status is independent of these local results.

## Toolchain on this workstation

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$PWD/.tools/gradle-user"
$env:ANDROID_HOME = "$PWD/.tools/android-sdk"
$env:ANDROID_USER_HOME = "$PWD/.tools/android-user"
$env:ANDROID_AVD_HOME = "$PWD/.tools/avd"
```

All tool downloads, SDK, AVDs and build artifacts are ignored. On Windows use
`--no-watch-fs`; if incremental resource/lint output is stale, clean app once.
Reports: core/build/reports/tests/test, app/build/reports/tests/testDebugUnitTest,
app/build/reports/androidTests/connected, app/build/reports/lint-results-debug.html.

## Release gates requiring hardware

Current-Android permission denial/revocation and service timeout behavior; Veepeak
pairing/disconnect/retry and per-ECU support; CX GATT pairing/fragmentation/recovery;
outdoor GNSS quality/crossings; prolonged offline recording, low storage,
battery/thermal behavior and provider-package coverage on target phones.
