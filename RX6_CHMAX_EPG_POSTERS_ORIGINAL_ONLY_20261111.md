# RX6 Original source ONLY — targeted CHMAX / EPG / Poster update

This branch was created from the already-uploaded full RX6 original-source branch
(`feature/rx6-original-source-only-20261111`), not from earlier RX6/Factory/4.0.4 feature branches.
All original `rx239` skin assets, `RxSkin.java`, artwork assets and channel logo images remain byte-identical.
The GitHub workflow verifies both source-authored SHA256 manifests before producing the APK.

Implementation:
- Keep original InfoBar card and themes. Reduce logo's allocated height within the 81dp header and show channel name on up to 2 auto-sized lines.
- Accept manufacturer EPG Pro as the top priority ONLY after exact DVB identity + channel-name + event-time checks; EIT/XMLTV remain fallbacks.
- Auto-fetch official CHMAX channel artwork on tune/channel selection. 500 ms cache/revision inspection (not 2 network calls/sec) and network backoff >=120 seconds. Official logo PNGs are stored in app `filesDir/native-channel-branding` and do not expire after a week; existing native packages still write to `filesDir/resolved-logos`.
- TheSportsDB: accept source-supplied banner if poster/thumb missing. Resolve missing badges with exact numeric team IDs, bounded to 4 lookups per prefetch job.
- Poster pipeline: exact IMDb title metadata only, fall back to RPDB and OpenPosterDB (public demo key `t0-free-rpdb`), with cached validated images. Archive.org is a last resort only when title/year match and a compatible explicit CC license is published.
- OMDb's dedicated Poster API requires a separate patron key and is **not** usable with the RPDB keys; deliberately no invalid or guessed OMDb calls.
- Video, DVB scan/motor/BISS, channel list, original background art and skin renderer are unchanged.
- Side-by-side package: `com.smarthub.plus.rx6epgchmax`; CI signature is ephemeral, so do not rely on it as a stable update key.

Limits:
- CHMAX services can reject calls without firmware-authorized identity/profile; the app does not bypass OEM privileges.
- TheSportsDB free key is rate-limited, and valid image URLs do not prove every sporting event has a poster.
- An APK built on CI is not the same as verified on a physical Amlogic X5M receiver.
