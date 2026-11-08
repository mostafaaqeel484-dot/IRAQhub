# Native422 FFmpeg licensing and provenance

This experimental optional decoder uses the FFmpeg project, release **9.0.2**
(https://ffmpeg.org/download.html), built as separate shared libraries
`libavformat.so`, `libavcodec.so`, `libswscale.so`, `libavutil.so`.

These libraries are built with `--disable-gpl --disable-nonfree
--disable-version3` and linked dynamically. FFmpeg is licensed under the
**GNU Lesser General Public License 2.1 or later** for this configuration.
Refer to the FFmpeg source tree's COPYING.LGPLv2.1 and LICENSE.md for the
precise terms, and respect notices from any optional decoder components.

A distributor of an APK embedding the FFmpeg libraries MUST preserve applicable
FFmpeg copyright/license notices, make the corresponding source and build
instructions available, respect LGPL reverse-engineering and relinking rights,
and comply with any other packaged dependency licenses. Including this note
alone does not discharge those requirements.

Official tarball `ffmpeg-9.0.2.tar.xz` and signature are fetched over HTTPS
from ffmpeg.org and verified using the upstream release PGP fingerprint
`FCF986EA15E6E293A5644F10B4322F04D67658D8`. Rebuild using
`scripts/build-native422-ffmpeg.sh` and Android NDK 29.0.14206865.

**Do not redistribute the experimental CI artifact as a final release**
without device acceptance and a full third-party licensing review.
