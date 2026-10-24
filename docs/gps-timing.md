# GPS timing

Android's GPS provider supplies WGS84 coordinates, accuracy, optional altitude,
speed/bearing, wall time and elapsed-realtime nanoseconds. Simulation uses the
same source contract and timing engine. Requested 5 Hz is not a guarantee that a
phone delivers that rate; Connection Status reports observed delivery intervals.

The engine uses a local metric projection with longitude wrapping, finite segment
intersection, direction checking, and monotonic timestamp interpolation. The
first accepted start/finish crossing starts timing; the next completes a lap.
Defaults: accuracy <=25 m, maximum gap 3 s, plausible movement <=100 m/s,
minimum lap 5 s and 100 m, crossing cooldown 3 s. Gates are 1–500 m and coordinates
are constrained to ±85° latitude. Up to 12 ordered sectors are supported.

Poor fixes/gaps/jumps invalidate the current lap. Timestamps must remain strictly
increasing even across a signal gap. Invalid laps remain in history and cannot
become best lap or best sector. Theoretical best combines best valid sectors.

Recording rejects fixes older than 3 s for timing and exposes accuracy, age,
observed rate and rejected fixes. A signal dropout breaks the timing segment.
Timing is not a position-accuracy guarantee: interpolation cannot remove GNSS
uncertainty. TrackDash is supplemental closed-course equipment, not certified timing.
