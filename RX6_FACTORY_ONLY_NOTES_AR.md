# IRAQhub RX6 · separate factory-only preview

The main branch and original uploaded APK are untouched.

Build target: gradle :app:assembleRx6FactoryPreview
Separate application ID: com.smarthub.plus.rx6factory

Scope:
- Enlarge the channel and provider logos and remove connected ivory/white logo borders.
- Prefer the observed CHMAX/OEM provider identity and verified DVB service entry for provider artwork.
- Check cached logo revision every second ONLY while InfoBar is visible; no 1-second online requests.
- Preload factory EPG / EPG Pro ahead of the DVB tune, asynchronously; respect native DVB EIT, then factory verified channel cache, then XMLTV.
- Preserve all other playback, scanning, remote controls and UI logic.
- Add Sky Italia and RAI provider artwork mappings using Wikimedia Commons.

This is an engineering preview. CI build and X5M receiver testing are still required.
