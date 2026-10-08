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
