# RX7 7.1.0 · Native 4:2:2 compatibility (X5M ARMv7)

## What this experiment actually does

An app-owned DVB MPEG-TS stream from LiveTsRelay is decoded by the
official FFmpeg 9.0.2 libavformat / libavcodec shared libraries.
libswscale converts each decoded frame to 800 x 450 YUV420P 8-bit,
then to RGBA for Android ANativeWindow. This does NOT re-encode H.264
and does not use an Android MediaCodec hardware encoder. The original
H.264/HEVC source is still software decoded at its broadcast resolution.

The engine runs in an isolated :decoder422 process, without another
tuner. It starts on explicit 4:2:2 playback only; normal 4:2:0 stays
on the OEM decoder. Safe Mode still forces OEM playback. Failure can
fall back once to libmpv on the same authorized TS source. Missing
MPEG-TS bytes, scrambled packets and unavailable OEM capture APIs
cannot be corrected by a decoder.

## FFmpeg source, licensing and rebuild

Version FFmpeg 9.0.2. Official source:
https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz
The source plus detached signature are PGP-verified using pinned key
fingerprint FCF986EA15E6E293A5644F10B4322F04D67658D8.

Rebuild: scripts/build-native422-ffmpeg.sh
Toolchain: Android NDK 29.0.14206865, ABI armeabi-v7a.
Configured LGPL only: disable-gpl, disable-nonfree, disable-version3.
Enabled required H.264/HEVC decoders, MPEG-TS demuxer, and the four
dynamic libraries libavformat, libavcodec, libavutil and libswscale.

LGPL copyright and license notices are bundled under
assets/third_party/ffmpeg inside the experimental APK.
FFmpeg is dynamically linked. Build scripts and JNI sources in this
repository enable replacing the shipped FFmpeg binaries.

## Device test protocol

CI success demonstrates packaging, signature and unit tests, not
real-world playback. Try a known free-to-air 4:2:2 source, check the
tuner lock and nonzero software_ts_capture_bytes, and monitor
native422_rendered_frames and native422_fps_x100 for at least five
minutes. Confirm any one-shot MPV fallback. A 0-byte TS capture must
be diagnosed separately from a decoder stall. No real X5M video
performance guarantee is made.
