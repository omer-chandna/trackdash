# BMW N52 evidence register

No enhanced commands have been implemented or verified. Attempt supported and
validated standard oil-temperature PID 015C before any proprietary fallback.

Oil-pressure monitoring is modeled as OK, LOW, UNKNOWN, or UNAVAILABLE, never a
fabricated numeric PSI/bar value. Generic OBD availability must not be assumed.

Before implementing a provider, record ECU identification, protocol and diagnostic
identifier, byte-level fixture, scaling, evidence source/license, adapter requirements,
and separate physical validation results. Unknown pressure is never healthy.
