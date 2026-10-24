# Compatibility report

Copy into a compatibility issue. This is a template, **not a verified vehicle row**.
Use “not tested” rather than guessing; remove VINs, MAC addresses, locations,
names/notes, tokens, and complete unredacted exports before posting.

## Setup

- Test date:
- App version and commit:
- Real hardware (not simulation):
- Vehicle year/model/engine/fuel/market (optional; no VIN/plate):
- Adapter model/hardware/firmware:
- Transport: Classic RFCOMM / documented device-specific BLE:
- Phone model and Android/API:
- Protocol reported by adapter:
- Responding ECU headers and selected ECU:
- Ignition / stationary engine state:
- Recording mode and observation duration:

## Sensors

| ECU | Standard PID / sensor | Advertised support | Valid readings / window | Measured Hz | Stale/errors/no data | Notes |
|---|---|---|---|---|---|---|
| | | | | | | |

- Capability bitmap queries that completed (sanitized):
- No-data/malformed queries and whether another sensor continued:
- Unavailable protections correctly shown as not monitored:

## Recovery and lifecycle

- Initial pairing / discovery:
- Disconnect / reconnect:
- GPS-only or combined when one source fails:
- Rotation, navigation, screen off/background:
- Stop/save/replay/export:
- Longest session and approximate storage use:
- Anything not tested:

## Evidence and permission

- Reproduction steps and expected/actual behavior:
- Minimal anonymized protocol fixture and expected decoded units/status:
- Local measurements/sanitized summary, not a full private export:
- I have removed identifying data and have permission to publish this report
  and any attached fixtures under the TrackDash Apache-2.0 license: yes / no
