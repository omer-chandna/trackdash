# Open-source foundation review

Owner-approved license: Apache-2.0. Companion functional work is
[PR #2](https://github.com/omer-chandna/trackdash/pull/2). Merge that PR before
calling the monitoring milestone complete. New work remains IN_REVIEW until
approved integration; this is not a production release or a hardware certification.

## Package

- Full project LICENSE and NOTICE; third-party attribution is kept separate.
- Reviewed release-runtime inventory: 97 external coordinates, exact version/POM
  metadata, upstream native notices and embedded JAR/AAR license texts. The
  inventory check rejects unreviewed graph changes and runs in CI.
- APK license/notice assets and a readable offline viewer under Settings with
  visible/system Back. The viewer uses the cockpit dark theme independently.
- Security reporting, privacy/export boundaries, contributor guidance, focused
  issue/PR templates and an explicit owner-controlled signing/release checklist.
- Compatibility matrix distinguishes software implementation, user reports and
  maintainer-reproduced physical evidence. No real combination is marked verified.
  Measurement instructions deduplicate cached per-sensor acquisition timestamps.

Attribution is not a legal certification or vulnerability scan. Map/provider data
keeps its own terms; tokens, toolchains, signing keys and private telemetry are
not included. No GitHub security setting, production key, store account, release,
cloud backend, automatic uploader or recurring service was created.

## Evidence

Combined candidate: 100 JVM tests, 26 API 29 Android tests, zero lint errors and
warnings, debug assembly and 97-coordinate attribution inventory. The two
attribution tests verify shipped texts and offline viewer Back behavior. The
functional branch's tests and the foundation-on-main branch are checked separately
before handoff; remote CI and hardware qualification are distinct gates.

Standalone foundation on main: 61 JVM tests and 20 API 29 Android tests passed,
including a repeat of all 20 after scroll/state synchronization corrections.
Debug assembly and the attribution inventory passed. Lint has zero errors and
one existing `ModifierParameter` warning in `StatusChip`; the companion functional
PR fixes that warning, so the combined candidate has no lint issues. CI specifies
the representative Pixel 2 device rather than assuming the runner's smaller
default emulator matches the landscape visibility contract.

The complete license text is retained from the
[Apache Software Foundation](https://www.apache.org/licenses/LICENSE-2.0).
Native notices are the exact versioned upstream texts linked from
[third-party notices](../../THIRD_PARTY_NOTICES.md). The declared POM inventory
includes the DataStore external protobuf BSD-3-Clause exception rather than
assuming every AndroidX artifact has the same license.

## Release gates still pending

Physical adapter/vehicle and current Android qualification; outdoor GNSS; long-run
storage/battery/thermal measurements; actual HTTPS receiver/TLS/mobile transitions;
private security-reporting availability; owner-managed release signing and an
explicitly approved distribution. See [release checklist](../release-checklist.md).
