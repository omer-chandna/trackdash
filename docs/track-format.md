# Track format

Track persistence and JSON import/export are planned, with no supported file format
yet. Do not distribute foundation files as compatible track packages.

The first version will specify a schema version, stable IDs, name, WGS84 coordinates,
finite start/finish endpoints, accepted crossing direction, ordered sector gates,
and optional distance/notes. Reject unsupported versions, invalid coordinates and
degenerate gates. Round-trip and malformed-input tests precede format publication.
Maps must use licensed sources and timing must remain independent of tile access.
