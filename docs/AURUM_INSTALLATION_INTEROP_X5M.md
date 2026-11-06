# Aurum installation interoperability map (Amlogic X5M, Android SDK 34)

## Evidence obtained
- The original `com.tosmart.aurum` APK declares `android:sharedUserId="android.uid.system"`; IRAQhub `com.smarthub.plus` is **not** signed with the vendor/system signature.
- The native installation navigation graph is `nav_installation.xml`, starting at `com.tosmart.dvb.common.installation.SearchChannelFragment`.
- Additional fragment names in the original application: `SatelliteAntennaFragment`, `SatelliteMotorFragment`, `SatelliteFinderFragment`, and `EditPidFragment`.
- The extracted resource set includes satellite manual/automatic/blind/fast scan, transponder, antenna, DiSEqC/USALS and motor layouts.
- The original APK includes `com.dvb.tuner.TunerManager` methods named `setDvbSTunerColumn`, `dvbSendMotorCMD`, `getMotorMoveTime`, `getLNBFreqSelect`, `getCurTunerIndex`; presence of methods **does not prove** ordinary app access to privileged firmware.
- IRAQhub already implements a local satellite/transponder editor (`InstallationEditorActivity`), motor/USALS interface (`MotorCenterActivity`), and a manual tuning workflow. These must not be described as editing the OEM database when they only edit IRAQhub state.

## Required on-device read-only probe (ADB)
ADB device: `192.168.0.214:5556` (connected from owner's Termux, not from ChatGPT).
1. Use `dumpsys package com.tosmart.aurum` to inspect exported activities and permissions.
2. With the native installation screen open, collect `uiautomator dump` + `screencap` for satellite list, LNB, DiSEqC, transponder editor, manual/auto/blind scan, and USALS.
3. Record foreground activity and relevant service names. Do not pull protected app data, credentials, or alter firmware.
4. Determine whether any documented/exported native installation entry point or accessible tuner API is actually callable from a normal application. Only after verification, expose it in IRAQhub.
5. Backup the receiver channel list and confirm dish limits before any **active** motor/tune tests.

## Safety and completion criteria
- Never silently substitute a local-only edit for a firmware edit.
- Never issue motor commands from a read-only inspection workflow.
- Never assume that copying DEX/SO code or `sharedUserId` grants OEM signature privileges.
- A feature is "fully ported" only after live device tests prove persistent firmware settings, correct LNB/DiSEqC/tune parameters, scan progress, channel persistence, motor stop, and safe error handling.
