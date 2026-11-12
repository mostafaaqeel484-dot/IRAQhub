# RX6 provider logo fixes — source stage only (NO APK BUILD)

- Based on RX6 PR #57; channel/source precedence unchanged.
- Four locally stored source-image vectorizations from user's own artwork: Sky color gradient, Arena white, DigitAlb red, CANAL+ white. SHA-256 enforced in source-download script.
- 11 explicit provider-name bindings now use these four local files.
- Removed 17 provider-logo mappings which incorrectly pointed to channel-only art. These do not touch CHMAX channel-logo datasets. Former mappings: Kanal 7 Media Group; NOW Türkiye; Show TV; Turkuvaz Media / ATV; Pink Media Group; Euronews Albania; Klan TV Group; HRT - Croatian Radiotelevision; Nova TV Croatia; bTV Media Group Bulgaria; Nova Broadcasting Group Bulgaria; TV2 Group Hungary; La7 / Cairo Communication; Rai 3 TGR regional feed services; ARTE; TF1 Group; TVI / Media Capital.
- 234 workbook companies/groups and their user decisions remain a separate audit inventory; unverified candidate URLs are NOT integrated as trusted assets.
- No Build, no device install, no claim that 234 logos were verified.
