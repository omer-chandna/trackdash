# Roadmap

This is the authoritative status record. Unmerged work remains IN_REVIEW even
after implementation and validation. COMPLETED requires approved integration.

| Feature | Status | Branch | Dependencies | Acceptance criteria / tests | Blockers |
|---|---|---|---|---|---|
| Project foundation | IN_REVIEW | feature/project-foundation | Initial main | Debug build/launch, dark shell, navigation, source contracts; 7 JVM + 3 emulator tests pass; lint 0 errors; CI configured | Explicit integration approval; GitHub CI not yet executed |
| Vehicle simulation | NOT_STARTED | feature/simulation-engine | Approved foundation | Deterministic source; start/stop; scenario tests; visible simulation label | Foundation integration |
| Dashboard | NOT_STARTED | feature/dashboard-ui | Simulation | Live RPM/temperature/status; portrait/landscape UI tests | Prior integration |
| Alerts | NOT_STARTED | feature/alert-engine | Sources/dashboard | Configurable hysteresis/duration; unknown never healthy; tests | Threshold evidence/configuration |
| GPS source/quality | NOT_STARTED | feature/gps-location | Foundation | Offline fixes, monotonic time, age/rate/quality; dropout tests | Runtime permission/device checks |
| Gate geometry | NOT_STARTED | feature/gps-gate-crossing | Core models | Finite intersections, direction, interpolation, invalid/jump/accuracy tests | Foundation integration |
| Lap timing | NOT_STARTED | feature/lap-timing | Geometry | First crossing starts; next completes; duplicate/minimum duration/distance tests | Prior integration |
| Sector timing | NOT_STARTED | feature/sector-timing | Lap timing | Ordered gates, missing gates, valid best/theoretical times | Prior integration |
| Track library | NOT_STARTED | feature/track-library | Foundation | Room save/load and migration tests | Prior integration |
| Maps | NOT_STARTED | feature/map-integration | Tracks | MapLibre lifecycle and licensed offline packages | Map source selection |
| Gate editor | NOT_STARTED | feature/track-editor | Maps/geometry | Tap endpoints, direction, sectors, save and reuse | Prior integration |
| Track sharing | NOT_STARTED | feature/track-sharing | Tracks | Versioned JSON validation/round-trip tests | Prior integration |
| GPS sessions | NOT_STARTED | feature/gps-session | GPS/timing/tracks | Foreground recording, permission handling, recovery | Device verification |
| Lap dashboard/storage | NOT_STARTED | feature/lap-persistence | GPS sessions | History, quality, valid best lap; Room/UI tests | Prior integration |
| Classic transport | NOT_STARTED | feature/bluetooth-transport | Foundation | RFCOMM, fragmented responses, timeout/disconnect tests | Physical adapter |
| ELM protocol/parser | NOT_STARTED | feature/elm327-protocol | Transport | Initialization, echoes, invalid hex, multiple ECU fixtures | Verified captures |
| Sensor discovery | NOT_STARTED | feature/sensor-discovery | Protocol | Supported bitmap chains, validation, VIN; tests | Prior integration |
| Polling | NOT_STARTED | feature/obd-polling | Discovery | Serialized priority/adaptation, latency/stale/rate tests | Physical rate testing |
| Vehicle profiles | NOT_STARTED | feature/vehicle-profiles | Discovery | Saved vehicles; generic fallback; BMW N52 profile | Prior integration |
| Combined recording | NOT_STARTED | feature/combined-telemetry | OBD/GPS sessions | Independent timestamps, bounded writes, recovery tests | Prior integration |
| Analysis | NOT_STARTED | feature/session-analysis | Recording | Statistics, graphs, honest elapsed-time lap comparisons | Prior integration |
| CSV export | NOT_STARTED | feature/csv-export | Recording | SAF, timestamps/units/sources, escaping tests | Prior integration |
| Custom dashboards/settings | NOT_STARTED | feature/dashboard-customization | Dashboard/profiles | DataStore preferences, layouts, units, keep-awake | Prior integration |
| BMW enhanced diagnostics | NOT_STARTED | feature/bmw-n52 | Profiles/OBD | Verified identifiers/scaling/fixtures and hardware status | Protocol evidence |
| BLE drivers | NOT_STARTED | feature/ble-adapters | Transport | Verified GATT drivers, notifications, recovery tests | Adapter protocol evidence |
| Release preparation | NOT_STARTED | docs/release-readiness | Integrated features | Reproducible APK, performance, docs and hardware checklist | Owner license/release authorization |

Follow-on phases also include onboarding, connection/vehicle managers, diagnostics,
fault-injection controls, simulation recording/export, and expanded hardware tests.
