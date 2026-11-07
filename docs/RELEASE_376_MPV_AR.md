# IRAQhub 3.7.6 RC1 — X5M / Android 14

**Pre-release / user acceptance build — 7 November 2026**

## New capabilities
- **Actual in-app MPV + VLC choice** for 4:2:2 DVB channels in channel details. `dev.jdtech.mpv:libmpv:1.0.0` is a Maven AAR with bundled FFmpeg/libmpv native renderer; it runs on the *same* app-owned MPEG-TS relay as existing LibVLC. MPV defaults to FFmpeg software decoding and OpenGL output; it returns to LibVLC on init/runtime errors. Choice is available in the 4:2:2 playback settings too.
- **DVB channel zapping**: retain tuner lease on the same physical TP *only when firmware lock is verified*, rate-limit expensive EPG refresh, log tune lock/decoder ACK/observed firmware frame progression latency.
- **Bounded recovery**: when firmware counters demonstrably stall on a locked, free-to-air service, attempt up to two restarts with cooldown; never override PVR, PIP, CA/BISS, or a retuning tuner. Video-flow counter != on-screen decoded image.
- **User-controlled backups**: export app-owned SQLite channel catalogue/favourites and safe appearance settings to ZIP using Android's document picker; import only after schema/integrity checks and transactional database replacement. Save the currently installed APK separately before updates. No OEM DB reads/writes.
- **Release signing workflow**: uses optional long-term developer-owned GitHub Actions secrets; if not configured, Debug is development-signed and Release is **unsigned**. Same-package updates require the same signing certificate.

## Safety and test limits
- This is not an approved production release; **real X5M 4:2:2 10-bit hardware/color/fps validation is still pending**.
- Device report from 2026-11-07 showed `FIRMWARE_CAPTURE_NO_TS_DATA` (zero bytes) for direct manual search; new MPV **does not fix lack of MPEG-TS capture**. Neither MPV nor VLC can decode missing transport stream data.
- libmpv is a software codec path; full-speed 1080i50/10-bit is *not guaranteed* on Amlogic X5M. GPU output compatibility needs device testing.
- Backup ZIP excludes keys, provider credentials, account tokens, or OEM databases. Restore does not downgrade APK or silently replace firmware services.
- Debug build signature can change between CI runs, so back up installed data before attempting replacement. The Release_UNSIGNED file cannot install until signed.
- Existing 3.7.3 stable tag on `main` is unchanged; this RC runs from feature branch for hardware acceptance.

## Suggested receiver verification
1. Open Channel List → selected channel details → **4:2:2 → MPV or VLC**; test on the same actual HD feed.
2. Record TS capture bytes, MPV video configuration, first displayed frame and any fallback.
3. Compare 10-minute playback audio sync/color/frames in both engines, including 1080i50.
4. Zap between two FTA channels sharing a TP and between TPs, compare `zap_tune_lock_ms` and `zap_decoder_ack_ms`.
5. Export a channel backup ZIP and installed APK, then test restoration of the ZIP on a spare/test installation.
6. Verify production signing certificate fingerprint matches the **currently installed** app before update; never uninstall the only copy without a backup.

Source: GitHub PR for this branch, plus mpv-android/libmpv APIs from their public source.