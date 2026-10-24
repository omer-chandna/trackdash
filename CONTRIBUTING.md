# Contributing to TrackDash

TrackDash is a read-only performance monitor and logger, not a repair or engine
protection tool. Keep standard OBD-II the default, and make unsupported/stale
readings explicit. Never infer sensor support from a manufacturer/model label.

Read [architecture](docs/architecture.md), [workflow](docs/development-workflow.md),
[roadmap](docs/roadmap.md), and the [engineering rules](.cursor/rules/engineering.mdc).
By intentionally submitting a contribution for inclusion, you agree to its
distribution under [Apache-2.0](LICENSE). Retain existing attribution and identify
third-party material; do not submit code or captures you cannot redistribute.

## Getting started

1. Follow the [README build instructions](README.md#build-and-install). Start with
   simulation; no adapter, vehicle, map token, or cloud account is needed.
2. Open a focused issue describing the user problem and acceptance criteria.
   Use the compatibility template for hardware reports, not a blanket claim of support.
3. Make a small feature/fix branch. Preserve unrelated work, use Conventional
   Commits (`fix:`, `feat:`, `docs:`, `test:`), and keep dependent PRs explicit.
4. Add regression tests, run relevant checks, and submit a PR describing what was
   actually verified. Never describe emulator tests as physical hardware evidence.

## Module boundaries

| Change | Put it here | Test without hardware |
|---|---|---|
| Standard PID decoding, framing, capabilities | `core` OBD/protocol packages | Sanitized fixtures, ECU isolation, malformed/partial responses |
| Samples, status/freshness, warnings, replay/packet logic | `core` telemetry packages | Deterministic timestamps, transitions, missing/stale/error and privacy cases |
| GPS crossings and laps | `core` GPS/timing packages | Directional finite gates, bad fixes, interpolation, ordering |
| Bluetooth, location, network transport | `app` sources/sharing | Fake source/transport plus permission/lifecycle instrumented checks |
| Database, exports, service lifecycle | `app` data/recording | DAO/cascade tests, bounded writer and interrupted-session checks |
| Display/navigation | `app` Compose screens | Semantics, landscape/compact layouts, Back and warning/SHIFT state |

Source acquisition, sensor discovery, protocol decoding, UI, and storage must stay
separate. No sockets/listeners in composables, no Android dependencies in `core`,
and no network dependency for GPS timing or local recording. Preserve canonical
units, acquisition timestamps, availability and explicit simulation labels.

## Verification

```text
./gradlew :core:test :app:testDebugUnitTest :app:verifyOpenSourceInventory :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
git diff --check
```

Run the second command with a configured emulator/device. If a check cannot run,
explain why; do not mark it passed. Add regression coverage for changed logic and
update user-facing docs. For a dependency update, review its resolved runtime graph,
upstream license/NOTICE files and native dependencies, update `licenses/` and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and run the inventory check.

## Hardware reports and privacy

Use the [compatibility report guide](docs/vehicle-compatibility.md#submit-a-report)
with app commit, Android/phone, adapter/firmware/transport, ECU response headers,
supported PIDs and measured sample rates. Park safely and use read-only queries.
Do not induce overheating, low pressure, or other dangerous faults for a test;
use simulation/replay for those transitions.

Never post VINs, Bluetooth MAC addresses, exact locations, credentials, unredacted
CSV, personal notes, or raw packet captures containing them. Use synthetic data
or a minimal sanitized protocol fixture with expected output. Include permission
to redistribute fixtures under the project license.

Security concerns belong in the [private reporting process](SECURITY.md), not a
public issue. Be respectful and specific in reviews. Maintainers may decline
unsafe commands, unverified enhanced sensors, dependencies without clear license
provenance, or changes that break offline/local-first operation.
