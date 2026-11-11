# RX6 Authentic Provider Logos + Independent Poster API Keys
- Source: original RX6 (RX239 skin unchanged) + PR #52 native EPG + country flags.
- Workbook TVP numbers are row counters, NOT official IDs. Country + exact
  channel name and verified DVB identity gate the artwork mapping.
- 398 distinct country-matched channel logos and 43 provider records are
  mapped to authentic source images (some reuse the same brand mark), with
  408 unique source image files. Exact bundled count appears in CI report.
- Sources pinned to tv-logo/tv-logos d32e347bb7c4c640dceec23957802ad9182f58a6
  (personal-use terms; ask its maintainer before broader distribution);
  simple-icons/simple-icons cd796c98565fc466923fc1128b962e133fa3c612
  (CC0 but trademark conditions); Commons: OSN TV logo (2023), Arena Sport
  logo (2019), Digitürk logo. Attribution and licensing apply per logo.
- For operators without confirmed authentic marks, no synthetic text-badge
  is substituted. Existing CHMAX and RX6 exact-channel assets remain first.
- Airtel Digital TV (India) added; 108.2 E SES 7 provider association requires
  explicit Airtel broadcast provider name, not orbital position alone.
- Keys: RPDB, OPDB, OMDb (patron), and TheSportsDB V1 stored separately under
  Android Keystore AES-GCM. Existing TMDB config retained, no private keys
  embedded into APK or media metadata. Images fetched with private keys
  are saved under key-free local poster tokens.
- Other sources such as TVmaze/Wikimedia/Archive have no mandatory
  per-user key in this integration. BetterPosters has no invented key field.
- EPG Pro/DVB EIT priority, manual-only external EPG, receiver services,
  4:2:2 playback and original RX239 visual assets preserved.
- Experimental APK package: com.smarthub.plus.rx6realbrands.
- Not all 234 workbook providers have a verified separate corporate image;
  channel logos are not falsely presented as ownership marks.
