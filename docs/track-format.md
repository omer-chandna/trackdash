# Track JSON v1

TrackJson encodes a document with `version: 1` and a `track` containing `id`, `name`,
`startFinish`, ordered `sectors` and optional `notes`. Each gate has id/name,
start/end points (`latitude`, `longitude`) and direction (`EITHER`, `RIGHT_TO_LEFT`,
`LEFT_TO_RIGHT`). Direction is relative to the directed start→end gate segment.

Decoder limits input to 128,000 characters, rejects unknown versions/fields,
nonfinite/out-of-range coordinates, duplicate gate IDs, gates outside 1–500 m,
and more than 12 sectors. SAF import enforces the size limit before deserialization.
An imported track receives a new local ID to avoid silent overwrite. Export is
pretty-printed JSON including defaults. Round-trip and malformed-input tests live
in core. A recording stores a track snapshot, so later edits do not change its data.

Map packages use MapLibre offline-region SQLite databases separately; track JSON
contains no map tiles. Providers and packages must be licensed for the intended use.
