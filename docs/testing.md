# Testing

Use JDK 17, the committed Gradle wrapper, and Android SDK 37. First-time resolution
requires network access. Do not substitute test results for physical verification.

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# With an emulator/device already booted:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Linux/macOS: replace `.\gradlew.bat` with `./gradlew`. The GitHub Actions workflow
runs build checks and a separate Android 10/API 29 emulator job on PRs, main pushes,
or manual dispatch. Workflow execution on GitHub is not verified until published.

## Foundation coverage

- Seven JVM tests: operating-mode source independence, unavailable values,
  finite measurements, unsupported states, stale timestamps, invalid GPS fixes,
  and separation of wall-clock from monotonic acquisition time.
- Three Compose instrumented tests: all destinations and back navigation;
  selected mode after navigation/recreation; landscape navigation and selection.
- Debug APK and test APK compilation; Android lint.

All ten tests passed locally on 2026-10-08, with the device tests on an Android 10
x86_64 Pixel 2 emulator using WHPX. Emulator launch was verified. Target API 37
runtime behavior and physical devices have not been tested.

Reports: `core/build/reports/tests/test/`, `app/build/reports/androidTests/connected/`,
and `app/build/reports/lint-results-debug.html`. Build artifacts are ignored by Git.

## Local toolchain

This workstation initially had no Java/Android tools on PATH. A checksum-verified
Temurin 17 JDK, SDK, and Gradle were installed under ignored `.tools/`. For reuse:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$PWD/.tools/gradle-user"
$env:ANDROID_HOME = "$PWD/.tools/android-sdk"
$env:ANDROID_USER_HOME = "$PWD/.tools/android-user"
$env:ANDROID_AVD_HOME = "$PWD/.tools/avd"
```

The current Android CLI uses slash-separated SDK package names, for example
`android --no-metrics --sdk=<path> sdk install platforms/android-37.0 build-tools/36.0.0`.
Legacy sdkmanager syntax may use semicolons. The project does not require these
local paths; use Android Studio or a normal SDK installation on other machines.

If Windows resource moves leave stale incremental resource/lint output, run
`gradlew.bat :app:clean :app:lintDebug :app:assembleDebug --no-watch-fs`.

## Future hardware checklist

Verify permission denial/revocation, Bluetooth fragmentation/disconnect/reconnect,
per-ECU capabilities, real update rates, GNSS accuracy/dropouts, long sessions,
screen rotation/backgrounding, storage recovery, export and battery/thermal behavior.
No physical OBD, BMW, BLE, GNSS or recording tests have been performed.
