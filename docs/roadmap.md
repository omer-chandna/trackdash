# Roadmap

Authoritative status as of 2026-10-14. The owner authorized direct incremental
commits to main and a final push. COMPLETED below means the stated software scope
is integrated and tested; physical verification is listed separately. This is a
development build, not a claim that every hardware/release acceptance gate passed.

| Feature | Status | Integration | Acceptance evidence / remaining limits |
|---|---|---|---|
| Foundation/navigation/theme/CI | COMPLETED | main | Native Compose, modes, portrait/landscape, pinned build tools and CI |
| Vehicle simulation/dashboard | COMPLETED | main | Replay/fault/lifecycle tests, explicit simulated labels |
| GPS geometry and lap/sector timing | COMPLETED | main | Finite directional crossings, interpolation, ordered sectors, invalid/best/theoretical rules; deterministic tests |
| GPS source and quality | IN_REVIEW | main | Android GPS, age/accuracy/rate/rejection reporting implemented; outdoor physical checks pending |
| Track library and sharing | COMPLETED | main | Room, v1 JSON, bounded validation, copy-on-import, emulator save/edit test |
| Map and gate editor | COMPLETED | main | MapLibre lifecycle, two-endpoint draw/redraw, direction/sector ordering, configured search |
| Offline maps | IN_REVIEW | main | Local geometry always works; licensed offline database import implemented; provider package coverage not hardware-tested |
| GPS/OBD/combined recording | COMPLETED | main | Foreground service, bounded writer, snapshots, recovery; combined background/lap/warning/export emulator test |
| Standard OBD protocol/discovery | COMPLETED | main | PID formulas/capabilities per ECU, framing and CAN VIN fixtures; no enhanced commands assumed |
| Classic Bluetooth | IN_REVIEW | main | RFCOMM, cancel/timeout/disconnect/retries, Veepeak Android setup; physical adapter validation pending |
| Polling | IN_REVIEW | main | Serialized priority intervals, stale status, latency/errors, retry backoff; real throughput and configurable per-PID rates remain follow-up |
| Vehicle profiles | COMPLETED | main | Saved user-created identities, detected VIN/capabilities and generic fallback |
| Dashboard/settings | COMPLETED | main | Per-profile gauge selection/order, numeric/bar display, size, units, keep-awake/fullscreen and shift threshold |
| Alerts | COMPLETED | main | Explicit per-profile temperature/RPM rules, hold/hysteresis, pressure/disconnect/stale/GPS loss, optional audio and persisted transitions |
| History/analysis/export | COMPLETED | main | Notes, valid best/average, sectors, bounded charts/path, exact peaks, elapsed-time comparison and paged CSV; regression tests |
| Simulation recording/fault controls | COMPLETED | main | Pace integration, jump/dropout/vehicle faults, lap/warning persistence and export |
| Enhanced manufacturer sensors | BLOCKED | protocol evidence | Exact ECU service identifiers/scaling/captures are not verified; oil-pressure switch remains unavailable in generic OBD |
| BLE compatibility | IN_REVIEW | main | Experimental vendor-documented OBDLink CX driver and scan/recovery; physical validation pending; Veepeak uses Classic |
| Release preparation | IN_PROGRESS | main | Build/tests/docs/screenshots prepared; physical/current-Android/performance gates and owner license decision pending |

## Remaining acceptance work

- Exercise a supported Classic Bluetooth adapter with a target vehicle: pairing, ECU selection,
  supported PIDs, oil-temperature availability, real rates and disconnect recovery.
- Validate any enhanced manufacturer identifiers with protocol evidence and read-only captures
  before adding a provider; commercial-app support is not sufficient evidence.
- Qualify the experimental CX BLE driver with actual hardware and pairing events.
- Test current Android permission/service timeouts, outdoor GNSS, battery/thermal
  behavior, long sessions, and licensed offline packages on target phones.
- Expand per-PID polling configuration and additional simulation trajectories/drift
  after measured source behavior. Current simulation is the demo circuit only.
- Select a project license and complete release qualification before publishing a release.
