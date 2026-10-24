# Compatibility

Android 10+ is supported by the code; automated device tests currently run on an
API 29 x86_64 emulator. Compile/target API is 37. Physical Samsung/Pixel devices
and current-Android service/permission behavior remain release checks.

| Source | Implementation | Physical validation |
|---|---|---|
| Simulated vehicle/GPS | Functional, recordable and exportable | Not applicable |
| Phone GPS provider | Permission-controlled, monotonic timestamps, quality checks | Required outdoors on target phones |
| Veepeak OBDCheck BLE | Classic RFCOMM per vendor Android instructions | Required with each target vehicle |
| Other ELM-compatible Classic | Standard read-only commands, capability discovery | Per adapter/ECU required |
| OBDLink CX BLE | Experimental documented UART driver | Required, including pairing and reconnect |
| Enhanced manufacturer identifiers | Not implemented without verified evidence | Blocked pending per-vehicle evidence |

Manufacturer/model labels never confer sensor support. Profiles for other makes
use generic advertised OBD capabilities. Unsupported values are hidden or labelled;
no nominal temperature, pressure or connection health is substituted.
