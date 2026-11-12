# RX6 — Independent channel logo sources / provider art / 133 approved providers

## User-accepted behaviour

**Channel logos** have two independent switches:
- **CHMAX/OEM source**: explicitly authorised CHMAX/factory image, if the receiver has a valid profile.
- **External/local source**: independently downloaded, packaged, USB-selected or mapped channel logos.
- Turn on **both** to prefer CHMAX and fall back to outside images. Either switch may be used alone; both may be disabled.
- Each distinct on/off combination uses a distinct `rx6:channel:source-v4:{mask}:` cache namespace. The old shared cache is not consumed by a disabled source. During 4:2:2 priority, cached fast-path issues no network requests.

**Provider logos** have their own on/off switch **unrelated** to either channel source:
- Add a provider image locally by two-letter country, brand name, and optional exact current DVB service.
- App-private provider artwork is never stored in CHMAX/OEM folders or as a CHANNEL logo.
- Previously approved Sky/Arena/DigitAlb/CANAL+ white/coloured images remain the highest-priority local group marks; a manually selected provider mark overrides them when matching.
- When no genuine/user-approved image is present, the provider's name appears as text rather than a fictitious color logo.

## The user-approved 133 URLs

`app/src/main/assets/provider-branding/approved-provider-sources.json` includes **133 distinct, explicitly approved** URLs from the user's 234-row audit. The **26 blocked wrong links remain excluded**.

These are **user-approved candidates**, not 133 validated/cached pictures. Only when a provider is actually matched does RX6 attempt its specific HTTPS source, validate image size and format, and store a 512px PNG in the private *provider* image cache. Wikimedia Commons redirects may resolve only to Wikimedia upload/thumbnail hosts. Other redirects, private-network DNS responses, invalid HTML/image bodies and oversized downloads are rejected. Failure leads to a text label, never a foreign logo or channel-art override. No bulk 133-image request runs on startup or during 4:2:2 video priority.

Existing 234-provider country/channel catalogue and manufacturer/DVB EPG priority stay unchanged.

## API key menu and InfoBar linkage

- A distinct **Save encrypted keys** action already exists for RPDB, OPDB, OMDb and TheSportsDB; now each has a **Test connection** button using a user-supplied or previously saved key.
- TheSportsDB may be tested via free shared `123` (rate-limited).
- Separate **Save without test**, **Test only**, and **Test and save** options are present for TMDB / OpenSubtitles / Subsource.
- Actual credentials remain encrypted with Android Keystore. Neither test results nor logs include credentials or a key-bearing URL. A saved key is **not** reported as a successful test.
- Poster/InfoBar pipeline already uses `OnlineProgramme` → `VerifiedPosterFallback` → `PrivatePosterCache` for RPDB, OPDB and OMDb; Sports programme images use `TheSportsDBProvider`. Key saves signal `epg_art_changed_at` for artwork refresh.
- API test results can be unverified if the provider rejects HEAD requests, sample image isn't available, free tier is limited or the network is unavailable. These outcomes are not treated as bad keys without proof.

## Quality gate (source only)

- 234 audit entries present.
- 133 approved image URLs, unique provider IDs.
- 26 blocked known incorrect sources excluded.
- 0 bundled provider-manifest channel logos.
- 4 channel source mode combinations, provider switch separate.
- Java/Kotlin text structure and UI wiring reviewed.
- **No APK was built** and no real receiver or authenticated API connection was tested. Do not mark this PR release-ready until Android/Gradle compilation, live CHMAX and manual-provider tests succeed.

## Source selection caveats

- The user's newly approved links may belong to old logos, news images or unrelated sites; approval authorises attempting the exact URL, not falsely certifying it as a company's official trademark.
- Background URL availability may change, especially Commons redirects. Offline provider-name fallback remains available.
- Only exact manufacturer/provider identity or user-assigned DVB channel overrides can select provider images. Satellite orbit alone must not assign a company's brand to every broadcaster on the satellite.
