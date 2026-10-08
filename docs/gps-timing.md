# GPS timing design constraints

Foundation includes validated WGS84 fixes with independent wall-clock and monotonic
timestamps. Acquisition and timing algorithms are not implemented yet.

Planned engine: filter fix accuracy/age/order/jumps, transform movement and gates
to local metric coordinates, intersect finite segments, validate direction, and
interpolate monotonic crossing time. First accepted start/finish crossing starts
a lap; the next completes it. Enforce cooldown, minimum duration/distance and
ordered sectors. Large GPS gaps must invalidate questionable timing rather than
create falsely precise crossings. Keep validity and quality metadata.

Phone GNSS delivery rates vary. Interpolation cannot remove positional uncertainty.
TrackDash is supplemental equipment for closed-course use, not certified timing.
