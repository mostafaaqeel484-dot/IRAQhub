# IRAQhub 3.7.4 RX Edition — UI-only visual refresh

**Source:** user-provided `RX_Launcher_v2.39_UI_Full.zip` and reference contact sheet.
This design follows the extracted RX glass/dark/light palette, horizontal cards,
D-Pad emphasis and satellite live-TV artwork. The original RX Home layout was
drawn programmatically, so this is a native adaptation, **not an exact binary
copy of RX Launcher**.

## What changed

| UI surface | Implementation | Business behavior |
|---|---|---|
| Home | `LauncherActivity` + `RxSkin` — themed background, glass navigation tiles, highlights, theme shortcut | Original click handlers, app pins, channel display and clock remain |
| Settings and internal pages | `HubActivity` common page, button, input and card surfaces | Existing intents, button callbacks and data fields preserved |
| InfoBar | `NowNextView` — dark glass, translucent poster cards, focus accents and technical badges | Original current/next poster, EPG, tuner, channel and provider data bindings preserved |
| Channel lists | `ReceiverLibraryActivity` + `ReceiverPanelStyle` — glass panel, searchable row states, transparent backdrop over video | DVB/Aurum modes, CH+/CH−, favourites, BISS, sorting, manual scan and signal bars unchanged |
| Wallpaper picker | `RxAppearanceActivity` | App-owned preferences only |

## Available offline wallpapers

RX Dark, RX Light, Iraq, Manchester United, Linux, Real Madrid, Al Ahly,
Egypt, Mountains, Space, Sunset and Minimal Black. All wallpaper imagery in
`RxSkin.Wallpaper` is **original procedural artwork rendered on Canvas** —
not official club photography or licensed crests. There is no external
network request or 4K background video.

## Controls

Settings → Appearance → RX Edition. Choose a wallpaper with the TV remote,
enable optional daily wallpaper rotation and translucent glass. The selected
accent also changes the InfoBar color, including EPG progress, without
changing program data.

## Preserved receiver functionality

**No modification** was made to `DirectTransponderScanner`,
`DvbPlaybackActivity`, `Media3PlayerActivity`, `SoftwareVideoActivity`,
`LiveTsRelay`, `CHMAXIntegration`, `MatchRepository`, EPG worker classes,
`ReceiverCatalog`, receiver DB import or OEM interfaces.

The existing 4:2:2 and DVB/manual scan restrictions remain. This is a
cosmetic-only release, **not a hardware compatibility fix**.

## Hardware checklist before replacing HOME

- Verify RX Home at 1920×1080 and 1280×720 with RTL/English and D-Pad focus.
- Open current/next Infobar over a live satellite channel, check EPG/posters and visible video behind channel list.
- Verify side/wide channel list, GUIDE/SEARCH/MENU, favourites, BISS and manual-scan navigation.
- Return from Settings to Home and confirm wallpaper and focus accent update.
- Verify no animated background decoder competes with 4:2:2.
- Keep APK backup; Debug APK is signed by a per-CI debug key and may not install over an existing signed APK.
- Release APK is unsigned and **not installable until developer signing**.

Version metadata: 3.7.4 / versionCode 33. Production merge requires TV acceptance.
