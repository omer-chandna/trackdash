# Simulation

Two workflows serve different needs:

- **Try vehicle simulation:** quick live demo with editable synthetic warning
  limits and normal/overheat/low-pressure/disconnect scenarios. Stops on background;
  rotation retains it. No recording.
- **Set up recording → Simulation:** production recorder with GPS-only, vehicle-only
  or combined sources. Real timing/alerts/persistence/export process synthetic data.
  Foreground service survives normal navigation/backgrounding and offers stop/save.

GPS follows a 150 m radius demo circuit at 5 Hz with start/finish and two ordered
sector gates. Pace is adjustable from 20–120 seconds per lap. Phase integration
prevents teleporting when pace changes and speed reflects pace. Dropout and jump
controls exercise invalidation. First crossing starts timing; later crossings
complete laps. Simulation uses monotonic acquisition and wall-clock timestamps.
Vehicle samples run at 10 Hz with deterministic RPM, gear, temperatures and other
standard sensors. Fault controls deliberately clear health/value availability.

All UI/export data is labelled simulated. The model is not a calibrated BMW N52
or a promise of real GNSS/OBD rates. Recording uses selected profile warning limits;
blank temperature/RPM settings disable those thresholds. Low-pressure/disconnect
rules are available when warnings are enabled. Track/settings snapshots, warnings,
GPS/vehicle samples and lap events can be reviewed/exported from Sessions.
