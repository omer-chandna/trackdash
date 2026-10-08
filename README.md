# TrackDash

Native Android motorsport telemetry and GPS lap timing, developed in small,
reviewable increments. Android 10 (API 29) and newer; compile/target API 37.

The foundation provides a dark Compose shell, Home/Tracks/Sessions/Diagnostics
navigation, portrait/landscape layouts, and lifecycle-restored operating-mode
selection. Core Kotlin contracts keep vehicle and GPS sources independent.
**This build does not yet acquire telemetry, simulate driving, time laps, or record sessions.**

## Build and install

Use JDK 17 and an Android SDK with API 37 (`platforms/android-37.0`) and build tools
36.0.0. Open the repository in an AGP 9.4-compatible Android Studio, or set
`ANDROID_HOME` to the SDK directory. Alternatively create ignored `local.properties`
with `sdk.dir` (escape the drive colon and backslashes on Windows).

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux use `./gradlew`. The wrapper verifies the Gradle distribution
checksum. First-time dependency downloads need network access; the foundation
app itself works offline and requests no network, GPS, or Bluetooth permissions.

Launch TrackDash, select a mode on Home, and navigate through the four sections.
Selection survives navigation and activity recreation. It is preparation only;
there is deliberately no session-start control before acquisition exists.

## Planned workflows

| Workflow | Current availability |
|---|---|
| Simulation | Source contracts only; deterministic vehicle/GPS simulation is next |
| Pair/connect OBD adapter | Not implemented; Bluetooth Classic will come before verified BLE drivers |
| Create a track and draw start/finish | Not implemented; planned MapLibre editor with finite directional gates |
| Start sessions and record laps | Not implemented; GPS-only, OBD-only, and combined paths planned |
| Review/export telemetry | Not implemented; local Room storage and SAF CSV export planned |
| BMW N52 diagnostics | Not implemented; generic support first; proprietary commands require evidence |

See [roadmap](docs/roadmap.md) for acceptance criteria and integration dependencies,
[architecture](docs/architecture.md), [testing](docs/testing.md), and
[known limitations](docs/known-limitations.md). The [foundation review](docs/reviews/project-foundation.md)
records the delivered increment and validation. No hardware capability is claimed.

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md). The owner has not selected
a project license yet.
