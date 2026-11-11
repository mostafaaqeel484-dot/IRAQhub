# IRAQhub 3.6.4 — Playback stability telemetry

## Changes
- Reset decoder telemetry at the beginning of each player activity session so previous playback data is not mixed into the current session.
- Count dropped video frames and drop-event batches using Media3 AnalyticsListener.
- Report total and average frame-processing offset per processed sample.
- Report playback-session start time and retain first-frame / codec-error observations.
- Keep the existing conservative 4:2:2 profile-detection wording: a detected profile and a rendered first frame do not prove sustained or bitstream-correct 4:2:2 decoding.

## Validation boundary
- Source-level checks only in this environment.
- No Gradle/Android SDK build and no target-device stream test are claimed.
- Frame-drop and processing-offset metrics are Media3 playback telemetry, not a direct hardware VDEC register readout.
