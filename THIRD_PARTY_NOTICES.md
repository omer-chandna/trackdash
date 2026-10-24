# Third-party notices

TrackDash's own work is Apache-2.0; that does not replace dependency licenses.
The [Apache license text](LICENSE), [project notice](NOTICE), this file and
`licenses/` are packaged in every APK under `assets/opensource/`. The build also
copies LICENSE/NOTICE/COPYING entries from resolved runtime JARs and AAR classes
into `opensource/embedded/<coordinate>/` without modifying their contents.

## Reviewed runtime components

Reviewed on 2026-10-24 from the **resolved releaseRuntimeClasspath** and the pinned
artifacts' published Maven POMs (including parent metadata for Guava). This is an
attribution inventory, not a vulnerability scan or legal certification.

| Component family | Declared license / retained material |
|---|---|
| AndroidX Activity, Compose/Material icons, Lifecycle, Navigation, Room, SQLite, core and support libraries | Apache-2.0; artifact notices retained by the packaging task |
| AndroidX DataStore, except external protobuf | Apache-2.0 |
| `androidx.datastore:datastore-preferences-external-protobuf:1.2.1` | **BSD-3-Clause**; exact bundled LICENSE.txt retained under `embedded/` |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization, JetBrains annotations | Apache-2.0 |
| JSpecify, Gson, Guava ListenableFuture | Apache-2.0; ListenableFuture inherits its license from `guava-parent:26.0-android` |
| OkHttp, Okio, Timber | Apache-2.0 |
| MapLibre Android SDK 13.6.1 | BSD-2-Clause for MapLibre, with separately licensed native dependencies; retain the complete [upstream Android notice](licenses/MapLibre-Native-Android-13.6.1.txt) |
| MapLibre Java GeoJSON/Turf 6.0.1 | Apache-2.0 |
| MapLibre Android Gestures 0.0.4 | BSD-2-Clause with upstream component notices; see [complete notice](licenses/MapLibre-Gestures-Android-0.0.4.txt) |

All **97 external runtime coordinates**, exact versions and POM evidence URLs are
in [runtime-license-metadata.tsv](licenses/runtime-license-metadata.tsv).
[runtime-dependencies.txt](licenses/runtime-dependencies.txt) is the machine-checked
coordinate snapshot. These do not imply that every file in a component has only
one license: keep embedded notices and the upstream native dependency notices.

The MapLibre notices were copied verbatim from its versioned upstream sources:

- [Native Android v13.6.1](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/LICENSE.md).
- [Gestures 0.0.4](https://github.com/maplibre/maplibre-gestures-android/blob/0.0.4/LICENSE.md).

Their upstream Java dependency lists may differ from TrackDash's resolved versions;
the TSV records what TrackDash actually resolves. Do not edit upstream license text
to make those lists appear to match.

## Updating dependencies

1. Run `./gradlew :app:reportOpenSourceRuntimeArtifacts` and inspect the changed
   coordinates and their POMs, parent licenses, embedded NOTICE files and native
   dependencies. Missing metadata is a review task, not permission to assume a license.
2. Obtain full, version-pinned upstream license/NOTICE texts for new dependencies
   not already covered; preserve copyright and required acknowledgements. Review
   changed MapLibre native notices separately.
3. Update `licenses/runtime-dependencies.txt`, the metadata TSV, this summary and
   any vendored notices. Keep build/test-only dependencies distinct from shipped
   runtime software.
4. Run `:app:verifyOpenSourceInventory :app:assembleDebug` and inspect the APK's
   `assets/opensource/`. The inventory task rejects unnoticed additions/removals;
   it does not automatically decide whether a new license is acceptable.

Build/test tools such as Gradle, Android Gradle Plugin, KSP, JUnit, test runners and
GitHub Actions are not represented as APK runtime dependencies. Their own licenses
still govern their use/redistribution. The Gradle wrapper source carries its own
notices; downloaded toolchains are not part of this repository.

## Maps and external data

No tile corpus, online provider account, vendor firmware, vehicle dataset or
third-party track collection is licensed by TrackDash's Apache grant. Map styles,
sprites, glyphs, tiles and offline packages have their own terms and attribution.
Keep MapLibre attribution controls visible and comply with your configured
provider's attribution/usage requirements. Do not commit provider credentials.

This file is informational; the actual license texts control. The Apache
Software Foundation's [license FAQ](https://www.apache.org/foundation/license-faq.html)
and [Apache-2.0 text](https://www.apache.org/licenses/LICENSE-2.0) describe the
project license; consult the specific upstream text for each third-party component.
