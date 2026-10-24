# Release qualification

This checklist is a **gate**, not a claim of completed validation. Maintainers
record the commit, device/adapter, date, evidence and result for each applicable
item. Mark untested as pending. Debug builds and emulator-only passes must not be
marketed as hardware-qualified or an engine-protection device.

## Source, tests, and attribution

- [ ] Review the diff, security/privacy impact and compatibility notes; all required
  PRs are approved and integrated. No secrets, private exports or captures in Git.
- [ ] Pin toolchain/dependencies, verify the Gradle wrapper checksum, and run a
  clean build with the documented JDK/SDK.
- [ ] Run `:core:test :app:testDebugUnitTest :app:verifyOpenSourceInventory
  :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`. Preserve reports;
  distinguish tests actually run from known future work.
- [ ] Inspect APK `assets/opensource/`, metadata inventory and upstream native
  license/NOTICE changes. Include LICENSE, NOTICE and third-party attributions
  with distributed binaries. Check map/data/provider obligations separately.
- [ ] Inspect landscape 16:9 and compact-window screens, portrait fallback,
  accessibility semantics, large text and system/visible Back.
- [ ] Verify three-pulse queued safety warnings, recurrence, sensor-loss wording,
  SHIFT hysteresis, invalid settings, and no gear or fabricated oil-pressure UI.

## Physical phone, adapter, and vehicle

- [ ] Test a real Android 10/API 29 phone and a current supported Android phone:
  Bluetooth/location/notification denial, grant/revoke, service start/timeout,
  backgrounding, screen off, rotation and OS interruption.
- [ ] Publish a redacted [compatibility report](vehicle-compatibility.md) for each
  claimed adapter/vehicle combination: discovery per ECU, actual PID returns,
  measured per-sensor rates, no-data/stale states and reconnect behavior.
- [ ] Confirm read-only commands only; unsupported sensors are explicitly not
  monitored. No manufacturer limits or proprietary commands are inferred.
- [ ] Verify source independence: GPS loss does not terminate functioning OBD;
  OBD loss does not terminate functioning GPS. Stale values do not look current.
- [ ] Test outdoor GNSS accuracy/age/rate, directional start/sector crossings,
  rejected jumps and invalid laps on a safe known course. Tiles must be optional.
- [ ] Test authorized offline map coverage/provider failure and preserve attribution.

Do not induce low oil pressure, overheating, or another dangerous fault on a real
vehicle. Use synthetic/replayed data for warning tests and respect factory warnings.

## Recording and failure endurance

- [ ] Record a documented long session (target at least two hours and the intended
  event duration); measure battery, thermal throttling, sample rates, storage growth,
  bounded memory/writer pressure and foreground-service restrictions.
- [ ] Test low/full storage or injected write errors: no false “saved” result,
  controlled failure, usable interrupted history, and export/delete afterward.
- [ ] Test process death/force-stop/relaunch, interrupted-session recovery, no
  duplicate session, and clear limits on uncommitted samples.
- [ ] Test export cancellation/failure, large paged export, session deletion cascade,
  active-session deletion guard, notes and saved settings snapshots.
- [ ] Test read-only replay, malformed/gapped samples and Back cancellation without
  modifying history or starting Bluetooth/network sharing.
- [ ] With sharing off, no receiver calls. With opt-in sharing, verify HTTPS/auth,
  privacy allowlist, no redirects, slow/down receiver, bounded drops and local
  recording continuity. Inspect receiver retention and access controls.

## Signing and distribution (manual owner approval)

- [ ] Select a release version and changelog with honest verified hardware scope.
- [ ] Generate/store the production signing key outside the repository, with an
  encrypted backup and documented owner-controlled recovery. Never commit it,
  passphrases or environment files; never reuse the public debug key for release.
- [ ] Build with the approved release signing configuration and verify the
  signature/certificate using Android SDK `apksigner verify --verbose --print-certs`.
  Record the expected certificate fingerprint and APK SHA-256 checksum.
- [ ] Install the actual signed release APK on target phones, test upgrade from
  the prior supported release, database preservation and fresh install.
- [ ] Publish only after explicit owner approval, including source tag/commit,
  APK checksum/signing fingerprint, license notices, privacy/security links,
  known limitations and completed/pending qualification evidence.
- [ ] Ensure GitHub private vulnerability reporting is enabled or a working private
  contact is documented before a public release. Do not rely on an untested inbox.

No signing key, store account, automated publishing or hardware certification is
created by completing the source changes alone.
