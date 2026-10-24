# Vehicle and adapter compatibility

TrackDash reads **standard OBD-II only**. It discovers responding ECUs and their
advertised supported PIDs; a make/model or saved profile never confers support.
Discovery is a setup aid, not a guarantee of correct readings, sufficient update
rate, or vehicle health. There are no verified physical vehicle reports yet.

## Implementation matrix

| Source / transport | Software coverage | Physical evidence / qualification |
|---|---|---|
| Simulated vehicle/GPS | Recordable synthetic samples, faults, laps and export | Automated/emulator only; not a vehicle compatibility claim |
| Phone GPS | Permissions, monotonic times, age/accuracy/rate and rejection checks | Outdoor phone/sky-view tests pending |
| Veepeak OBDCheck BLE / Classic RFCOMM | Vendor-directed Android transport; standard ELM framing/discovery/polling | Vehicle/adapter/firmware combination unverified |
| Other ELM-compatible Classic adapters | Same bounded, read-only standard protocol | Per-adapter testing required; clone behavior varies |
| OBDLink CX / device-specific BLE | Experimental documented UART protocol | Hardware/pairing/reconnect tests pending; not universal BLE |
| Generic oil temperature PID 015C | Decoded only when ECU advertises support | Availability and measured rate vary |
| Discrete low oil pressure / gear | Not supplied by generic OBD source | **Not monitored**; no substitute healthy value |
| Enhanced/proprietary sensors or vehicle writes | Out of scope | No commands guessed from model name or another app's support |

Android minimum is API 29; compile/target is API 37. Automated device coverage is
reported in [testing](testing.md). Current-Android permission/service behavior,
physical radios and long-running background use remain separate release gates.
The vendor's [Veepeak Android instructions](https://veepeak.com/pages/obdcheck-ble)
explain why this named “BLE” adapter uses Classic Bluetooth on Android. See
[protocol evidence](obd-protocol.md).

## What a verified report looks like

The public matrix grows from specific, reproducible reports, not broad brand claims:

| Report | Vehicle / ECU | Adapter / firmware / transport | Phone / Android / app commit | Sensors and rates | Result |
|---|---|---|---|---|---|
| None submitted | — | — | — | — | Hardware qualification pending |

A maintainer adds a row only after reviewing a report linked from the first column.
“Works” means the explicitly tested combination and sensors, not every trim/year or
future firmware. Use **reported**, **reproduced**, or **regression** labels; reserve
**verified** for a maintainer-reproduced physical check with evidence and date.
Keep partial support useful: a failed oil-temperature PID must not invalidate RPM
or GPS that is working independently.

## Submit a report

Use the repository's compatibility issue form or copy
[compatibility-report-template.md](compatibility-report-template.md). Include:

- Date, app version/commit and real-vs-simulation confirmation.
- Model year, manufacturer, model, engine/fuel and market only if comfortable;
  **omit VIN, registration, Bluetooth MAC, exact location and personal identity**.
- Adapter model, hardware/firmware if known, Classic vs device-specific BLE,
  phone model and Android/API version.
- Protocol and each responding ECU header; which ECU's values were used. List
  supported PID bitmaps and which displayed sensors actually returned valid data.
- Sample window length and measured valid readings per second **per sensor**;
  stale/error/no-data counts, latency if measured, and ignition/engine state.
  A requested polling interval is not a measured update rate.
- Pairing, initial connection, disconnect/reconnect, source-independent GPS and
  background/rotation behavior; distinguish untested from failed.
- Minimal anonymized parser fixtures with expected units/status. Never upload a
  whole unredacted session, VIN response or credential-bearing log.

Test while parked or have a passenger operate the display during a lawful
closed-course test. Do not provoke dangerous engine failures; use simulation/replay.
Only share evidence you have permission to publish under Apache-2.0.

## Example measurement method

After setup and warm-up, record at least 60 seconds in a documented stationary
engine/ignition state. Export locally, count valid samples of each sensor using
its acquisition timestamp, **deduplicate repeated `(sensor, acquisition timestamp)`
pairs**, and divide by the observation window. Vehicle batches include cached
readings; counting CSV rows alone overstates slower sensors' rates. Report sensor
age, gaps and timeout counts alongside average rate. Do not merge ECU responses
into a single “supported” list or imply that a high RPM sample rate makes coolant
equally fast. Inspect the export for private data before sharing summary counts.
