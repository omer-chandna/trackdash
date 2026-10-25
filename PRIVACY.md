# Privacy and data handling

TrackDash is local-first. No project-operated cloud backend, account registration,
advertising SDK, analytics service, or automatic crash uploader is configured.
The Android Internet permission supports services you explicitly configure.

Monitoring reliability and open-source foundations are integrated through PRs
#2 and #3. Non-recording live view is under review on `codex/live-performance-view`.

## Non-recording live view

Live view keeps the latest vehicle readings and warning state in memory. It does
not create a session, save telemetry/events/snapshots, acquire GPS, or start the
optional sharing sender—even if sharing is enabled for recordings. Back or Stop
live view ends acquisition; normal backgrounding continues it with a foreground
notification. Rotation preserves it; process death does not resume it.
Android may retain navigation and warning-animation state for activity recreation;
that is not a saved telemetry or warning-event history.

Saved vehicle profiles, display/limit settings, and pre-existing sessions remain
on the phone. Explicit configuration saves and compatibility-report exports are
separate user actions, not automatic trip recording. The app still initializes
its local database on startup; live view is not a promise of no filesystem access.

## Stored on the phone

The app-private Room database stores vehicle profiles (including detected VIN
when available), tracks/gates, recordings, telemetry samples, timestamps, GPS
coordinates/quality, laps, warning events, notes and snapshots of session settings.
DataStore holds display/limit settings and configured map/search providers.
Sharing uses separate app-private preferences with an encrypted authorization
value. Setup choices are retained in activity state, not published. Android backup/device transfer is disabled in the app
manifest and extraction rules. Device/OS access controls still apply; data is not
a separately encrypted session vault.

Stop and save a recording before deleting it. Confirmed deletion removes the
session's stored samples, laps, events and notes; it does not delete exported
copies, remote receiver data, tracks, or vehicle profiles. Uninstalling normally
removes app-private data. SQLite/filesystem deletion is not a certified secure
erasure procedure.

## Data leaving the phone

| Action | What the recipient may receive |
|---|---|
| CSV export through Android's document picker | Stored readings/status, timestamps, GPS path and session metadata; inspect notes/profile names and the actual file before sharing |
| Track JSON export | Track name and exact timing-gate coordinates |
| Optional online maps/search | IP address, requested map area/style resources and entered search text/coordinates, according to that provider's policy |
| Optional HTTPS sensor sharing | Only v1 allowlisted sensor readings/status/units/source and source-relative timing; the receiver also sees network IP and request metadata |
| GitHub issue/compatibility report | Whatever you voluntarily post; public posts can be copied permanently |

Maps/search have no built-in hosted provider credentials. Choose services whose
terms and privacy policy you accept. A map style may load its own tile, glyph and
sprite hosts. Offline map databases must be lawfully obtained and may contain
provider-specific metadata. Timing and local recording work without online maps.

## Optional sharing during recordings

Sharing is **off by default**, applies to new live recordings, and needs an explicit
HTTPS receiver URL. It does not upload historical sessions. The fixed sensor packet
excludes GPS coordinates, VIN, wall-clock time, vehicle/session identifiers, track
names and personal notes. Simulation is labelled. The receiver can still infer
activity from sensor readings, elapsed time, connection timing and IP address.

An optional authorization value is encrypted with an Android Keystore key. Do not
put credentials in the receiver URL. Endpoint validation rejects URL user info,
query strings and fragments; redirects are not followed. A small in-memory queue
may drop packets when slow/offline; it is not a durable upload backlog. Receiver
errors do not stop local recording. Local session replay never sends live packets.
See the exact [wire format and receiver guide](https://github.com/omer-chandna/trackdash/blob/eace92219670cf0dae0c01ed25dd6fd4dc4b0071/docs/telemetry-sharing.md)
at the reviewed companion commit.

Disable sharing for future recordings to stop sending to that receiver. To change
settings during an existing recording, stop and save it, then start another. Ask
your receiver administrator separately to delete remote data; local deletion does
not recall prior packets. Review receiver retention, authentication and access
controls before enabling it.

## Share useful evidence safely

Prefer simulation, anonymized protocol fixtures, or a minimal compatibility report.
Remove VINs, Bluetooth addresses, exact home/track routes, names/notes, URLs with
tokens, authorization headers and log details that identify people. CSV is not an
anonymous export. See [Security](SECURITY.md) for private reporting.
