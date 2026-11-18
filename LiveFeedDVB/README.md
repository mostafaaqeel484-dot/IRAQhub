# LiveFeed DVB — Android TV 1.2.1

A standalone native Android TV receiver companion (Android 8.0–14+; no NDK; works on 32-bit ARM), **not** a modification of IRAQhub.

- Native Arabic, focusable TV UI, grid of feed cards, filters (on-air, 4:2:2, favorites), searchable frequencies
- Multi-source feed aggregation: Live-Feed.net + Satelliweb sightings + optional Telegram Ben 10 feeds public preview. Normalize satellite orbital slot (E/W), frequency, polarization and SR to collapse duplicates across sites. Retain image from the primary site and display all contributing sources.
- Satelliweb sightings are explicitly labeled **Reported**, not confirmed ON AIR. Satelliweb is refreshed at most once per 60 seconds; the Telegram public channel preview at most every 15 seconds (if accessible). The primary feed is polled as often as every 5 seconds while the foreground monitor is running. Source rate limits and slow networks can delay these intervals; failed sources leave others available.
- Source terms/permissions: Satelliweb textual sightings may be subject to its website terms. This is an unofficial private-use prototype; seek the publisher\'s consent before redistributing their content in a public/commercial application. No Satelliweb images are copied.
- Satellite / frequency / polarization / symbol rate / codec / chroma information collected from https://live-feed.net/?show_off=1
- Feed snapshots are fetched from source image URLs when available and cached on disk
- New-feed notifications (in-app 5s monitoring tick; Android JobScheduler background fallback *at least* 15 minutes, subject to system scheduling)
- Opt-in floating TV alert banner with image, satellite and transponder: enable **▣ عائم** and grant **Display over other apps**. An explicitly enabled foreground service checks for updates every 5 seconds, with a persistent notification and a Stop action. Two newly discovered feeds display simultaneously, top-right and bottom-right, for 10 seconds; remaining new feeds queue in pairs. Long-press the toggle for an example banner. Android/OEM restrictions may block overlays on secure video surfaces.
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

Permission `POST_NOTIFICATIONS` must be granted on Android 13+. Android power management may delay background jobs; prompt push notifications require an official server-side API / push provider. 5-second primary-source polling requires the foreground monitor to stay running and an active network connection. Android/OEM battery restrictions or site rate limits can delay checks; periodic JobScheduler fallback cannot promise 20 seconds. The app is unofficial and is not associated with live-feed.net.

### Telegram preview limitations
- User-requested channel: https://t.me/livefeeds1 (public HTML attempt at https://t.me/s/livefeeds1).
- The availability of THIS particular channel's public preview could not be independently verified in this development environment. The Telegram parser is covered by an HTML fixture unit test, **not** a live integration test.
- If Telegram blocks the preview, it fails safely and the other two sources keep working. A reliable authorized API feed would need the channel owner's cooperation or an official credentialled integration.
- Human reports are labelled REPORTED, not ON AIR. Freshness does not imply verified signal. Avoid publicly redistributing channel content without permission.

### Telegram troubleshooting (v1.2.1)
Tap **المصادر** in the app header to see the last fetch outcome for each source.
Telegram diagnostics display HTTP/network errors, whether Telegram returned a public message list, and how many DVB posts the HTML parser recognized. **فحص الآن** forces a new check; **فتح قناة تيليگرام** opens the channel in a browser/app. The public preview is not guaranteed to work on every network or device, and a successful APK build does not validate access to it. No Telegram credentials are embedded.
