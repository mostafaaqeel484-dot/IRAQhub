#!/usr/bin/env bash
# Verified official FFmpeg release cross-compiled for X5M Android ARMv7.
# No GPL/nonfree modules. This script is intentionally NOT part of default APK.
set -euo pipefail
VERSION=9.0.2
KEY_FINGERPRINT=FCF986EA15E6E293A5644F10B4322F04D67658D8
: "${ANDROID_HOME:?$ANDROID_HOME must contain pinned NDK 29.0.14206865}"
NDK="$ANDROID_HOME/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -x "$NDK/armv7a-linux-androideabi26-clang" ]] || {
 echo "Install official Android NDK 29.0.14206865" >&2;exit 2;
}
DEST="${1:-$PWD/.native422/ffmpeg-armv7}"
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
BASE="https://ffmpeg.org/releases/ffmpeg-${VERSION}.tar.xz"
curl --fail --location --retry 5 --retry-delay 5 -o source.tar.xz "$BASE"
curl --fail --location --retry 5 --retry-delay 5 -o source.tar.xz.asc "$BASE.asc"
curl --fail --location --retry 5 --retry-delay 5 -o ffmpeg-release-key.asc \
 https://ffmpeg.org/ffmpeg-devel.asc
export GNUPGHOME="$TMP/gnupg"
mkdir -m 700 "$GNUPGHOME"
gpg --batch --import ffmpeg-release-key.asc
ACTUAL="$(gpg --batch --with-colons --show-keys --fingerprint ffmpeg-release-key.asc |
 awk -F: '$1=="fpr" {print $10;exit}')"
[[ "$ACTUAL" == "$KEY_FINGERPRINT" ]] || {
 echo "FFmpeg release key fingerprint mismatch: $ACTUAL" >&2;exit 4;
}
gpg --batch --verify source.tar.xz.asc source.tar.xz
tar -xJf source.tar.xz
cd "ffmpeg-${VERSION}"
export PATH="$NDK:$PATH"
./configure \
 --prefix="$DEST" --target-os=android --arch=arm --cpu=armv7-a \
 --enable-cross-compile --cc=armv7a-linux-androideabi26-clang \
 --cxx=armv7a-linux-androideabi26-clang++ \
 --ar=llvm-ar --ranlib=llvm-ranlib --nm=llvm-nm --strip=llvm-strip \
 --enable-pic --enable-shared --disable-static --disable-symver \
 --disable-debug --disable-doc --disable-programs --disable-autodetect \
 --disable-gpl --disable-nonfree --disable-version3 \
 --disable-everything --enable-avcodec --enable-avformat --enable-avutil \
 --enable-swscale --enable-network --enable-pthreads --enable-neon \
 --enable-demuxer=mpegts --enable-decoder=hevc,h264,mpeg2video,mpeg4 \
 --enable-parser=hevc,h264,mpegvideo,mpeg4video \
 --enable-protocol=http,tcp,file \
 --extra-cflags="-O2 -fPIC -march=armv7-a -mfpu=neon" \
 --extra-ldflags="-Wl,-z,relro,-z,now"
make -j "$(nproc)"
make install
# Only the 4 LGPL runtime libraries required by the direct decoder.
for name in avformat avcodec swscale avutil; do
 [[ -s "$DEST/lib/lib${name}.so" ]] || {
  echo "FFmpeg missing lib${name}.so" >&2;exit 5;
 }
 file "$DEST/lib/lib${name}.so"
 llvm-readelf -h "$DEST/lib/lib${name}.so" | grep -q 'Class:.*ELF32'
 llvm-readelf -h "$DEST/lib/lib${name}.so" | grep -q 'Machine:.*ARM'
done
echo "Verified FFmpeg 9.0.2 ARMv7 native libraries: $DEST"
echo "Keep FFmpeg copyright, license notices and share source/build scripts."
