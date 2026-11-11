# IRAQhub 3.7.0 RX

This source package is based on the Phase 11 RX launcher source tree.

## Included
- RX launcher integration and receiver navigation.
- Receiver playback and DVB bridge classes.
- Unified EPG and poster providers present in the source tree.
- Media3 playback for supported streams.
- LibVLC 3.7.0 software playback path for the software decoder activity.
- GitHub Actions workflow for unit tests, debug/release APK builds, and artifact upload.

## Version
- applicationId: `com.smarthub.plus`
- versionCode: `29`
- versionName: `3.7.0`
- compileSdk: `35`
- minSdk: `26`

## Build
Push this source tree to the `main` branch of the IRAQhub GitHub repository, or upload it as the repository contents. Then open **Actions → Android CI**. The workflow runs unit tests, builds debug and release APKs, and uploads both APKs as an artifact.

## Important limitations
- This is source packaging, not a prebuilt APK. GitHub Actions must complete successfully before an APK is available.
- Hardware-specific RX / DVB / 4:2:2 playback must still be validated on the target receiver.
- Experimental Time Shift capture is intentionally not wired into the launcher.
