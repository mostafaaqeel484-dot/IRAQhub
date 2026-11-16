# IRAQhub RX6 4.0.18 — build staging (not an APK yet)

This branch was created from RX6 **4.0.14** only as a secure staging point, because the latest **4.0.18** full ZIP is a ChatGPT conversation attachment and is not available to the GitHub connector as uploadable bytes.

**Do not build or label this branch as RX6 4.0.18 until its entire source has been uploaded.** The CI source gate deliberately rejects 4.0.14 and checks for 4.0.18, the LiveFeed activity, remote control policy, artwork cache and bundled country flags.

Required ZIP: `IRAQhub_RX6_4.0.18_Exact_Source_CI_Build_Kit.zip`
Expected ZIP SHA-256: `e5ef5e8923dea7592b8be1f2f8eb9745ae8195a2c867a6138321f40dfd54b522`

Upload **all extracted ZIP files** to this branch (including hidden `.github` files), not the ZIP itself. After uploading, pushing the source to `build/rx6-418-exact-source` automatically triggers `IRAQhub RX6 4.0.18 — Exact Source X5M APK`.

The resulting preview is side-by-side **DEBUG**-signed `com.smarthub.plus.rx6api234`, not a production upgrade. A successful build is **not** validation of DVB scan or continuous 4:2:2 playback on X5M hardware.
