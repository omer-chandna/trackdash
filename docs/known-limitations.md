# Known limitations

The app implements a deterministic vehicle simulator, live dashboard, fault
scenarios, demo warning limits, navigation and operating-mode selection. Tracks
and Sessions display empty states; Diagnostics distinguishes the running simulator
from disconnected physical OBD, inactive GPS and recording.

Bluetooth, GPS acquisition/simulation, timing geometry, lap/sector state machines,
maps, track editing, Room storage, DataStore settings, CSV export and
manufacturer-specific diagnostics are future increments. No physical hardware
is verified. Mode selection survives saved-instance restoration but is not a
persisted user preference across an explicit force-stop/fresh launch.

Demo values are synthetic, not a calibrated BMW model. Temperature defaults are
explicit demo examples, not manufacturer safety thresholds; limits last only as
long as the ViewModel. Alerts currently cover configured temperatures, low
oil-pressure switch and disconnected data. Audible notifications, history,
persisted enable/threshold settings, and general stale-reading detection are not
implemented. The source emits explicit unavailable values for its disconnect
scenario. Real polling must implement acquisition/freshness checks independently.

Simulation survives navigation/rotation but stops on backgrounding; it is not a
foreground recording service. There is no sample history, recording or export.

Android 10 emulator launch and UI tests pass. API 37 is compiled/targeted but has
not been runtime-tested. Accessibility at large font scales and long-session
performance await feature UI development. UI text is currently English-only.

Gradle 9.6.0 is pinned to AGP 9.4's documented default and within Kotlin 2.4.20's
documented compatibility range. Version-upgrade advisories may depend on the
metadata available to lint; the simulation validation run reported no issues.

The CI workflow is configured locally but has not run on GitHub. Foundation was
fast-forwarded into local main with owner authorization, and subsequent work is
committed directly to local main as requested. No remote push, PR publication or
release has been performed. The owner must select a
license before an open-source release. Dependency/native-library strip messages
do not indicate physical or performance validation.
