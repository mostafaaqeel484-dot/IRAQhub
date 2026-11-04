# IRAQhub 3.6.5 — Low-overhead playback telemetry

## Changes
- Move per-callback frame-processing and dropped-frame accounting into in-memory atomic counters.
- Persist aggregated telemetry at most once per second instead of writing SharedPreferences on every Media3 callback.
- Reset all per-channel playback counters when a new channel starts, preventing mixed channel statistics.
- Retain the existing Media3 diagnostics and conservative 4:2:2 verification states.
- Version code 28; version name 3.6.5.

## Validation boundary
- Static source checks and archive integrity checks only.
- No Android SDK/Gradle build or physical receiver test is claimed.
- Metrics remain Media3 telemetry and do not prove hardware 4:2:2 bitstream correctness.
