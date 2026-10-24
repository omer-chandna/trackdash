# Dependency decisions

Verified 2026-10-08 against official release notes and repository metadata.
Versions are pinned in `gradle/libs.versions.toml` and the wrapper properties.

| Component | Pin | Evidence |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | [9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Google Maven](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) |
| Gradle | 9.6.0 | AGP 9.4 compatibility table; distribution SHA-256 pinned |
| Java | 17 | AGP compatibility table; local Eclipse Temurin archive SHA-256 verified |
| Kotlin / Compose compiler | 2.4.20 | [Kotlin release notes](https://kotlinlang.org/docs/whatsnew2420.html); JVM plugin upgrades AGP's built-in Kotlin dependency |
| Compile/target SDK | 37 | [Android 17 setup](https://developer.android.com/about/versions/17/setup-sdk); stable `platforms/android-37.0` package |
| Compose BOM | 2026.09.00 | [Official BOM guide](https://developer.android.com/develop/ui/compose/bom) |
| Activity | 1.13.0 | [Release notes](https://developer.android.com/jetpack/androidx/releases/activity) |
| Lifecycle | 2.11.0 | [Release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| Navigation Compose | 2.10.2 | [Release notes](https://developer.android.com/jetpack/androidx/releases/navigation); stable maintenance release |
| Coroutines | 1.11.0 | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml) |

AGP supplies Android Kotlin support; do not apply the legacy Kotlin Android plugin.
Navigation Compose provides a small established back stack; revisit Navigation 3
when more complex multi-pane navigation is needed. Room, DataStore, Hilt, and
MapLibre will be verified and pinned with their first functional integrations.

CI actions are pinned by commit to official release tags verified through GitHub
on the same date: checkout v7.0.1, setup-java v6.0.1, setup-android v4.0.4,
gradle/actions v6.4.0, upload-artifact v7.0.2, android-emulator-runner v2.38.0.
The workflow uses hosted Ubuntu runners; inspect each PR's remote results separately
from the documented local verification.

## Integrated data/map dependencies

Pinned and resolved in the integrated 0.3 build:

| Component | Pin | Evidence |
|---|---|---|
| Room | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore | 1.2.1 | https://developer.android.com/jetpack/androidx/releases/datastore |
| MapLibre Android | 13.6.1 | https://maplibre.org/maplibre-native/android/api/ |
| KSP | 2.3.12 | https://github.com/google/ksp/releases |
| kotlinx.serialization | 1.11.0 | Maven Central metadata; version catalog |

Room schema generation uses KSP and exports the v1 schema. DataStore is used for
profile settings, serialization for persisted/shareable typed payloads, and
MapLibre for editor/path rendering and offline-region package import. Manual
application composition remains in use; Hilt has not been introduced.

## Binary attribution

The open-source foundation adds a reviewed 97-coordinate release-runtime inventory,
exact POM metadata and retained native/embedded notices under `licenses/`.
Run `:app:verifyOpenSourceInventory` whenever dependencies change. Packaging and
offline viewing do not constitute vulnerability scanning or legal certification;
see [third-party notices](../THIRD_PARTY_NOTICES.md).
