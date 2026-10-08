# Known limitations

The foundation implements navigation and operating-mode selection. Tracks and
Sessions display honest empty states; Diagnostics reports inactive sources.
Source interfaces are contracts, not acquisition implementations. There is no
simulation start control or live dashboard yet.

Bluetooth, GPS acquisition, timing geometry, lap/sector state machines, maps,
track editing, Room storage, DataStore settings, warning logic, CSV export and
manufacturer-specific diagnostics are future increments. No physical hardware
is verified. Mode selection survives saved-instance restoration but is not a
persisted user preference across an explicit force-stop/fresh launch.

Android 10 emulator launch and UI tests pass. API 37 is compiled/targeted but has
not been runtime-tested. Accessibility at large font scales and long-session
performance await feature UI development. UI text is currently English-only.

Gradle 9.6.0 is pinned to AGP 9.4's documented default and within Kotlin 2.4.20's
documented compatibility range. Lint recommends a newer Gradle release; this
advisory remains visible rather than adopting an unverified combination.

The CI workflow is configured locally but has not run on GitHub. No remote push,
PR publication, merge or release has been performed. The owner must select a
license before an open-source release. Dependency/native-library strip messages
do not indicate physical or performance validation.
