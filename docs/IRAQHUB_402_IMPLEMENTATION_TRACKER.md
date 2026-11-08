# IRAQhub 4.0.2 — implementation tracker (X5M)

Status: **alpha development, not hardware-verified**. Based on 4.0.1 RC2. Do not replace a working launcher with this preview until device tests pass.

## Logo provided by owner

Owner supplied a 1536×1536 IRAQHUB+ black-background logo with satellite dish, Iraqi flag ribbon, red/green player and 4:2:2 badge. This is the authoritative artwork. **Binary image not yet committed**: GitHub text-file connector cannot upload binary assets. Local Android resource pack prepared separately; do not substitute the old cyan play icon in release. To integrate: put supplied image into drawable-nodpi/iraqhub_logo_original.png, generated density mipmap ic_launcher.png assets, and 640×360 tv_banner.png; change Manifest icon/banner references; remove conflicting old XML drawables.

## Priorities and acceptance criteria

1. **4:2:2**: inspect native TS bytes and continuity/PIDs before switching decoder. Keep existing MPV/VLC as fallback, no blanket library deletion. Measure input bitrate, first-frame time, frame progression, dropped frames and CPU for 1080p422 and 720p420 on X5M. No success claim until real moving video on HDMI.
2. **Posters**: restore cached logo/poster lookup, distinguish EPG-missing from artwork-missing and API failures, avoid stale results after channel switch.
3. **InfoBar**: flush against bottom screen edge, compact height, date/year typography, signal S/Q, CA systems read from actual broadcast descriptors; highlight multiple active systems such as Nagra2 and Betacrypt, never infer encryption from name alone.
4. **Channel list**: signal strip at bottom; yellow button opens A-Z selector; remove search bar and redundant lower boxes, keep D-pad focus/OK/back semantics.
5. **Remote**: numeric channel entry with bounded timeout and cancel/back; volume OSD tied to real AudioManager and hardware volume, not fake volume value.
6. **Front panel**: discover vendor-specific services/settings and guard with capability checks; no promise of universal support.
7. **Typography**: Thmanyah Sans primary and Serif Display headings only. License allows packaged app embedding but prohibits independently extractable raw font distribution; keep raw OTF out of public GitHub and downloadable assets until compliant packaging or rights confirmation.
8. **Validation**: Android 14 API 34, armeabi-v7a, 3.6GB RAM, X5M; compile assembleX5mPreview, install side-by-side, inspect logs and TV screen, verify rollback.

## Release gate

No stable 4.0.2 or GitHub pre-release until APK built, installed, and at least basic DVB playback/navigation validated. Draft PR is development only.
