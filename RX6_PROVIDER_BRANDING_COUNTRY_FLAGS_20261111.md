# IRAQhub RX6: provider branding and country flag in InfoBar

**Basis:** User-uploaded workbook:
`RX6_TV_Providers_Arena_Frequencies_EN_2026-1.xlsx`
SHA-256: e03a8eda058160d1de719b2b05bf8f454995b92e76f543b26a8866af3e227dab

**Key rule:** `TVP0001` etc. are workbook numbering ONLY. These values are NOT
official provider IDs, CHMAX provider IDs or DVB service IDs and are deliberately
excluded from the runtime dataset.

- Offline GZIP register: 234 provider-name entries, 1376 country/channel
  relationships, 7 complete satellite-service references for Arena variants.
- Country flags appear under the channel logo in the original Tiger RX6
  InfoBar. Derived from **channel market** in the user's register.
  Where identical channel names exist in several countries, do NOT guess:
  resolve via an exact DVB satellite identity or confirmed receiver provider,
  otherwise show no false flag.
- Official CHMAX/provider logo fetch and existing source artwork remain first.
  Registered entries without a confirmed official logo use a simple
  **typographic provider-name badge** as a visible offline fallback.
  This is not represented as an authentic trademark graphic.
- Black/dark verified logos receive a thin WHITE OUTSIDE stroke on the RX6
  dark skin. White/light and colourful logos retain original pixels.
- The receiver's home skin, RX239 backgrounds, channel images, posters,
  playback and native/manual EPG engines remain untouched.
- Original InfoBar only gains 13dp flag row, small header/bar height allowance,
  and modestly larger provider/logo area.
- Side-by-side preview package: com.smarthub.plus.rx6providers.

Known constraints: 234 provider entries are NOT 234 confirmed official logo
image files. The source workbook contains provider names, not copyrighted
PNG logos or image URLs. Typed badge placeholders are used when no verified
official image is available. Physical X5M testing required for flag emoji
glyph availability and provider/channels mapping.
