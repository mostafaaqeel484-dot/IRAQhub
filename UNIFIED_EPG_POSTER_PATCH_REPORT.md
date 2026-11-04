# IRAQhub 3.6.2 — Unified EPG / Poster integration patch

## Base and scope
This patch is applied to the uploaded `IRAQhub-3.6.0-source.zip` source tree. It is **not** a source reconstruction of the separately uploaded 3.7.0 APK; the source and APK version numbers differ. Do not install this build over 3.7.0 until its source lineage/signing configuration is reconciled.

## Changes
- `OnlineEpgGuide.java`: adds a bounded refresh for the three feed IDs already mapped by this source (`AE1`, `ALJAZEERA1`, `BEIN1`); downloads use the existing `AtomicFile`, ETag/Last-Modified logic, 10 MiB compressed cap, and now reject non-GZIP payloads before replacing the prior file. Per-channel SAX parsing remains lazy.
- `BetterPostersProvider.java`: reads the supplied BetterPosters Stremio manifest dynamically, validates its identity, types, resources and catalogs, caches the manifest for four hours, allows a seven-day stale fallback, and gates `meta`/`bp-search` use on advertised capabilities.
- `IraqHubSyncWorker.java` and `IraqHubSyncScheduler.java`: add a unique WorkManager periodic job every four hours, requiring network connectivity, with exponential retry. A one-time initial sync is also queued on first launcher start. WorkManager persists its schedule across reboots.
- `LauncherActivity.java`: ensures the periodic job is registered on launcher start.
- `app/build.gradle`: adds WorkManager and increments this source branch to 3.6.2 / versionCode 25.

## Important scope boundaries
- This source branch currently maps only three EPGShare feeds and parses a requested channel on demand. It does **not** implement a full all-channel XMLTV database or a channel mapping UI/SQLite schema from the larger specification.
- `BroadcastEpgProvider` currently reads native EPG only through the receiver's loopback EPG_TDT protocol for its own supported DVB path, then falls back to mapped EPGShare feeds. It does not expose a generic raw DVB EIT/PAT/PMT reader.
- The patch refreshes the BetterPosters manifest and preserves the existing provider's standard `/meta` and `/catalog` calls. It does not add TMDB/TVmaze/TheSportsDB/Wikimedia providers; the source already has TMDB and sports-related code, while TVmaze/Wikimedia coverage is not proven by this patch.
- No claim is made that BetterPosters returns Arabic artwork for every title, or that all catalog requests return posters.
- WorkManager's four-hour interval is periodic and OS-managed; Android may defer execution under Doze/battery restrictions. It is not an exact wall-clock timer.
- No APK build or device acceptance test was run in this environment: Gradle/Android SDK are not installed here. The new code therefore remains source-level pending compilation and real receiver testing.

## Verification performed
- ZIP extraction and source-path checks.
- Confirmed existing EPG feed mapping, streaming SAX parser, native EPG provider, BetterPosters provider and existing TMDB integration.
- Confirmed new source references are internal to the same package.


## 4:2:2 compatibility and playback-efficiency continuation
- Added `VideoDecoderCompatibility`, which enumerates Android's regular video decoders and reports only explicit HEVC Main 4:2:2 10/12-bit and AVC High 4:2:2 profiles advertised by installed codecs. Software and hardware decoder claims are separated; missing capability data is not treated as support.
- Added codec-string hints for HEVC profile_idc 4/5/6/7 and AVC profile_idc 122. These identify a likely 4:2:2 stream profile only; they do not prove that the device can decode it.
- Added the 4:2:2 capability report and available video decoder list to Compatibility.
- Media3 IPTV playback now enables decoder fallback, disables extension renderers, removes the back buffer, and uses memory-aware buffer targets: 16 MiB / 8–24 seconds on low-memory devices, 32 MiB / 10–30 seconds otherwise. Playback and rebuffer thresholds remain 1.0 / 2.5 seconds.
- Media3 records a detected 4:2:2 codec profile and surfaces a decoder compatibility hint on decoder initialization/decoding errors.
- DVB playback remains on the receiver firmware decoder path; this app does not software-decode or re-encode the satellite video.
- Added unit test cases for HEVC Main 4:2:2 and AVC High 4:2:2 codec-string identification, including common 4:2:0 negative cases. The standalone codec-string parser was also compiled with `javac` stubs and its positive/negative cases passed.

## 4:2:2 validation boundary
Android's codec list reports only capabilities exposed to ordinary applications. Some TV firmware exposes the DVB hardware decoder only through its native playback service, not through Android `MediaCodecList`. Therefore a missing 4:2:2 profile in the app report is not proof that the receiver's native DVB path cannot decode it. Conversely, a profile listed by Android does not guarantee real-time decoding at every resolution, bitrate, frame rate, or bit depth. Acceptance must be measured on the target receiver using the actual channel/transport stream and decoder frame counters.


## IRAQhub 3.6.3 — runtime decoder diagnostics
- Bumped this source branch to versionName `3.6.3`, versionCode `26`.
- Added Media3 analytics hooks for selected input video format, decoder initialization/name, initialization duration, first rendered frame, frame-processing telemetry, and codec errors.
- The Compatibility report now displays the most recent IPTV decoder session, including decoder classification, input codec/profile, resolution, first-frame status, processed-frame samples, processing offsets, and the last codec error.
- Added explicit status stages: `PROFILE_DETECTED_NOT_YET_RENDERED`, `DECODER_INITIALIZED_PROFILE_UNVERIFIED`, and `FIRST_FRAME_RENDERED_PROFILE_NOT_BITSTREAM_VERIFIED`. These labels deliberately avoid claiming full 4:2:2 correctness based only on codec metadata or one rendered frame.
- Added unit tests for software/vendor decoder-name classification.
- Diagnostics are local SharedPreferences fields; no stream URLs, credentials, or channel names are copied into the report.

## 3.6.3 validation boundary
- Source-level edits and static checks only. This environment has no Android SDK/Gradle installation or cached Media3 1.5.1 artifacts, so the Android project could not be compiled here.
- No actual 4:2:2 transport stream was decoded on the target receiver. First-frame rendering is evidence of initial presentation, not proof of sustained 4:2:2 decode, correct chroma reconstruction, or stable playback under load.
- Final acceptance requires a real 4:2:2 sample/channel on the receiver and comparison of decoder name, frame counters, playback duration, dropped frames, and errors.


## 3.6.4 playback stability telemetry
- Playback telemetry is reset per player activity session.
- Dropped video frames and drop-event batches are counted through Media3 AnalyticsListener.
- Compatibility report includes total/average processing offset and session start.
- These values are software playback telemetry, not proof of native hardware decoder internals.
