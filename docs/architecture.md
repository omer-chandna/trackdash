# Architecture

TrackDash is an offline-first native Android application for vehicle telemetry,
GPS timing, and combined recording. Minimum Android version: 10 (API 29).

Start with two modules: `app` owns Android lifecycle, composition and UI;
`core` owns platform-independent models and source contracts. Feature packages
will grow inside these boundaries before further Gradle modules are justified.

Dependency direction: Compose screens → ViewModel → injected source contracts
→ implementations. Core never imports Android or UI types. Source streams use
coroutines/Flow. UI consumes immutable StateFlow through lifecycle-aware collection.
The application composition root supplies dependencies by constructor injection;
introduce Hilt when services/repositories justify the additional code generation.

The activity owns a SimulationViewModel that injects a monotonic clock into the
simulated VehicleSource. A shared VehicleTelemetryRepository keeps one collector
and one latest sample, with explicit stopped/running/error states. The dashboard
collects UI StateFlow with lifecycle awareness. Demo fault controls stay in the
simulation layer; ordinary gauges consume the same strongly typed sample model
that physical sources will supply. The basic alert engine uses explicit rules,
hold times and hysteresis without real-vehicle threshold defaults.

Vehicle and location sources remain independent. Both real and simulated sources
will implement the same contracts. No source invents a missing value. Sensor
readings carry status, source, canonical units, and monotonic acquisition time.
Wall-clock time is reserved for human-readable session metadata.

Future boundaries:

- OBD: transport → serialized ELM protocol → parser → capability discovery → polling.
- Vehicles: generic capabilities with verified manufacturer providers; BMW is a profile.
- GPS: source → quality filtering → local metric geometry → lap/sector state machine.
- Tracks: Room repository, MapLibre viewer/editor, versioned JSON import/export.
- Telemetry: bounded recording, Room storage, alerts, analysis, Storage Access Framework export.
- Simulation: deterministic vehicle/GPS trajectories through production processing paths.

Map tiles, Bluetooth connectivity, and vehicle profiles must never be prerequisites
for GPS timing. UI cannot access sockets, database handles, or location listeners.
Room, DataStore, MapLibre, and hardware libraries are added alongside their first
functional feature rather than as unused foundation dependencies.
