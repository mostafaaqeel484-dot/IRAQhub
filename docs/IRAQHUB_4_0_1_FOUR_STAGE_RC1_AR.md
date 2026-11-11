# IRAQhub 4.0.1 RC1 — X5M four-stage release notes / ملاحظات التطوير

**Device target:** Amlogic X5M, Android TV 14 (API 34), 32-bit `armeabi-v7a`, 4GB RAM.
**Delivery:** isolated package `com.smarthub.plus.x5m401` (different from original `com.smarthub.plus`), separate app data and temporary Debug signature.
**Source:** stacked on PR #41's HEVC/TS fixes; previous main app stays untouched.

## المرحلة 1 — الاستقرار ومشغل 4:2:2
- Integrated PR #41 source-TS packet continuity preservation, lost CC/TEI diagnostics, low-churn PVR rolling, and single SW/no-audio/GPU-native stall-recovery.
- Added optional **Native422 V2 guarded session** to player menu. V2 uses the existing native libmpv's FFmpeg decode backend in an isolated process, with SW/video-only/native output defaults, not a separate direct-FFmpeg JNI codec implementation.
- Maintains original DVB, LibVLC and MPV options, plus a manual **Safe Mode** preference which routes satellite channel selection to the normal DVB player and does not touch system firmware.
- IMPORTANT: Live HEVC 4:2:2 10-bit, first-frame output and smooth 50 FPS are not proven. Source video sometimes reports `hevc 1920x540 50fps`, not necessarily true 4:2:2. Do not claim the TV picture works until X5M testing.

## المرحلة 2 — القائمة والـInfoBar
- DVB satellite channel menu: removed on-screen search field and redundant big bottom signal boxes; faster, larger visible list.
- YELLOW (and A–Z tool) opens exact initial-letter selection: **A–Z**, **Arabic letters**, `#` for digits, **All** to clear. The chosen letter filters all matching channel names and BACK clears the active filter. Does not mutate saved channel order.
- InfoBar follows supplied broadcast reference: black bottom ribbon, green channel title, EPG now/next and progress, tuner S/Q, icon rows moved under right-aligned date, year in smaller type above `الأحد 8 نوفمبر 5:55` style with the device timezone. Lower near screen edge.
- Posters default off in compact ribbon; can be reenabled in appearance settings.
- Thmanyah font: **local import from a legitimate user-owned OTF** under `Information bar > Import personal Thmanyah OTF`, then app-owned private storage. Fonts are NOT added to the repo or rehosted because the typeface owner forbids font-file redistribution/hosting; originals can be downloaded from their official source. Native receiver fallback remains available.

## المرحلة 3 — رسيفر وEPG/CHMAX/PVR/backup
- Preserves existing Aurum-safe CHL channel index, manual tuning/OEM scan route, motor/LNB menu, authorized BISS dialog, DVB EPG, verified CHMAX logo matching, favourite groups, track selection and bounded PVR capture.
- The old firmware scanner limitations still exist; `tp_record=1` does not prove that the firmware exposes a full-TP stream. No claim of fixed blind scan.
- In `Settings > Stability & backups`, added safe read-only user ZIP backup verification (manifest format, member safety, SQLite header, size cap and CRC); **no destructive restore** or OEM DB modifications.
- Existing channel DB export remains available; preview app data is isolated, so it does NOT copy channels/motor settings from the original installation automatically.

## المرحلة 4 — أداء وكاش
- All player-heavy services remain app-owned and run through `PlaybackResourceMode` and `IraqHubSyncScheduler` pausing background artwork/prefetch while SW HEVC runs.
- Fixed hidden poster work: when no posters are shown, safe mode is enabled or software playback focuses resources, `LocalInfoBarController` now avoids scheduling poster decoding/network requests.
- Keeps independent NOW/NEXT cache, visible-row logo request discipline, CHMAX/EPG providers and exact service ID matching; no remote-service bypass.
- Size of shipped APK is smaller because only `armeabi-v7a` native libraries are packaged.
- Android JUnit, signing, APK CRC, AAR libmpv DEX presence and NDK r29 C++ dynamic symbols are checked in CI. Test before replacing any launcher/home settings.

## Device acceptance checklist
1. Install 4.0.1 RC1 Preview **beside** original. Do NOT uninstall `com.smarthub.plus`.
2. Import a valid CHL into this distinct app if its own channel list is empty; private app data is not migrated.
3. YELLOW -> B: verify only names beginning B; BACK restores ALL; Arabic initial `ا` also filters appropriate Alef forms.
4. Confirm INFO bottom ribbon date/year/icon layout and readability from sofa distance.
5. In developer/player menu, select 422 V2 on a clear, authorised live MPEG-TS feed. Compare normal DVB, SW V2 and existing isolated MPV. Check actual visibly moving frames, decoded source format, dropped frames, TS CC/TEI logs, CPU.
6. Turn on Safe Mode; verify normal DVB is selected on next channel selection and old app is untouched.
7. Verify and export a personal backup and check artwork does not fetch in background when hidden.
8. Run 10-minute continuous source playback only after a moving image appears. If black, capture MPV Stats and `adb logcat -d -v time | grep -Ei 'IRAQhub422|ffmpeg/video|TS_SOURCE_INTEGRITY|HEVC_NO_FRAMES' | tail -n 120`.

### Known caveats
- No new independent JNI libavcodec engine in this RC; a completely separate hardware-validated decoder is a longer R&D item. V2's orchestration is real, its decode backend remains libmpv/FFmpeg.
- Black screen after HEVC RPS/POC packet loss is not guaranteed fixed; synthetic TS continuity counters were removed but missing packets cannot be regenerated. A damaged PVR feed remains damaged.
- Preview APK uses CI Debug signature so upgrading to a *different build of the same preview package* may require a stable future keystore; do not uninstall the original application.
- A private GitHub repository **Pre-release (draft=false)** requires signing into the repository owner's GitHub account for APK download.
