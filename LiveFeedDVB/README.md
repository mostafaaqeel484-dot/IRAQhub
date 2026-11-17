# LiveFeed DVB — Android TV

A standalone native Android TV receiver companion (Android 8.0–14+; no NDK; works on 32-bit ARM), **not** a modification of IRAQhub.

- Native Arabic, focusable TV UI, grid of feed cards, filters (on-air, 4:2:2, favorites), searchable frequencies
- Satellite / frequency / polarization / symbol rate / codec / chroma information collected from https://live-feed.net/?show_off=1
- Feed snapshots are fetched from source image URLs when available and cached on disk
- New-feed notifications (in-app 90s polling; Android JobScheduler background *at least* 15 minutes, subject to system scheduling)
- Cold start sets a notification baseline to avoid false floods
- Can copy a transponder and launch IRAQhub for manual DVB tuning; **does not** control tuner or auto-tune
- No BISS keys are stored or included
- No API is assumed: the HTML parser is best-effort, and may need adjustments if the third-party site changes markup; data is not guaranteed in real time

## Build

```sh
gradle -p LiveFeedDVB :app:assembleDebug --no-daemon
```

Android SDK 35 and JDK 17 required. Output: `LiveFeedDVB/app/build/outputs/apk/debug/app-debug.apk`.

Permission `POST_NOTIFICATIONS` must be granted on Android 13+. Android power management may delay background jobs; prompt push notifications require an official server-side API / push provider. The app is unofficial and is not associated with live-feed.net.
