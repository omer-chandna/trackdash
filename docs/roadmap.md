# Roadmap

Authoritative status as of 2026-10-25. COMPLETED below describes software already
integrated on main, including PRs #1, #2 and #3. Non-recording live view remains
IN_REVIEW on `codex/live-performance-view`. Physical verification is listed
separately. This is a development build, not a claim that hardware/release gates passed.

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
| Enhanced manufacturer sensors / repair operations | OUT_OF_SCOPE | owner scope | Standard read-only OBD-II only; oil-pressure switch remains unavailable in generic OBD |
| BLE compatibility | IN_REVIEW | main | Experimental vendor-documented OBDLink CX driver and scan/recovery; physical validation pending; Veepeak uses Classic |
| Release preparation | IN_PROGRESS | main + review | Apache-2.0 approved; attribution/software qualification in review; physical/current-Android/performance and signing gates remain |

## Rally refresh integrated

Merged through PR #1. Review notes and screenshots:
[rally dashboard refresh](reviews/rally-dashboard-refresh.md).

| Feature | Status | Acceptance evidence / remaining limits |
|---|---|---|
| Rally display and manual-car focus | COMPLETED | Large adaptive white-on-black readings, 12 RPM lights, explicit configured redline, SHIFT pulses/hysteresis; no gear UI |
| Sequential engine warnings | COMPLETED | Three-pulse cycles per activation, pressure priority, queue clear/recurrence, saved rotation state, connectivity exclusion, safety-before-SHIFT tests |
| Landscape navigation and detail flows | COMPLETED | Fixed icon rail, portrait bottom navigation, advanced Connection Status, visible/system Back and continuing foreground recording |
| Session deletion and neutral vehicle profiles | COMPLETED | Conditional DAO deletion, confirmation/cascade tests, active-session protection, exact legacy preset cleanup preserving user profiles |

## Monitoring and open-source milestone

Functional review: [PR #2](https://github.com/omer-chandna/trackdash/pull/2),
`codex/monitoring-reliability`. Open-source packaging is reviewed separately on
`codex/open-source-foundation`. Both PRs are merged on main. Nothing below claims
physical hardware or production-release qualification.

| Feature | Status | Acceptance evidence / remaining limits |
|---|---|---|
| Monitoring reliability | COMPLETED | Independent source recovery, stale-value watchdog, unresolved warnings, validated limits, storage-failure and Back regressions |
| Standard OBD-II setup | COMPLETED | Per-ECU advertised/actual/unknown states, readiness, guarded VIN identity and explicit compatibility export; real hardware pending |
| Saved-session replay | COMPLETED | Read-only bounded playback, preserved timestamps, malformed/gapped samples, Back cancellation; no recording/network |
| Optional HTTPS sharing | COMPLETED | Default off, privacy allowlist, encrypted authorization, bounded non-blocking queue and health counters; real receiver qualification pending |
| Open-source foundation | COMPLETED | Apache-2.0, 97-coordinate attribution inventory, offline license viewer, privacy/security/contribution guidance and compatibility/release gates |
| Non-recording live view | IN_REVIEW | Vehicle-only foreground dashboard; no session/writer/GPS/sharing; warnings, recovery, explicit stop and Back; `codex/live-performance-view` |

## Remaining acceptance work

- Exercise a supported Classic Bluetooth adapter with a target vehicle: pairing, ECU selection,
  supported PIDs, oil-temperature availability, real rates and disconnect recovery.
- Qualify the experimental CX BLE driver with actual hardware and pairing events.
- Test current Android permission/service timeouts, outdoor GNSS, battery/thermal
  behavior, long sessions, and licensed offline packages on target phones.
- Expand per-PID polling configuration and additional simulation trajectories/drift
  after measured source behavior. Current simulation is the demo circuit only.
- Complete the release checklist, owner-controlled signing and physical qualification
  before publishing a release. The license decision is Apache-2.0.
