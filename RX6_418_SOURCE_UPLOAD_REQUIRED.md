# RX6 4.0.18 — exact-source APK build stage (NOT built)

## Confirmed blocker — 2026-11-16
This branch is still based on RX6 **4.0.14** code, **not** the 4.0.18 attachment. Its GitHub Actions run count is zero. Do not label any APK from the current state RX6 4.0.18 or Turbo.

## Prepared source ZIP, locally verified
File name: `IRAQhub_RX6_4.0.18_GitHub_Build_Fix_Source.zip`
SHA-256: `e86f09e627b01f6dfec1ce6de061091304111100c22b39f1e40d377739187ee6`
Includes exact 4.0.18 source plus **build metadata only** fixes: preview suffix updated from 4.0.14, and debug preview application ID changed to `com.smarthub.plus.rx6api418` to reduce install signature collision with old preview.

Uploader: `IRAQhub_RX6_418_GitHub_BuildFix_Upload.sh` (from the current ChatGPT conversation artifact). Upload **all extracted files** to this branch. The push will trigger `.github/workflows/rx6-418-exact-source-build.yml`. It validates source version, Java tests, Android API 34, ARMv7 native libs and APK signature; the result is a debug-signed **side-by-side** APK in draft release.

**Not a Turbo 4.0.19 implementation**. No compiled 4.0.18 APK is available yet and physical receiver tests of manual search, EPG and 4:2:2 are still outstanding.

Never upload signing secrets or keystores in source commits.
