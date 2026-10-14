# BMW N52 evidence register

Initial profile: 2009 BMW 128i E82, N52. This is saved vehicle identity, not proof
of which ECU variant or enhanced measurements are available. The owner's adapter
is Veepeak OBDCheck BLE using its documented Android Classic connection.

Standard PID 015C is attempted only when the ECU advertises support. If unavailable,
TrackDash does not substitute coolant temperature or a synthetic oil value.
Oil-pressure status is OK/LOW/UNKNOWN/UNAVAILABLE, never fabricated PSI/bar. The
standard OBD provider always reports this switch as UNAVAILABLE.

Enhanced BMW oil temperature and oil-pressure access remain BLOCKED: no verified
ECU identifier, request/response fixture, scaling and adapter requirements for
this exact vehicle have been established. Commercial-app compatibility in the
[Veepeak support guide](https://veepeak.com/pages/obdcheck-ble) does not publish
those diagnostic identifiers and is not evidence for inventing them.

Before enabling an enhanced provider, add authoritative protocol evidence or
owner-authorized diagnostic captures with ECU identity, service/identifier,
response length/endianness/scaling, status semantics, negative-response behavior,
read-only command review, parser fixtures, and separate physical validation.
Keep those providers opt-in and preserve the generic fallback. Proprietary code,
licensed diagnostic databases and private captures must not be copied into Git.
