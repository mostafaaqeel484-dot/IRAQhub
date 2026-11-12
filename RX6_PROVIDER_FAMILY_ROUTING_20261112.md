# RX6 provider-family artwork routing – 2026-11-12

## The fix

The 234-row spreadsheet is a **mixed provider/broadcaster/carriage catalogue**, not 234 distinct verified logo images or DVB provider identifiers. This checkpoint intentionally does not claim all 234 logos are official. The channel name/provider label alone is insufficient for every service.

Never replace CHMAX/OEM **channel** images with operator art. The artwork described here is exclusively the separate InfoBar **provider** badge. Manufacturer EPG Pro → DVB EIT → user-initiated XMLTV/EPGShare priority is unchanged.

Brand priority (before the workbook's unique published-owner lookup):
1. `Sky ...` / `SkyNews...` including Sky Sport Arena → shared colorful Sky brand
2. `Arena Sport`, `Arena Premium`, `Arena Fight`, `Arena Tenis`, etc. → white Arena family mark (all regional variants), while retaining verified country-specific flags
3. Albania on Eutelsat 16°E: `SS-1` through `SS-7`, `SS-HD 1`, `SuperSport Albania` → DigitAlb
4. `Tring Sport 1`–`7`, Sport News and other Tring channels → Tring
5. `CANAL+` and published Canal+ Polska-only branded subfamilies on Hotbird 13°E → **Platforma Canal+ Polska**; NEVER apply Poland badge to the French CANAL+ feed on Astra
6. SES 7/9 at 108.2°E (receiver's selected Airtel DTH package): use the Airtel Digital TV *platform* badge for the service lineup, **not** claim Airtel owns individual channels like Sony or Star
7. OSNtv → white OSN; MBC, Rotana, Al Jazeera and beIN → distinct Arabic-network provider marks; beIN Turkey stays with Digitürk when the DVB platform is Turkish
8. Remaining channels → only a uniquely identified workbook publisher/group; avoid forcing a carriage provider's logo solely from an unrelated listed channel name

## Original receiver database verification

The locally supplied Android receiver SQLite DB has 10,672 service records across 45 satellites and no reliable provider IDs: `program_table.provider_id` is zero for all channels. This DB was **not** uploaded to GitHub. Investigated concrete service names / physical coordinates, not preexisting 234-row workbook IDs as DVB identifiers.

- SES 7/9 is stored as 1082 (108.2°E), not 1083: 421 services.
- Hotbird 13°E is stored as 130; CANAL+ Premium / Sport / Extra etc. coexist there with Sky Italia and other packages.
- Eutelsat 16A has `SS-1 HD` through `SS-7 HD`, `Tring Sport 1 HD` through `Tring Sport 7 HD`, and multiple Arena regional variants.
- Region-specific Arena service identities remain distinct even though their provider logo is the same.
- Do not auto-assign EPG events from provider logo matching or name similarity.

## Newly audited Wikimedia Commons provider-image candidates

See `real-logo-manifest.json` for exact source image URLs. Image rights/trademarks stay with owners, including:
- CANAL+ wordmark: `File:Canal+Logo.png`
- MBC Group (Saudi Arabia): `File:MBC Group Logo.svg` (not Philippine MBC Media Group)
- Rotana Group: `File:Rotana Group Logo.svg`
- Al Jazeera logo: `File:Aljazeera logo.png`
- beIN Sports (global): `File:Bein Sports Logo.svg`
- Dubai Media: `File:Dubai Media Logo.svg`
- Abu Dhabi Media Network (2023): `File:Abu Dhabi Media Network logo 2023.svg`
- Alkass Sports: `File:شعار قنوات الكاس الرياضية.svg`
- Al Sharqiya: `File:Al Sharqiya.png`
- Al Rabiaa: `File:شعار قناة الرابعة.svg`

The remaining providers still display clearly marked colored placeholders if there is no trusted original image. The goal is **correct affiliation without fabricated logos**, not a falsely completed 234-artwork claim.

## Acceptance tests

`ProviderFamilyRulesTest` checks Sky vs Arena on Hotbird, seven DigitAlb SS and seven Tring Sport channels, Poland vs France CANAL+, Airtel SES, OSN/MBC/Rotana/Al Jazeera, and beIN MENA vs Turkish distribution.
