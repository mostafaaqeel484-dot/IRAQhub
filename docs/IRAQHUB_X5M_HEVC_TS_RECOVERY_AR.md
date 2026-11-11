# IRAQhub 4.0.0 X5M: HEVC transport continuity + bounded stalled-video recovery

## Evidence from X5M (8 Nov 2026)
- ARMv7 32-bit, Android TV 14 (API 34), `armeabi-v7a`.
- libmpv opened `http://127.0.0.1:<port>/<token>.ts` and identified `mpegts`, video `hevc 1920x540 50fps`, audio `mp2`.
- Field error spam `Could not find ref with POC`, `Error constructing frame RPS`, invalid HEVC NALU. MPV FILE_LOADED/clock do not prove displayed frames.
- OEM `AmCodecVDA` logs are **another Android process**, so do not infer they come from libmpv software decode.
- Blank screen with no decoded pixel format and absent FPS despite approximately 5.36 Mbit/s TS source capture.

## Corrections in this Draft
1. **Do not rewrite every MPEG-TS continuity counter.** Old `TsSplice.Continuity.rewrite()` silently falsified CC on missing/duplicated packets. Lossy HEVC PES cannot be repaired by header rewriting. New `observe()` counts genuine source packet gaps, TEI flags, invalid sync/headers and leaves original bytes untouched so FFmpeg can recognize discontinuities.
2. Log `TS_SOURCE_INTEGRITY` no more than every ~5 s during damaged input and expose `TS original CC gaps`, `TS transport error packets`, `TS last gap PID` and `PVR rollovers` in MPV Stats. No decoded frames with zero CC gaps still permits a bad HEVC source or wrong ES framing: CC covers only transport continuity.
3. PVR rotation threshold raised from 24 MiB (~38 s at 5.36 Mbit/s) to **48 MiB** (~75 s), remaining bounded and using current existing app-owned PVR capture. This reduces segment transitions; a firmware-reported gap can still happen.
4. A fresh MPV HTTP client starts from the *newest existing TS segment*, not the previous potentially damaged segment. It still may need to wait for the next valid HEVC random-access frame.
5. If loaded but source pixfmt unknown and FPS non-positive for >=3 samples after >=18s, reconnect **once** to existing TS without changing tuner, using SW decoder / GPU-native output / Video Only. No infinite restarts. If it remains black, report `HEVC_NO_FRAMES_AFTER_ONE_RETRY` and collect logs.
6. The remote control buttons are now dark with visible white text, distinct focus border and pressed color. Previous white-on-white buttons were unreadable on Android TV 14 firmware.
7. Preview package has a **new distinct id** `com.smarthub.plus.x5mhevc` to avoid CI Debug signing collision with the original and older preview apps; native libs only `armeabi-v7a`; NDK r29 libc++ retained. Does not migrate original app settings.

## Validation (CI)
`.github/workflows/x5m-hevc-ts-reference-recovery.yml` runs unit tests including `TsSpliceContinuityTest` and `Mpv422StreamHealthPolicyTest`, compiles ARMv7-only APK, checks packaged MPV DEX and C++20 dynamic symbols, APK CRC, signature, alignment, package id, minimum Android SDK and publishes a Draft private release on success. Real DVB/HEVC motion is **not** verifiable in CI.

## X5M acceptance test
1. **Do not remove** the installed `com.smarthub.plus`; the new APK is called `IRAQhub 422 HEVC Fix` and is separate.
2. Try the same authorised free-to-air HEVC feed using SW + Video Only + GPU Native for 90 seconds, without changing 420/422 conversion.
3. Confirm whether real moving pictures appear, check MPV Stats. If still black, inspect `TS original CC gaps` and `TS transport error packets`.
4. From previously authorised Termux ADB:
```sh
adb logcat -d -v time | grep -Ei 'IRAQhub422|TS_SOURCE_INTEGRITY|PVR_ROLLOVER|HEVC_NO_FRAMES|ffmpeg/video' | tail -n 120
```
5. Try a known-good HEVC 4:2:0 channel to isolate whether this is source-specific versus the relay/decoder. If HEVC 4:2:2 input really is 50 fps, Amlogic X5M CPU might still be insufficient for smooth software 50 fps; the purpose here is to recover *any valid frame* first.

**Not proven:** A continuity fix cannot restore packets genuinely missing from OEM PVR capture. Complete validation requires device testing with one good live feed and possibly saved TS samples/CC analysis.
