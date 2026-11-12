# RX6 — 234 provider logo corrections (source only)

**No APK was built and no application installed.** This branch is deliberately not included in RX6's Android build workflow push filters. Changes are isolated on top of the provider-family fixes from PR #57.

## What has been fixed in the source

- Retained the **234 original user-reviewed provider IDs** and their decisions in `RX6_234_Logo_Audit_Report.csv`.
- Blocked **26 clearly wrong logo URLs**, including the singer Pink mistaken for Pink Media Group, A2 for A1 Macedonia, Dolby Digital Plus for Slovenian PRO PLUS, an orchestra for RTVE, Ministry of Finance for Kuwait Ministry of Information, and flags masquerading as logos of generic groups.
- The audit is **not** an automatically trusted logo database. `approve` and `keep` preserve the user's decisions, but a remote image still needs successful download and visual confirmation before inclusion.
- Added **four locally stored vector versions of user-supplied references**: white Arena mark, red-backed DigitAlb, colorful Sky and white CANAL+ wordmark. These are render-ready vector tracings of the supplied references, not byte-identical original PNGs.
- Mapped those four families to **11 specific country-provider entries**: five Arena entries (RS, HR, ME, BA, SI), three Sky (IT, DE, GB), two DigitAlb/SuperSport (AL), one CANAL+ Polska (PL).
- These four files are in `app/src/main/assets/provider-branding/images`; their SHA-256 checksums are pinned in `real-logo-manifest.json`, and `scripts/fetch_rx6_provider_logos.py` validates those checksums *without network access*.
- Prevented false green/color badges: if the provider does not have an approved genuine logo, InfoBar shows the **provider name as plain text**, not a fabricated corporate mark. Existing actual CHMAX/OEM channel artwork stays separate.
- Did not modify the factory DVB channel database, EPG source priorities, receiver settings or CHMAX storage.

## Coverage is not the number of approved photographs

| Review result | Count |
| --- | ---: |
| Submitted entries | 234 |
| Candidate URLs still requiring image validation | 133 |
| Clearly wrong submitted URLs blocked | 26 |
| Need source verification | 14 |
| Need actual direct image URLs | 2 |
| Owner-supplied artwork mapped in review | 10 (plus the 3rd Sky country mapping added in source) |
| Need user choices | 32 |
| Need actual images | 8 |
| Kept previously embedded, with no new file supplied | 7 |
| Generic category without one corporate brand | 1 |
| Rejected by user | 1 |

The source manifest currently has **61 provider mapping entries** referencing artwork, but not all are separately validated corporate logos. Seventeen older files are TV channel/network graphics reused for related provider identities and require a separate human review. These are not presented as 61 unique, checked corporate pictures.

## Rules preserved

- **Sky**: shared colorful family mark for Sky-branded services.
- **Arena**: shared owner's white Arena mark for all named Arena variants.
- **DigitAlb**: red corporate mark for SS-1 through SS-7 on the Albanian DVB group.
- **Tring**: separate brand for Tring Sport 1 through 7 (existing source, no new unverified artwork added).
- **CANAL+ Polska**: user-supplied white CANAL+ wordmark for Polska on Hotbird 13°E; not Canal+ France.
- **Airtel Digital TV**: 108.2°E SES 7/9 DTH branding; service/platform affiliation does not imply channel ownership.
- **OSN**: white OSN mark, not a generic wordmark with a fake black background.
- **Neutral black text/art**: adapt to white on dark; preserve actual chromatic logos and use the broadcaster's country flag only when backed by channel identity.

## Source-only quality gate

Run `python3 scripts/check_rx6_provider_logo_audit.py` from the repository root.

It checks that the 234-row audit is complete, the 26 known wrong links remain disabled, only the four pinned user marks are treated as newly verified local images, the 11 family references match, and no channel-logo asset mappings are added to the provider image manifest. It does **not** compile or sign an APK.

## Still pending before user authorizes an APK

- Visually verify and download remaining approved logos one by one; an image URL is not evidence the art belongs to the named company.
- Decide the 32 marked `needs_choice` and provide genuine images for eight `needs_image` entries.
- Audit 17 reused broadcaster channel/network marks for suitability as provider identities.
- Add and test the **CHMAX-only / Internet+local-only / Auto** channel-logo source switch separately; this branch fixes provider artwork, not that channel-image resolver.
- Validate EPG and CHMAX on actual Amlogic X5M receiver; this branch makes no claim to have fixed live EIT/provider authorization.

**User's private receiver database and API keys were not committed.**
