# TrackDash

Native Android motorsport telemetry and GPS lap timing. Android 10+ (API 29),
compile/target API 37. Version 0.3 integrates simulation, GPS timing, track editing,
foreground recording, standard OBD, analysis, and local export.

## Build and install

Use JDK 17 and Android SDK API 37 (`platforms/android-37.0`), build tools 36.0.0.
Set `ANDROID_HOME` or an ignored `local.properties` with `sdk.dir`.

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux use `./gradlew`. Dependencies need network on the first build.
GPS, timing, vehicle data, saved gates, recording, review and export work offline.
Online map tiles/search are optional and require your configured provider.

## Try it without a car

Choose a mode on Home, then **Set up recording**. Leave **Simulation** enabled and
press **Start recording**. GPS simulation uses the saved demo circuit; combined
mode also records synthetic vehicle readings. Change lap pace, trigger GPS loss,
a location jump, overheating, low oil pressure or disconnect while recording.
The first start/finish crossing starts the timer; the next completes a lap.
Use **Stop and save**, then **Sessions** to review laps, charts, warnings and CSV.
All synthetic data is identified as simulation in the UI and export.

**Try vehicle simulation** is a separate lightweight demo: it stops in the
background and does not record. Session recording continues with a foreground
service through navigation, rotation and normal backgrounding.

## Driving display and warnings

The landscape rally display uses large white numbers on black, compact controls,
and 12 green/yellow/red RPM lights. Configure **Shift RPM / redline** for a real
vehicle profile; no vehicle limit is assumed. Reaching that limit flashes a
full-screen **SHIFT** prompt three times, followed by a steady prompt until RPM
falls more than 200 RPM below the limit.

New engine-safety warnings flash individually, three pulses per warning. Low oil
pressure has first priority among simultaneous warnings; additional warnings join
the queue. Cleared warnings leave the queue, and active warning banners/gauge
highlights remain until the conditions clear. GPS loss and stale/disconnected data
show status warnings without triggering the engine-safety flash.

Home, Tracks, and Sessions use an icon rail in landscape and an icon-only bottom
bar in portrait. Connection Status is available through **Settings → Maps & advanced**.
Back leaves an active recording running; **Stop and save** explicitly ends it.

## Connect the owner's Veepeak OBDCheck BLE

1. Plug the adapter into the stationary vehicle and turn on ignition.
2. Pair **VEEPEAK** in Android Bluetooth settings; the vendor specifies PIN **1234**.
3. In TrackDash, choose Vehicle dashboard or Combined telemetry, open recording
   setup, turn Simulation off, and load paired Classic adapters.
4. Select VEEPEAK and a vehicle profile. For combined mode, also select a track.
5. Grant the requested Bluetooth/location permissions, then start recording.

[Veepeak's Android instructions](https://veepeak.com/pages/obdcheck-ble) use Classic
Bluetooth even though the product name includes BLE. Only read-only standard
OBD queries are sent. Sensor availability comes from the responding ECU, not the
vehicle name. Unsupported oil temperature remains unavailable; oil pressure is
never fabricated. Physical adapter and vehicle validation is still required.

An **experimental OBDLink CX BLE** option uses its documented UART service and
acknowledged writes. It is not a generic driver for every BLE adapter and has not
been hardware-tested. See [protocol evidence](docs/obd-protocol.md).

## Create a track

Open **Tracks → New track**. Navigate with the map or enter latitude/longitude.
Choose **Draw start / finish**, then tap two endpoints across the circuit.
Select an accepted direction; add ordered sector gates, redraw/reorder them and
save. Gates must be 1–500 m long. Export/import versioned JSON from the library;
imports create a copy rather than overwrite an existing track.

The default map is an offline background with gates and recorded paths. In
**Vehicles & settings**, configure an authorized HTTPS MapLibre style and optional
Nominatim-compatible search endpoint, or import a licensed MapLibre offline-region
database. Offline packages are not arbitrary MBTiles files. Timing never needs
map tiles. Imported packages may lack resources; check coverage before use.

## Settings and analysis

Create vehicle profiles, choose gauges and their order, size and bar presentation,
units, keep-awake/fullscreen behavior, and warning settings. Temperature/RPM
thresholds are user supplied; there are no assumed manufacturer safety limits. Recording
snapshots settings at start. Session notes, complete samples, warnings and track
snapshots stay locally in Room. Android backup is disabled.

Session analysis includes exact recorded peaks, sampled charts and GPS paths,
valid best/average lap, sectors, and two-lap comparisons by elapsed time. CSV is
written through Android's document picker in bounded pages with timestamps,
canonical units, source and availability status. Stop a session before export.

Use the trash action in session history or session details to permanently delete
a finished or interrupted session and its samples, laps, events, and notes. A
confirmation is required, and active recordings cannot be deleted.

## Validation and remaining work

JVM tests, Android 10 emulator tests, lint and debug builds are documented in
[testing](docs/testing.md). This is a development build, not a hardware-qualified
release. Proprietary enhanced commands require verified ECU identifiers and
captures for each supported vehicle; they are deliberately not guessed. Modern-device foreground-service,
Bluetooth, GNSS accuracy, thermal/battery and long-run tests remain release gates.
See [roadmap](docs/roadmap.md), [limitations](docs/known-limitations.md),
[architecture](docs/architecture.md), and [integration review](docs/reviews/integrated-telemetry.md).

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md). The owner has not selected
an open-source license; no release is published by this development push.
