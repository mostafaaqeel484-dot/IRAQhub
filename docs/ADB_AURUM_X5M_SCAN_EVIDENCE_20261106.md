# ADB evidence — Aurum native satellite search, Android 14

Source: owner-provided `IRAQhub_scan_probe_20261106_202525.zip`, nine captured stages, 2026-11-06. This report deliberately excludes any serials, authentication data, or raw logs.

## Native screen fingerprints and results

| Capture | Evidence |
| --- | --- |
| 01 install-menu | Aurum home/no-video fallback was active; not an installation fragment in this frame |
| 02 manual options | `NavHostActivity`, title **البحث اليدوي**, satellite Nilesat (7.0W), RF 11013/H/27500; mode **إضافة**, preserve custom names **إيقاف**, services **جميع القنوات**, network scan entry and strength/quality |
| 03 manual result | `NavHostActivity`, native `tv_tips=انتهاء المسح وحفظ القنوات`, 7 television + 1 radio, 3 FTA, 5 scrambled, 7 HD, total 8; this is an OEM completed scan saved by Aurum, *not* an IRAQhub import |
| 04 results/home | Aurum home; no XML service list present |
| 05 fast options | `NavHostActivity`, **فحص سريع**, shown provider nC+ HD, Hotbird (13.0E), 10719/V/27500, add mode, custom name preservation off |
| 06 fast results | Native progress view showed 100% and zero TV/radio services for the captured attempt; the log window also contains `gs_start_fast_scan` and `providerIndex`, but log entries must not be assumed to correspond precisely to the screenshot settings |
| 07 fast results | 100%, zero TV/radio in screenshot |
| 08 antenna/auto options | Nilesat (7.0W), 11013/H/27500, Universal(9750/10600), 13/18V, DiSEqC 1.0/1.1, 22 kHz Auto and USALS visible |
| 09 scan tools/blind | Native progress 34%, 31 detected transponders and 3 new transponders listed; `dvbstack` log shows repeated `FE_GET_BLINDSCAN tp_num` polling (not an accessible public API) |

**Other observed information:** Device reported Android API 34; the Aurum APK is installed under `/vendor/app/aurum/aurum.apk`. The service list includes Android TV input services. The foreground search UI is `com.tosmart.aurum/com.tosmart.dvb.common.NavHostActivity`.

## Reproducible XML view IDs

The native **manual-result** screen exposes:

- `header_title`, `tv_tips`, `channel_all_total_count`
- `tv_detail_title`, `tv_radio_title`
- Repeated **`tv_search_result_title` + `tv_search_result_info` pairs**, in TV and radio panes, for total / free / HD / encrypted.

The native **manual/fast options** screens expose repeated `name` / `value` rows, `sb_strength`, `sb_quality`, `tv_percent`, and `help_bar`. Their fields are not automatically meaningful commands; no privileged operation should be triggered from a read-only UI observation.

## What was implemented in IRAQhub

- Pure-Java `AurumScanSummary` validates the TV+radio result display and declines partial or inconsistent totals.
- Opt-in `AurumScanObserver` in the existing channel Accessibility bridge records only the visible **completed** result counts; it does not harvest protected channel records, touch the remote, or issue tuning/motor commands.
- `ReceiverToolsActivity` shows the last observed result after returning to IRAQhub and has a transparent handoff to Aurum's **native navigation** (`com.tosmart.action.NavHost`) when the firmware permits it. The user still navigates to the native Installation menu.
- Local IRAQhub manual tuning, saved-TP sweep, and TS service import remain separate and are not misrepresented as native blind or fast scans.

## Access and correctness gates

The OEM declares `com.dvb.service.permission.WRITE_CONFIG` and `com.tosmart.dvb.common.permission.READ_PROGRAM_INDEX` as **signature** permissions. Neither Android 14 nor successfully capturing its UI via ADB grants an unrelated APK those privileges.

**Not proven by this archive:** A callable non-privileged binder/AIDL/interface for `SearchManager2` service discovery and channel persistence, live full-blind/manual/fast scanning by IRAQhub itself, safe motor control or direct write access to the Aurum program database.

A genuine independent port still needs an authorised OEM SDK/bridge or successful runtime validation of a documented non-privileged interface; the `NavHost` intent is an optional OEM UI handoff, not an independent scan implementation. Do not silently replace a user's OEM channel list or move the dish.
