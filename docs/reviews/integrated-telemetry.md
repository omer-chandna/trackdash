# Integrated telemetry review

## Purpose

Turn the original vehicle simulator into a working offline telemetry application:
GPS/sector timing, editable saved tracks, real source adapters, foreground session
recording, persistent configuration, review, charts and exports.

## Decisions

- Shared real/simulated source contracts feed the same timing and recording paths.
- Local Room v1 schema and bounded queued writes; no background recording in an
  Activity. Interrupted sessions are retained and labelled on next startup.
- Standard advertised OBD data comes first. The owner's Veepeak uses documented
  Android Classic transport. CX BLE is explicitly experimental. No guessed manufacturer
  diagnostic identifiers or fabricated pressure values.
- Source/time/status metadata survives CSV export. Charts are bounded overviews;
  exact peaks are computed while streaming the full session.
- Map tiles/search are configurable dependencies; saved gate geometry is usable offline.

## Validation

On 2026-10-14, all 51 JVM tests and 12 API 29 emulator tests passed. Lint reported no issues and the debug APK assembled successfully. The emulator suite covers Room paging/recovery,
background combined recording, valid simulated lap and warning persistence, CSV,
long-session GPS analysis bounds, track editing, navigation and quick-demo faults.
The final suite also checks compact landscape recording gauges. Debug APK and
Android lint are built from the integrated source. See testing.md for final counts.

## Remaining risks

Physical adapter/vehicle, BLE pairing, current Android service behavior and outdoor
GNSS/performance checks remain. Enhanced manufacturer commands are blocked by missing
verified protocol evidence. Offline packages/provider credentials were not supplied.
The owner has not selected a project license. No public release is created.

## Artifacts

- app/build/outputs/apk/debug/app-debug.apk (local development build)
- docs/screenshots/recording-portrait.png and recording-landscape.png
- Versioned Room schema in app/schemas
- docs/roadmap.md for explicit remaining acceptance work
