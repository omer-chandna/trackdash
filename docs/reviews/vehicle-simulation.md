# Vehicle simulation and live dashboard review

Integrated into local `main` under the owner's 2026-10-08 authorization for small
main-branch commits. Foundation was fast-forwarded first; no remote push occurred.

## Delivered behavior

Home now opens a functioning vehicle demo without a car, adapter, permissions, or
network. It generates timestamped RPM, gear, temperatures, speed, throttle, load,
voltage and discrete oil-pressure state through the shared vehicle source.

- Deterministic 24-second synthetic trajectory, target 10 Hz, fresh monotonic times.
- Explicit simulation provenance throughout the source and dashboard.
- Start/stop/restart with one collector and bounded latest-value storage.
- Normal, overheating, low-pressure, and OBD-disconnect scenarios.
- Disconnection clears gauge values and pressure state to unavailable.
- Explicit temperature rules with two-second hold and 3°C hysteresis in the demo.
- Editable coolant/oil demo limits with validation; no claimed manufacturer thresholds.
- Critical low-pressure and warning data-loss states from the sample data.
- Responsive numeric gauges, RPM bar, four-column landscape and two-column portrait.
- Navigation/rotation retain the demo; app backgrounding and ViewModel destruction stop it.
- Diagnostics shows the simulator's sample count separately from physical source status.

The reusable repository and alert engine remain in the Kotlin core module. UI
conversion from m/s to km/h occurs only during presentation. Vehicle sample batches
preserve individual reading timestamps so future physical sensors need not be
falsely synchronized. The activity composition root injects the simulator/clock;
the UI consumes immutable ViewModel state.

## Validation executed

| Check | Result |
|---|---|
| `:core:test` | 24 passed: model, source, repository, and alert invariants |
| `:app:testDebugUnitTest` | 5 passed: control/state, faults/recovery, limits, restart, cancellation |
| `:app:connectedDebugAndroidTest` | 7 passed on API 29 x86_64 emulator |
| `:app:assembleDebug` | Successful; version 0.2.0 APK |
| `:app:lintDebug` | Successful; latest report says no issues found |
| App installation/launch | Successful on the emulator |
| Screenshot inspection | Portrait and compact landscape checked |
| `git diff --check` | Passed |

The initial test-harness run stalled because the endless ViewModel stream was
still active when runTest tried to drain its scheduler. Tests now clear the
ViewModelStore in a finally block inside runTest, so cleanup also runs after an
assertion failure. The corrected tests passed; no result from that aborted run
is counted as a pass.

[Portrait screenshot](../screenshots/simulation-portrait.png) ·
[Landscape screenshot](../screenshots/simulation-landscape.png).
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Remaining work

No physical adapter/vehicle/GNSS capability has been tested or implemented. The
simulator is not a BMW physics model. GPS simulation and real gate/lap/sector
algorithms are next, followed by map editing and recording. DataStore settings,
Room storage, audible alerts, alert history, dashboard customization, background
recording and CSV export remain future increments. Limits are session-local.

The roadmap distinguishes completed basic simulation warnings from the full
vehicle alert system. GitHub CI is configured but has not run remotely, and no
release or PR has been published. Local main is the approved integration point
for the next deterministic GPS timing increment.
