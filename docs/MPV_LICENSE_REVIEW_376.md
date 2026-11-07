# IRAQhub 3.7.6 — bundled libmpv / FFmpeg license review (ACTION REQUIRED)

This PR adds a real Android Maven AAR: `dev.jdtech.mpv:libmpv:1.0.0`.
The published POM advertises MIT for the **Android wrapper**. This is **not
the license of the entire native media stack**.

Evidence from the upstream exact `v1.0.0` build scripts:

- https://github.com/jarnedemeulemeester/libmpv-android/blob/v1.0.0/buildscripts/scripts/ffmpeg.sh
- The native FFmpeg configure line includes
  `--disable-static --enable-shared --enable-{gpl,version3}`.
- https://github.com/jarnedemeulemeester/libmpv-android/blob/v1.0.0/buildscripts/scripts/mpv.sh
- The native mpv script builds a shared `libmpv.so`. The combined software
  may be subject to GPLv3 obligations. Review **all** linked libraries.
- The upstream mpv notice: https://github.com/mpv-player/mpv/blob/master/Copyright

For an internal, personal Android TV test the user can evaluate the feature.
**Do not publish a broad public APK with this embedded native binary without
verifying the exact binary's licenses, retaining notices and providing the
required corresponding source/buildup/relinkability (or moving to a proven
LGPL-only binary).** This memo is not legal advice.

### Distribution decision for this PR

- CI saves the APKs to a **private Draft Release** in the owner's repo; it
  never automatically makes the GPL-containing AAR build a public release.
- Debug APK is ephemeral CI-signed; production Release is unsigned unless
  `IRAQHUB_RELEASE_KEYSTORE_BASE64`, `IRAQHUB_RELEASE_KEY_ALIAS`,
  `IRAQHUB_RELEASE_STORE_PASSWORD`, and `IRAQHUB_RELEASE_KEY_PASSWORD`
  are configured by the owner in GitHub Actions secrets.
- No signing key material may be committed to this repository.
- Real X5M High422/HEVC 4:2:2 display & audio tests are still mandatory.
- Android 14 targetSdk remains 34; compileSdk 36 upgrade is solely for the
  libmpv AAR's metadata requirement.
