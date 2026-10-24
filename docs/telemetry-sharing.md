# Optional telemetry sharing

TrackDash remains an offline, local-first recorder. Sharing is an explicitly
enabled **outgoing HTTPS POST**, not a server on the phone, a cloud account,
an automatic upload of recordings, or a requirement for recording.

## Set up a receiver

1. Run an HTTPS receiver you control, with a certificate trusted by Android.
   TrackDash does not install certificates, disable TLS verification or support
   cleartext HTTP. A reverse proxy can provide TLS for a self-hosted service.
2. Accept `POST` with `Content-Type: application/json; charset=utf-8` at a fixed
   URL, and respond with a `2xx` status promptly. Redirects are treated as errors.
   Keep request-body limits small (for example, 16 KB), validate `schema`,
   `version`, sensor IDs, units and numeric bounds, and reject unknown versions.
3. Protect the endpoint with authentication and rate limits. Configure an
   optional complete Authorization header, such as `Bearer <receiver-token>`.
   Credentials must not be placed in URL query parameters, fragments or user-info.
4. In Settings, enable Optional telemetry sharing and save the receiver URL and
   optional Authorization header. Changes apply to **the next recording**.
   Leave sharing off when using an untrusted receiver.

The receiver is a separate deployment choice, not bundled infrastructure or a
guaranteed delivery service. It should handle concurrent clients without
assuming that `elapsedMillis` uniquely identifies a vehicle or session. Configure
separate authorized endpoint paths/tokens if isolation is required.

## Version 1 payload

```json
{
  "schema": "dev.trackdash.telemetry",
  "version": 1,
  "elapsedMillis": 12000,
  "source": "OBD",
  "connected": true,
  "sensors": [
    {
      "sensor": "ENGINE_RPM",
      "unit": "RPM",
      "value": 3400.0,
      "status": "AVAILABLE",
      "ageMillis": 50
    }
  ]
}
```

`source` is `OBD` or `SIMULATION`; simulation is never presented as real vehicle
data. `elapsedMillis` starts at zero for each recording's first offered vehicle
sample. Acquisition timestamps are not rewritten in the saved recording.
`ageMillis` is the sensor's age at the delivered vehicle batch, not receiver time.
Sensors retain canonical units: RPM, Celsius, meters/second, percent, volts,
degrees and grams/second. Only `AVAILABLE` readings carry a numeric value;
stale/unavailable/error/unsupported states must never be interpreted as healthy.
The sender is a vehicle-sensor stream: GPS-only recordings send no sensor packets.

There is deliberately no VIN, device/vehicle/session identifier, gear, wall-clock
timestamp, GPS position, path, notes, saved alert messages or authentication
material in the JSON. The Authorization header travels only to the configured
HTTPS endpoint. This is data minimization, **not anonymity**: a receiver still
sees the sender's network address, connection times, authorization identity, and
performance patterns. Do not publish a receiver token or private endpoint in
issues or logs.

The optional Authorization value is AES-GCM encrypted at rest with a
non-exportable Android Keystore key in app-private storage; Android backups are
disabled by application policy. It must exist briefly in app memory and in the
HTTPS request header. It is not written to telemetry, saved sessions, exports or
diagnostic strings. Removing the field and saving deletes the stored ciphertext.

## Failure behavior and limits

- At most one offered sample per second by default, including simulated sessions.
- Eight queued messages; a full queue drops new messages rather than delaying
  acquisition or local persistence. Sampling cadence itself is not counted as
  queue loss.
- Two-second connect/read timeouts, a five-second coroutine request deadline,
  finite request payloads, no response-body parsing, no redirects or automatic
  retries. Platform socket cancellation is ultimately bounded by socket timeouts.
- `sent`, `dropped` and `errors` counters are recording-scoped and available to
  the live status UI. They contain no endpoint, token or server response text.
- Stopping a recording cancels the sharing worker and discards unsent messages.
  Nothing is queued to disk, resent later, or transmitted by session replay.
- Sharing failures are isolated from GPS, OBD, warning evaluation and the local
  sample writer. Local recording remains authoritative.

Automated tests cover payload privacy/versioning, canonical values and stale
states, cadence, full queues, errors, timeouts, cancellation, redirect rejection,
HTTP settings and encrypted credential storage. Real receivers, TLS/proxies,
mobile-network transitions and physical phone qualification remain deployment
checks; unit tests are not a security audit of a chosen server.
