# IRAQhub+ 4.0.2 Alpha — X5M progress

## Verified repository changes
- Dock in-player InfoBar flush to the bottom edge by default; no extra floating margin.
- Remove InfoBar ribbon rounded corners and horizontal/bottom floating margins.
- Add a user-selectable `osd_dock_to_bottom` preference to OSD settings.
- Retain original DVB playback, native receiver integration, artwork providers and 4:2:2 engines; no risky library removal without device tests.
- The user-supplied IRAQhub+ artwork has been converted into Android icon/banner assets in an external ZIP, **not yet merged into Git**. The existing manifest launcher icon remains unchanged.

## Pending acceptance gates
1. Integrate user-supplied PNG assets into Android resource tree and manifest.
2. Build `assembleX5mPreview` on CI with pinned NDK r29, inspect logs and APK artifact.
3. Test real X5M hardware: InfoBar on 1080p display, remote digits and volume keys, front-panel support.
4. Diagnose posters via `osd_now_poster_status`, `osd_next_poster_status` and real EPG programme metadata.
5. Capture actual raw 4:2:2 TS and test MPV libavcodec decoder and output on ARMv7; avoid treating MediaCodec advertised HEVC as 4:2:2 support.
6. Publish a pre-release only after an installable, signed preview APK is produced.

No APK build or hardware success is claimed in this document.
