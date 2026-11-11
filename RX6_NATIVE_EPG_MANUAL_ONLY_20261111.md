# RX6 original source — native EPG first / manual-only external guide

Base: uploaded full RX6 original-source history, retaining original rx239 skins,
wallpapers, custom fonts, native logo assets and all poster-source toggles.

- Manufacturer EPG Pro is the first verified provider.
- Native DVB EIT comes second. Access requires the firmware to expose
  locked tuner and complete service identity; no bypass of OEM permissions.
- External EPGShare/XMLTV has NO scheduled, change-channel, auto-prefetch or
  on-demand network activity. Legacy workers return success without fetching.
- ManualEpgActivity is reachable under Settings > InfoBar. It previews an
  explicit EPGShare feed (e.g. SA1) or bounded HTTPS XML/XML.GZ and saves only
  exact-service-scoped events inside filesDir/manual-epg with the original RX skin.
- External records never overwrite manufacturer databases or native EIT. They
  fill NOW/NEXT gaps only. Existing automatically fetched guides are ignored.
- Native CHMAX logos retained and exact-service images are persisted under
  the same key for list and InfoBar across reboots/offline playback.
- Enlarges channel brand within the original 81dp header, preserves FIT_CENTER
  and edge-connected pale matte cleaning.
- Poster source toggles stay independent from EPG guide fetch behavior.
- Preview package com.smarthub.plus.rx6nativeepg. This uses CI debug signing:
  old package data is not transferred automatically. Stable signing and app ID
  should be established for production updates.
- Build/test success is not evidence of authorised manufacturer EPG access
  for every OSN channel; physical receiver tests required.
