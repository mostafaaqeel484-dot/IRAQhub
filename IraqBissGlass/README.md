# IraqBISS Glass TV · v0.2 beta

Standalone Android TV satellite feed **metadata** aggregator built for 32-bit Android TV receivers (including Amlogic X5M Android 14). Remote/D-pad UI and translucent glass-style cards.

- Built-in sources: live-feed.net, Satelliweb, public Telegram previews (feed_hunter, livefeeds1) when accessible.
- Optional extra HTTPS RSS/Atom feeds, local cached data, duplicate frequency merging.
- Filters: all, sports, 4:2:2, FTA, favorites. Copy transponder information.
- Pop-up glass notifications: two independent top/bottom overlays for ~10 s; Android system notifications for newly reported feeds. Both require device permissions, and OEM firmware can restrict overlays.
- Source diagnostics, manual refresh, remote navigable menus.
- First sync seeds seen-feed cache and does **not** trigger a flood of alerts. Feed sightings are not verified DVB signals.
- App uses Android APIs only, no native .so, compatible with 32-bit/armeabi-v7a.

## Build / install

Go to **Actions > IraqBISS Glass TV APK** on the feature branch. Download artifact `IraqBISS-Glass-TV-APK`, or download the prerelease asset `IraqBISS-Glass-TV-v0.2.apk`.

```sh
adb install -r IraqBISS-Glass-TV-v0.2.apk
```

Optional overlays need **Settings > Display over other apps > IraqBISS Glass > Allow**, then enable inside the app. Android 13+ notification permission is requested if applicable.

Not a DVB tuner, stream player or BISS decryption tool. The original IRAQhub launcher is untouched: this is a standalone project in `IraqBissGlass/`.

Sites may alter layout or rate-limit; adapters are best effort. Respect source terms; add custom permitted RSS sources.
