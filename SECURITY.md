# Security policy

TrackDash is development software. There is no promised support window or response
SLA; security fixes target the latest `main`. A public source repository is not a
claim of security certification or hardware safety.

## Report a vulnerability privately

Use the repository's [private vulnerability report](https://github.com/omer-chandna/trackdash/security/advisories/new)
if GitHub offers it. If private reporting is unavailable, open a public issue
with **only** “Please enable private vulnerability reporting” and no exploit,
secrets, personal data, or vulnerable endpoint details. Wait for a maintainer to
provide a private channel. Do not post sensitive evidence publicly.

Include affected commit/version, Android version, the smallest safe reproduction,
impact and any suggested fix. Use synthetic telemetry and redact VINs, adapter
addresses, coordinates, notes, endpoint credentials and access tokens. Do not
probe other people's receivers, vehicles, devices or accounts.

Relevant reports include malformed OBD/import/replay input, unintended vehicle
writes, permission/lifecycle failures, exposed private exports, unsafe endpoint
handling, secret storage, or a sharing failure that interferes with local recording.

## Threat boundaries

- Bluetooth adapters, ECUs, imported files and configured map/HTTPS services are
  external inputs, not trusted code. Discovery means advertised support, not
  verified sensor accuracy.
- OBD traffic is read-only. No fault clearing, coding, actuation or repair commands
  are supported. Do not add arbitrary command execution as a troubleshooting shortcut.
- Local app-private storage and Android Keystore depend on the phone/OS security.
  A compromised/rooted device or an exported file is outside that boundary.
- Optional sharing is off by default. Only trust a receiver you control; HTTPS
  protects transport, not the receiver operator. See [Privacy](PRIVACY.md).

A lost/stale reading must never be presented as a healthy condition. TrackDash
cannot guarantee warnings arrive before damage or replace factory instruments.
For distribution, complete [release qualification](docs/release-checklist.md),
keep signing material out of Git, and never test dangerous failures on a real car.
