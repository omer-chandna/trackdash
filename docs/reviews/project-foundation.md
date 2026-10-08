# Foundation branch review

Historical review from before integration. The owner subsequently approved and
fast-forwarded this foundation into local main on 2026-10-08. Current status is
maintained in the roadmap; see the vehicle-simulation review for the next increment.

Branch: `feature/project-foundation` → `main` (local, unmerged).

## Purpose and scope

Turn the initial README-only repository into a buildable native Android application.
Deliver the first practical milestone before implementing simulated/hardware telemetry.

- Kotlin/Compose Material 3 app, API 29 minimum and API 37 target.
- Dark motorsport theme, launcher icon, matching system bars and responsive navigation.
- Home, Tracks, Sessions and Diagnostics with explicit inactive/empty states.
- Operating-mode selection through a SavedStateHandle-injected ViewModel/StateFlow.
- Independent vehicle and GPS contracts in a JVM module, canonical units and validated data.
- Gradle wrapper with distribution checksum, version catalog and documented dependency evidence.
- Build/lint/test/APK CI and an API 29 emulator job; actions pinned to verified release commits.
- Persistent Cursor engineering rules, architecture, workflow, roadmap and feature constraints.

Two modules keep Android concerns separate while avoiding premature fragmentation.
Jetpack's ViewModel factory supplies SavedStateHandle; no dependency service locator
or unnecessary Hilt code generation is introduced at this stage. Hardware and
simulation implementations will enter behind the shared contracts.

## Validation executed

| Check | Result |
|---|---|
| `:core:test` | 7 passed, zero failures |
| `:app:testDebugUnitTest` | No app JVM test sources; UI behavior covered on emulator |
| `:app:connectedDebugAndroidTest` | 3 passed on API 29 x86_64 emulator |
| `:app:assembleDebugAndroidTest` | Test APK compiled |
| `:app:lintDebug` | 0 errors, 1 Gradle-version advisory |
| `:app:assembleDebug` | Debug APK built |
| `adb install` / explicit activity launch | Successful on API 29 emulator |
| Portrait and landscape screenshots | Visually inspected; system-bar contrast corrected |
| CI YAML parsing | Valid jobs/triggers; remote GitHub execution not performed |
| `git diff --check` | Passed |

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
Reports are described in [testing](../testing.md).

## Screenshots

[Portrait](../screenshots/foundation-portrait.png) · [Landscape](../screenshots/foundation-landscape.png).
Both are actual emulator captures, not mockups. Content scrolls on short screens;
navigation remains accessible outside the scrolling page.

## Limitations and integration

No acquisition, simulator, timing engine, map, persistence, warning engine, or
export is delivered in this branch. API 37 runtime and all physical hardware remain
unverified. Hilt, Room, DataStore and MapLibre enter with functional features.
Gradle remains on the documented AGP default within Kotlin's supported range.

No remote push, PR publication, merge or release occurred. Main is unchanged.
The feature remains IN_REVIEW until explicitly approved and integrated, as required
by the supplied engineering workflow. The next dependent increment is
`feature/simulation-engine`: deterministic simulated readings and start/stop
through the shared vehicle source, visibly marked as simulation. Start it from
the approved foundation integration point, then add the dashboard and alert branches.
