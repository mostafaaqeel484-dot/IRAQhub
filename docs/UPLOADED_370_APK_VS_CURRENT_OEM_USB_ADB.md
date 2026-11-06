# IRAQhub uploaded APK vs current X5M source — 2026-11-06

## Evidence examined

- Owner-uploaded file: `com.iraqhub.plus_3.7.0.apk` (175 MiB as transferred).
- Parsed its **binary AndroidManifest.xml** and all four classes*.dex files.
- Actual package/application ID: **`com.smarthub.plus`** (despite the filename).
- APK version: **3.7.0 / versionCode 24**, debuggable, min SDK 26, target SDK 34.
- Current source on this branch: `app/build.gradle` **versionCode 29**, same `com.smarthub.plus` and target SDK 34.
- APK embeds 32-bit and 64-bit LibVLC and other image/translation libraries; do not copy native libraries over newer versions without ABI/behavior verification.
- Static DEX confirms original `ManufacturerToolsActivity` has its **own onCreate/load code** and `ReceiverToolsActivity` has a `nativeAction` pathway. Newer pre-fix `ManufacturerToolsActivity` was a redirect to `ReceiverToolsActivity`, concealing the OEM tools page.
- `ManufacturerFeatures` is present in the APK, with method names `read`, `host`, `find`, `open`, `unavailable`, `navigateSidebar`, `hasFragment`, and `parseBundle`. Its corresponding source has been retained in the project.
- The uploaded APK has `DvbTuner.ensure`, `DvbTuner.read`, `DvbTuner.stopMotor`, and `DvbTuner.usalsEnabled`, **but no separate ManualTransponderActivity / InstallationEditorActivity / TransponderSweepActivity**. Those were introduced in newer project revisions.
- The old APK **does not** contain the vendor's `SearchManager2` class; it is an IRAQhub application, not the firmware `com.tosmart.aurum` source APK.
- Newer code adds the app-owned manual tune, saved-transponder sweep, TS PAT/PMT/SDT import and InfoBar artwork pipeline. These should remain separate from the true OEM service-scanner.

## Real OEM route reused from the old application

Aurum's published `ManufacturerFeatures.host(false, "Search_Channel", 1, 0)` produces:

- Intent action `com.tosmart.action.NavHost`
- Package `com.tosmart.aurum`
- Boolean extras `from_home=true` and `from_home_as_app=true`
- String `host_type=Search_Channel`
- Integer `sub_host_type=1`
- Integer `enter_type=0`

This is more specific than launching `NavHostActivity` blindly or sending a generic `NavHost` action. The new navigation tries this **published, permission-checked** OEM action first, then the explicit captured activity and regular/Leanback launcher. It opens **the original menu only**; it does not start scanning or persist channels.

## What was ported, and what was not

- Restored a separate `ManufacturerToolsActivity`, using `ManufacturerFeatures.find/unavailable/open` to show actual exported firmware satellite-search, LNB/USALS, TV/radio editing, favourites, sorting and channel-backup routes. Every denied route shows a status, rather than having an inert button.
- Added an original-firmware tools tile to the satellite setup screen; kept the requested four top-level IRAQhub installation choices.
- Added the observed OEM route and error to the developer diagnostic screen.
- Preserved `ManualTransponderActivity` as **real tuner lock** and `TsServiceImportActivity` as MPEG-TS parsing, not a falsely advertised full channel scan.
- **Not ported:** `SearchManager2` proprietary implementation, privileged `WRITE_CONFIG`, direct writes to Aurum channel database, automatic blind/fast scanning from a normal Android app, motor movement.

## ADB verification needed on the owner's X5M

Run the companion `IRAQhub_USB_ADB_Installation_Probe.zip` locally in Termux (ADB serial `192.168.0.214:5556`) or a USB-connected PC. It captures OEM activity resolution, Android TV XML/PNG screens and, optionally, a copy of **only** the installed Aurum APK. It does not use root or access `/data/ChannelImgs`.

One-shot menu entry from the user's own ADB host:

```sh
adb -s 192.168.0.214:5556 shell am start \
 -a com.tosmart.action.NavHost -p com.tosmart.aurum \
 --ez from_home true --ez from_home_as_app true \
 --es host_type Search_Channel --ei sub_host_type 1 --ei enter_type 0
```

**Safety:** ADB shell launching an OEM activity does not prove IRAQhub's normal UID can open it. Check in-app route status and Android permissions independently. Do not invoke firmware writes or motor commands merely because ADB debugging is enabled. Back up the channel database before any *user-operated* scan.
