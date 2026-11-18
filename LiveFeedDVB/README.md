# LiveFeed DVB — Android TV 1.1.0

A standalone native Android TV receiver companion (Android 8.0–14+; no NDK; works on 32-bit ARM), **not** a modification of IRAQhub.

- Native Arabic, focusable TV UI, grid of feed cards, filters (on-air, 4:2:2, favorites), searchable frequencies
- Multi-source feed aggregation: Live-Feed.net + Satelliweb live sightings. Normalize satellite orbital slot (E/W), frequency, polarization and SR to collapse duplicates across sites. Retain image from the primary site and display all contributing sources.
- Satelliweb sightings are explicitly labeled **Reported**, not confirmed ON AIR. They are fetched at most every two minutes; the primary feed continues to be polled every 20s while the foreground monitor is on. If one source fails, the other can still populate the list.
- Source terms/permissions: Satelliweb textual sightings may be subject to its website terms. This is an unofficial private-use prototype; seek the publisher\'s consent before redistributing their content in a public/commercial application. No Satelliweb images are copied.
- Satellite / frequency / polarization / symbol rate / codec / chroma information collected from https://live-feed.net/?show_off=1
- Feed snapshots are fetched from source image URLs when available and cached on disk
- New-feed notifications (in-app 20s polling; Android JobScheduler background fallback *at least* 15 minutes, subject to system scheduling)
- Opt-in floating TV alert banner with image, satellite and transponder: enable **▣ عائم** and grant **Display over other apps**. An explicitly enabled foreground service polls every 20s, with a persistent notification and a Stop action; each new-feed banner disappears after 8 seconds; long-press the toggle for an example banner. Android/OEM restrictions may block overlays on secure video surfaces.
- Root-cause fixed: timestamp regex extraction previously threw IndexOutOfBoundsException for every card. Site fetch now falls back to the active list and failure messages show useful details.
- Cold start and upgrades to transponder-based dedupe set a notification baseline to avoid false floods, including duplicate notifications from different websites
- Can copy a transponder and launch IRAQhub for manual DVB tuning; **does not** control tuner or auto-tune
- No BISS keys are stored or included
- No API is assumed: the HTML parser is best-effort, and may need adjustments if the third-party site changes markup; data is not guaranteed in real time

## Build

```sh
gradle -p LiveFeedDVB :app:assembleDebug --no-daemon
```

Android SDK 35 and JDK 17 required. Output: `LiveFeedDVB/app/build/outputs/apk/debug/app-debug.apk`.

Permission `POST_NOTIFICATIONS` must be granted on Android 13+. Android power management may delay background jobs; prompt push notifications require an official server-side API / push provider. 20-second polling requires the foreground monitor to stay running and an active network connection. Android/OEM battery restrictions or site rate limits can delay checks; periodic JobScheduler fallback cannot promise 20 seconds. The app is unofficial and is not associated with live-feed.net.
