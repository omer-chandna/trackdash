# Local session replay

Open a stopped/saved session in Sessions, then choose **Replay session**. The
screen is prominently marked **REPLAY · SAVED DATA, NOT LIVE**. It does not start
a foreground recording, connect to an adapter, modify the session or share data.
Back and Stop replay cancel playback. Replay from start begins again at the first
readable vehicle/GPS/alert event. Choose 1×, 2× or 4× before starting playback.

The reader uses the existing version-1 session payloads (`VehicleSample`,
`GpsFix`, and string alert/clear events). No database migration or CSV change is
needed. It pages at most 256 rows at a time, retains original acquisition times,
and reproduces insertion/delivery order across independent GPS and OBD sources.
Late deliveries cannot move the playback clock backwards. Scaled waiting gaps
are capped at two seconds so a long dropout is inspectable without a long wait;
this is not a wall-clock-duration reconstruction.

Saved dashboard settings are used if readable. The last 20 recorded alert,
unconfirmed, confirmed and cleared transitions appear as a chronological event
log, not inferred live alarms or full-screen safety flashes. SHIFT state is
derived from recorded RPM and the saved configured limit. Missing/stale values
remain explicit rather than being interpolated. Unsupported metadata is skipped;
malformed telemetry payloads up to a 65,536-character limit are counted and skipped. Larger
payloads are rejected without decoding. Invalid/non-advancing page order fails
cleanly instead of looping. No sample values or coordinates are fabricated.

This provides a reproducible first troubleshooting step without requiring a car.
Replays are private local recordings and can contain GPS locations and timing
information. Do not attach a database or CSV to a public issue without reviewing
and redacting it. Use synthetic protocol fixtures for public bug reports where
possible. The optional outgoing sensor-sharing format deliberately excludes GPS
coordinates and does not upload replay data; see [telemetry sharing](telemetry-sharing.md).

Automated tests verify deterministic paging, timestamp retention, interleaved
delivery order, speed/gap bounds, malformed payload handling and cancellation.
Real adapter/vehicle and outdoor GNSS validation remains separate.
