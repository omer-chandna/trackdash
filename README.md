# TrackDash

Native Android motorsport telemetry and GPS lap timing, developed in small,
reviewable increments. Android 10 (API 29) and newer; compile/target API 37.

Version 0.2 adds a working vehicle simulator: live gauges, start/stop, fault
scenarios, and editable demo warning limits. The dark Compose app supports
portrait/landscape, navigation, and lifecycle-restored operating-mode selection.
Core Kotlin contracts keep vehicle and GPS sources independent.
**Real OBD/GPS acquisition, lap timing and session recording are not implemented yet.**

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

Launch TrackDash and choose **Try vehicle simulation**. RPM, gear, temperatures,
speed, throttle, load, voltage and oil-pressure switch state update live. Use
**Scenarios** for overheating, low pressure or OBD disconnect, and **Warning limits**
to edit the demo thresholds. Stop clears values; restarting begins normal driving.
All measurements are explicitly simulated. No car, adapter or network is needed.

The demo continues through rotation/navigation and stops when the app backgrounds.
It does not record data. Planned real operating-mode selection remains separate.
See [simulation guide](docs/simulation.md) for behavior and limitations.

## Planned workflows

| Workflow | Current availability |
|---|---|
| Vehicle simulation | Implemented: deterministic stream, live gauges, faults, editable demo alerts |
| GPS simulation | Not implemented; will exercise the real crossing/timing engine |
| Pair/connect OBD adapter | Not implemented; Bluetooth Classic will come before verified BLE drivers |
| Create a track and draw start/finish | Not implemented; planned MapLibre editor with finite directional gates |
| Start sessions and record laps | Not implemented; GPS-only, OBD-only, and combined paths planned |
| Review/export telemetry | Not implemented; local Room storage and SAF CSV export planned |
| BMW N52 diagnostics | Not implemented; generic support first; proprietary commands require evidence |

See [roadmap](docs/roadmap.md) for acceptance criteria and integration dependencies,
[architecture](docs/architecture.md), [testing](docs/testing.md), and
[known limitations](docs/known-limitations.md). The [simulation review](docs/reviews/vehicle-simulation.md)
records this increment and validation. No hardware capability is claimed.

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md). The owner has not selected
a project license yet.
