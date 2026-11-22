# RX7 7.2.0 experimental ExtePlayer3-Android PTS adaptation

This build does **not** contain a complete, unchanged ExtePlayer3 Enigma2
binary. Its actual upstream component is
`app/src/main/cpp/vendor/exteplayer3/pts_clock.h` from
https://github.com/skyjet18/exteplayer3. It provides the MPEG-TS
90 kHz PTS wrap/discontinuity mapping. The clock is called on every
decoded frame by `exteplayer3_android_clock.h` and the Android JNI
FFmpeg decoder. The renderer uses `ANativeWindow`, not the unavailable
Linux `/dev/dvb/adapter0/video0` hardware device.

User-selectable playback modes: original (up to 1920×1080 pixels,
still software-decoded from the transmitted H.264/HEVC bitstream) or
800×450, converted to 8-bit YUV420P and RGBA Surface. Interlaced input
is passed through; a complete 50i field-rate deinterlacer is NOT
implemented in this first adaptation.

MPEG-TS radio and audio streams are probed from the actual PMT.
FFmpeg software decodes MP2/MP3/AAC/AC3/EAC3 with libswresample
into 48 kHz S16 stereo Android AudioTrack. Audio PTS drift/lip-sync
is not yet verified by the X5M; a stable A/V sync meter cannot be
claimed from decoder timestamps alone. Yellow/AUDIO selects real
stream indices. GREEN switches original/450 output (same TS relay).
INFO/OK shows the HUD, 0 pins it, EXIT/BACK quits. The statistics
screen reports actual captured TS, decoded/displayed frame progression
and drops; absent readings remain unavailable.

The app attempts this genuine partial ExtePlayer3-PTS Android port
first **only for explicitly selected 4:2:2 channels**. Ordinary 4:2:0
DVB channels still use firmware, Safe Mode remains OEM, and a stalled
JNI video may fall back to MPV on the same TS. A missing or scrambled
TS source is not repaired by changing software decoders.

License: The actual upstream ExtePlayer3 PTS source is covered by
GPL-2.0-or-later; the full license text and attribution are included
in the APK under assets/third_party/exteplayer3. The rest of FFmpeg
is PGP-verified upstream 9.0.2, compiled LGPL as 32-bit ARMv7 shared
libraries. Corresponding source/scripts are in this public repo.

Acceptance pending: physical X5M MPEG-TS capture, audible dual audio
tracks, 1080i50 picture motion, stable FPS and A/V sync, correct
deinterlace, 800×450 output, key mapping, native library load, and
one-shot MPV fallback. CI checks prove build/signature, not playback.
