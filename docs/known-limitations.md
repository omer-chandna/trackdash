# Known limitations

- No physical OBD/GNSS hardware has been tested here. API 29 emulator validation
  does not prove target API 37 foreground-service, radio or permission behavior.
- The owner's Veepeak OBDCheck BLE is configured through Classic Bluetooth as its
  vendor directs for Android. Actual adapter/ECU compatibility and rates need a car test.
- Proprietary oil temperature/pressure identifiers are unverified and absent.
  Generic 015C requires advertised support; gear and oil-pressure switch remain
  unavailable. Never interpret a missing switch reading as healthy oil pressure.
- The OBDLink CX BLE driver is experimental and uses a documented device-specific
  service; it is not a universal BLE adapter driver. VIN decoding currently supports
  headered CAN ISO-TP; other VIN framing is reported unavailable.
- The default ECU is the first sorted response. Polling is serialized with fixed
  per-PID target intervals and retry backoff; user-configurable PID rates and measured
  adapter throughput are not yet qualified.
- Real GPS sampling rate/accuracy depends on the phone and sky view. Timing gates
  use a local metric model limited to ±85° latitude. Maps never improve GNSS accuracy.
- Simulation has one circular circuit, adjustable pace and explicit faults. It is
  not a calibrated vehicle model and does not yet simulate arbitrary saved tracks/drift.
- Offline packages must be licensed MapLibre offline-region databases, not generic
  MBTiles. Packages/providers were not externally downloaded or field-tested.
  Online map/search providers are user configured; access/coverage can fail.
- Chart/path overviews are decimated to bounded memory; CSV retains stored samples.
  Charts break across gaps; comparisons align elapsed time, not traveled distance.
- Foreground recording survives normal activity lifecycle, not force-stop/process
  death. Recovery marks interrupted sessions; uncommitted samples can be lost.
  Simulation foreground service uses Android's data-sync type and obeys OS timeouts.
- Warning limits are user supplied, not validated vehicle guidance. Settings are
  snapshotted at session start. Audio depends on device notification volume.
- Native UI flows currently use application composition and lifecycle-aware flows;
  broader ViewModel/repository extraction remains a maintainability improvement.
- Production signing, store distribution, owner license selection, long-run
  battery/thermal tests and an open-source release are not part of this development push.
