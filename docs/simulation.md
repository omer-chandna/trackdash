# Vehicle simulation

The deterministic vehicle source runs without Bluetooth, permissions, or a vehicle.
Its cold stream emits one sample batch every 100 ms while collected; cancellation
releases the loop. Each collection restarts the synthetic drive cycle while using
fresh monotonic acquisition timestamps. Scheduling delays affect delivered rates;
100 ms is a target, not a guaranteed hardware rate.

Values are synthetic demonstration data, not a calibrated BMW/N52 model or
engine-safe thresholds. All readings and batches carry SIMULATION provenance.
Normal, overheating, low oil-pressure switch, and disconnected scenarios are
supported. Disconnection emits null/unavailable measurements and unavailable
pressure rather than retaining healthy values. Gear is a simulated value only.

`VehicleSource.samples()` delivers a batch of timestamped readings with discrete
oil-pressure/gear/connection state. Real implementations must preserve each sensor's
independent timestamp; a batch does not imply synchronized physical acquisition.
LocationSource remains independent. GPS simulation, recording and export are not
part of this vehicle-source increment.

Source tests cover deterministic replay, bounded values and gear changes, fault
states, ten-hertz virtual-time delivery, collector cancellation, and restart time.

The shared `VehicleTelemetryRepository` retains only the latest batch and a count.
Start is idempotent, stop cancels collection and clears live values, and failures
are explicit. It is owned by a lifecycle scope; operations and delivery run on that
scope's dispatcher. Physical sources must move blocking work off the UI thread.
Repository tests cover cancellation, repeated start, restart, failure and completion.

The basic warning evaluator accepts explicit temperature rules and applies a
minimum duration plus hysteresis. It also reports low pressure and data loss.
Missing/stale readings reset temperature evaluation; no absence of alerts implies
healthy oil pressure. The demo UI will supply clearly labeled, editable synthetic
limits. These are not manufacturer thresholds. Audible notifications, persisted
settings, alert history and real-vehicle warning presets remain planned.
