# Architecture

Two modules keep Android dependencies out of timing/protocol logic. `core` owns
validated telemetry models, cold source contracts, geometry/lap timing, alerts,
standard PID decoding, VIN/prompt framing and CSV field serialization. `app`
owns Android sources, Room, DataStore, foreground recording, MapLibre and Compose.

`TrackDashApplication` is the manual composition root: one Room database,
application initialization/recovery job, recording StateFlow and simulation
controls. The isolated quick demo retains its ViewModel/repository lifecycle.
Library/settings screens collect Room/DataStore flows and issue suspend writes;
recording UI consumes immutable service state. No socket or location listener is
owned by a composable. Hilt is not necessary for this small composition root.

Acquisition: RFCOMM or adapter-specific BLE → serialized ElmProtocol → per-ECU
capabilities → prioritized PID polling → VehicleSample. Real Android GPS and
simulated trajectories implement the same LocationSource. Vehicle/GPS run as
independent collectors; combined mode can retain GPS if OBD exhausts retries.

RecordingService starts only from the foreground UI, declares appropriate service
types, posts a stop action, and persists independent source timestamps through a
256-row channel. One IO writer writes batches of up to 64. Producers suspend when
full; cancellation closes the queue and drains accepted samples before finalizing.
Process death leaves RECORDING rows; next application startup marks them
INTERRUPTED. No claim is made that a killed process resumes acquisition.

Room schema v1 stores tracks, profiles, sessions and typed JSON sample/event rows,
with cascading session deletion and an index on session/time. Track/settings
snapshots preserve the recording configuration. Schema export is committed.
There is no destructive migration fallback. DataStore stores per-profile layouts,
units, explicit warning settings and optional map/search endpoints.

GPS timing is independent of tiles and OBD. Valid finite crossings interpolate
monotonic time. Only valid ordered laps contribute to best/theoretical times.
Source quality and acquisition timestamps remain explicit. Generic OBD cannot
manufacture discrete oil-pressure or gear information.

Analysis scans Room pages, computes exact maxima and bounds chart/path samples to
2,000 per series; the last 200 warning events are displayed. CSV pages stream to
SAF output. Charts break across substantial measurement gaps and lap comparisons
use elapsed time, not an implied distance alignment.

MapView owns and releases its native lifecycle with its Compose destination.
Gate/trace geometry renders with a local style even when provider tiles are absent.
Search and package import are explicit user actions; no provider credentials or
public tile scraping are built into the app.
