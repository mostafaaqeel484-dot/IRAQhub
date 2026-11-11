# IRAQhub logo sources

The default channel/network logo path is independent of CHMAX and has no API key.

| Module | Responsibility |
|---|---|
| ChannelAliasDatabase | Local aliases and explicit provider mapping; validated atomic JSON replacement |
| ChannelLogoResolver | Bundled verified assets, mapped HTTPS sources and verified public catalogue fallback |
| ProviderResolver | Explicit channel-to-network relationship, never digit stripping |
| ProviderLogoManager | One shared provider asset for all channels in that network |
| ChannelLogoCache | 12 MB memory LRU and permanent PNG files in app-private storage |
| LogoRepository | Cache-first synchronous memory callbacks, IO coroutines, deduplicated requests, three bounded image jobs, retry backoff |

`assets/logo-resolver/mapping.json` is the default editable mapping. `files/logo-mapping.json` is its optional user override. The settings screen imports/exports it through Android's document picker. Unknown names do not select unrelated channels. Explicit JSON mapping conflicts are rejected. Public catalogue duplicates for the same matched name now select a stable random candidate, preferring English and the best image size within each candidate. Generic Abu Dhabi Sports/Dubai Sports mappings provide a network only, never a guessed channel logo.

The OSD keeps the channel logo on the left and puts the provider at the far right, after the clock and signal meters. Both images preserve their aspect ratio. The provider image is 40% of the primary image width; when it is unavailable its name replaces the image. Unknown provider names are marked unavailable. A neutral local vector is used while a channel logo is missing. Responses are bound to the active channel identity before applying them.

ResolutionBadge uses actual Media3 video-size callbacks for IPTV and fresh receiver decoder fields for native TV. Unknown dimensions hide the badge. SD, HD, FULL HD and 4K/UHD assets are local vector drawables; names such as “4K” are not measurements.

## Optional CHL add-on

`ChlChannelIndex` reads the manufacturer's concatenated JSON objects, or a JSON array. Its bounded scanner handles quoted braces, escaped quotes, UTF-8 and a BOM without regex splitting. It validates satellite/transponder references and stores only name, recorded provider, orbital position, band, frequency, polarity, symbol rate and SID. A failed import leaves the previous index intact. Favorites, locks, original channel ordering, audio/subtitle data and export indexes are not retained or written to the receiver.

After import, a validated compact binary index avoids repeated JSON parsing on process startup. Its IO loading does not block the independent logo cache path.

The imported file is a snapshot. Its CA/codec fields are not live encryption, signal or resolution measurements. A CHL export index is not a native programme ID. Names retain quality suffixes when resolving a physical CHL service. As requested, duplicate names on different services select a random matching logo candidate using an installation-specific seed. The order is stable across navigation/restarts, and up to three matching services are tried if a source has no image. This never changes receiver tuning or decoder measurements. A provider name is used only if all matching records agree; a provider image still requires an exact trusted provider mapping.

`ChlLogoAddon` is explicitly optional and disabled by default. When enabled, an unmapped channel with matching CHL records may use the manufacturer's own authenticated logo endpoint, falling back to the independent resolver if unavailable. Mapped bundled channel logos continue to use the independent path. Retrieved images share the repository's disk cache, LRU, request deduplication and IO limit.

The factory client uses this app's own receiver authentication/session and normally accessible hardware properties. Its compatibility profile is kept in encrypted Android Keystore storage, not compiled into the source or APK. This receiver was provisioned through the separate test APK's RSA/AES-GCM envelope. That endpoint and profile are absent from the delivered application. A different installation needs a valid receiver session and compatibility profile; importing CHL alone does not grant factory authentication. There is no root, native database modification, borrowed factory token, system UID/signature impersonation or relaxed TLS validation.

CHMAX logo responses must match the requested satellite, complete transponder string and SID. The observed SDK/CDN format appends `.png` to its returned icon path. The add-on accepts only the tested HTTPS CDN host/path, rejects userinfo/query/fragment and never uses a generic/default/transponder image as a channel or provider logo. No bulk logo downloads occur on import.

The user's original CHL is not bundled in either application or source archive. Test instrumentation receives a temporary app-cache copy and deletes it after parsing. Test video clips are AndroidX Media fixtures in the test APK only and are never displayed as live channels.

## Attribution

Independent metadata: https://iptv-org.github.io/api/channels.json and https://iptv-org.github.io/api/logos.json . Bundled channel images reference the public URLs recorded in mapping.json. MBC Group and beIN SPORTS network marks are from Wikimedia Commons, with source URLs recorded in the mapping. Images and trademarks remain the property of their respective owners. CHMAX images are obtained only through the optional authenticated manufacturer integration for the user's receiver.


## TV devices without a document picker

The actual GAZAL receiver does not resolve OPEN_DOCUMENT or GET_CONTENT. The CHL import button therefore starts an in-app LAN receiver when a picker is missing; there is also an explicit receive button. Its random-token URL serves an upload page and accepts only a bounded raw CHL body. It never serves local files or provider credentials. Requests from foreign origins, wrong tokens, unexpected content types and oversized bodies are rejected. The receiver expires after ten minutes and closes when the settings page leaves the foreground. This is a temporary endpoint inside the TV app, with no required computer backend. The normal document-picker path remains available on TVs that provide one.

CHL logo cache keys now represent the matched channel name group, so reading a saved image does not wait for full CHL index loading. Recorded provider names for visited channels are retained separately for fast text fallback and cleared when an imported index is replaced. Provider/network relationships still require explicit mapping or consistent imported metadata; random logo selection never invents a provider. SVG viewport sizing is normalized before rasterization, and provider images use a light local plate for contrast.