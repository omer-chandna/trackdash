# Rally dashboard and warning manager refresh

Status: IN_REVIEW on `codex/rally-dashboard-warning-manager`, targeting `main`.
This work is awaiting approved integration.

## Resulting behavior

The driving screens use large adaptive white-on-black telemetry values, simple
outlined cells, and a compact header. The shared RPM strip illuminates 12 dots in
green/yellow/red zones derived from the selected Shift RPM. Real profiles require
an explicit limit; the standalone vehicle demo uses 7000 RPM. Reaching the limit
produces three full-screen SHIFT pulses followed by a steady prompt, with 200 RPM
reset hysteresis.

The warning manager presents one engine-safety warning at a time. Each activation
receives three 250 ms on / 250 ms off pulses. Simultaneous warnings prioritize
critical alerts and low oil pressure. New warnings join the pending queue without
restarting a current engine warning, cleared conditions are removed, and a later
recurrence can flash again. Queue and pulse-phase state survive configuration
restoration. Opening an already-active dashboard does not replay old warnings.
Engine-safety warnings take precedence over SHIFT; connectivity and data-quality
warnings retain banners without joining the safety flash queue.

Primary navigation is Home, Tracks, and Sessions, with a fixed 72dp landscape
icon rail and an icon-only portrait bottom bar. Settings provides advanced
Connection Status. Detail flows expose Back controls; returning from a live
session preserves its foreground recorder. Tracks, Settings, setup, and history
use adaptive panels; large session windows expose master/detail panes.

Session history and details provide deletion with destructive confirmation. The
DAO conditionally deletes only non-recording sessions, with the existing sample
foreign-key cascade. The old bundled vehicle preset is removed by its exact ID;
custom profiles and historical sessions remain. Room schema v1, serialized gear
fields, session data and CSV formats remain compatible while gear is absent from
the driving UI.

## Screenshots

All values below are synthetic demo data or captured warning-layer test previews.

![Landscape rally demo](../screenshots/rally-demo-landscape.png)

![Full-screen SHIFT flash](../screenshots/shift-flash.png)

![Steady SHIFT prompt](../screenshots/shift-steady.png)

## Validation

The final verification command and results are recorded in
[Testing](../testing.md). Regression coverage includes alert categories, RPM zones,
shift hysteresis, simultaneous/new/cleared/recurring warning ordering, restoration
without replay, pressure priority, guarded session deletion and cascading samples,
legacy-profile cleanup, icon navigation, Back behavior, and landscape text sizes.

Validation uses the local API 29 emulator and synthetic sources. Physical vehicle,
adapter, outdoor GNSS, current-Android foreground service, and extended thermal /
battery qualification remain the release gates documented in the roadmap.
