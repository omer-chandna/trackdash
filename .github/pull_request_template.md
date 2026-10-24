## Purpose

What changes, why, and which issue does this address?

## Boundaries

- [ ] Standard read-only OBD-II only; no arbitrary vehicle commands or assumed manufacturer limits.
- [ ] Missing/stale/unknown data cannot appear healthy. Simulation and replay remain clearly labelled.
- [ ] Storage, source recovery, and optional network work remain bounded and independently cancellable.
- [ ] No credentials, identifiers, private telemetry, or unrelated files are included.

## Verification actually performed

List exact tests/builds, commit/device/adapter, and results. Separate automated checks from physical testing. Do not imply that an emulator verifies hardware compatibility.

## Compatibility and release impact

Describe saved-data/export/API changes, privacy impact, dependency attribution changes, and remaining limitations. Update docs and the roadmap. Hardware and signing/release gates remain explicit.
