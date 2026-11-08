# Codex instructions — IRAQhub 4.0.4 X5M

## Source of truth
This branch is a full Git-tracked source snapshot derived from IRAQhub 4.0.4 Draft PR #46 (commit 1828c1ba762106f9607f05761f10ad9d19f9fcf5), itself stacked on PR #45. The top-level README.md has historical information and may be outdated; use current Gradle, workflow, tests and the CODEX_HANDOFF_4_0_4.md document for this version. Never assume that main includes the 4.0.4 work.

## Hardware & guardrails
- Target receiver: Android 14 API 34, Amlogic X5M, 32-bit armeabi-v7a only, 4 GB RAM, external OEM DVB/tuner services (Aurum launcher `com.tosmart.aurum`).
- Default appId: `com.smarthub.plus`. JNI preview: `com.smarthub.plus.x5m404jni`, side-by-side, no replacement of installed production launcher.
- Do not break the existing channel list, DVB access, motor/USALS, BISS, PVR, CHMAX mapping or Tiger remote navigation.
- Do not attempt to bypass OEM/Android permissions. Keep factory UI hidden only where access to documented OEM services legitimately allows.
- Never claim real 4:2:2 decoding, continuous frames, DVB TS capture, or physical installation have been verified just from a green CI build.

## Toolchain & commands
Use actual build.gradle and `.github/workflows/iraqhub-403-native422-jni-armv7.yml`, not old README requirements:
- JDK 21; Gradle 8.11.1; Android SDK platform 36, build-tools 35.0.0.
- NDK 29.0.14206865, CMake 3.22.1, ARMv7 `armeabi-v7a`.
- JVM regression: `gradle :app:testDebugUnitTest --no-daemon`.
- Basic app: `gradle :app:assembleDebug --no-daemon`.
- Verified JNI FFmpeg: `bash scripts/build-native422-ffmpeg.sh "$PWD/.native422/ffmpeg-armv7"`, then
  `gradle :app:assembleNative422Preview -Pnative422FfmpegRoot="$PWD/.native422/ffmpeg-armv7" --no-daemon`.
- Native FFmpeg `.so` libraries are *generated third-party build outputs*, not committed to the source ZIP. Preserve upstream PGP verification and license notices; do not replace with arbitrary libraries.
- For distributed APKs: ZIP align BEFORE final signing, verify APK Signature Scheme v2/v3 and Android API 34 against the final downloaded bytes; compare SHA-256 with the uploaded release asset. GitHub Actions build green is not evidence of installability on the receiver.

## User's full acceptance brief
**Mandatory implementation plan:** Read [IRAQhub_4.0.4_FULL_CODEX_PLAN_AR.md](IRAQhub_4.0.4_FULL_CODEX_PLAN_AR.md) in full. It is the detailed phased P0/P1/P2 engineering backlog, regression matrix, on-device acceptance checklist and definition of done. Keep it updated as tests prove results.\n\nRead **the Arabic section `تعليمات المستخدم المفصلة لـ Codex` in `CODEX_HANDOFF_4_0_4.md`**. It defines exact P0/P1/P2 requirements for real manual DVB TP scanning, HEVC 4:2:2 freeze/black screen, CHMAX logo identity/cache/CHL merge, EIT/XMLTV and sports posters/synopses, Tiger/Aurum InfoBar, RX professional UI/remote shortcuts, authorized BISS input, original OEM reverse engineering, USALS, channel management, ADB onboarding, and final-installable APK verification. Treat it as the user's current acceptance criteria; prioritize P0 fixes before cosmetic changes. Do not claim success without device evidence.

## Highest priorities
1. Fix manual DVB transponder scan returning `FIRMWARE_CAPTURE_NO_TS_DATA`, `NO_COMPLETE_PSI`, `services=0`: identify supported OEM capture interface or provide explicit diagnostic rather than fabricated services.
2. Independent 4:2:2 decoder continuous frames/no black screen, particularly HEVC 50fps; preserve audio, TS continuity/PSI/TEI diagnostics, account for X5M ARMv7 CPU load. Packet EAGAIN retry was added in 4.0.4; physical proof still absent.
3. Real native EIT NOW/NEXT under `DVB:<physical>` channel keys; FAST EPG, posters/synopses on both upper InfoBar sides including sports. Check cancellation, stale event races and CHMAX logo cache.
4. Lightweight Tiger black satellite-grouped channel list, green encrypted channels, 10-step horizontal navigation, wrap-around, alphabetical indexing, remote BLUE mini signal panel, PageDown BISS, PageUp FTA filter.
5. Preserve import/export and original satellite/EPG settings while consolidating redundant UI settings.

## Acceptance
New code needs tests and on-device evidence. Keep draft PR changes reviewable. No secrets/keys/SN or device credentials in commits. For Codex continuation, read CODEX_HANDOFF_4_0_4.md and inspect current git status before editing.
