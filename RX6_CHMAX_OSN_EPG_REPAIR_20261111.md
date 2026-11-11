# RX6 original source / CHMAX timeout and OSN EPG repair

Base: feature/rx6-poster-source-switches-20261111 (the full original RX6 source +
independent poster-provider controls).
Original RX239 skin, PNG/JPEG art and logo DB remain unchanged; CI's asset
SHA-256 manifests verify byte-for-byte identity.

CHMAX:
- EPG Pro error code 50000 is a server-side business rejection and is **not**
  bypassed. EPG lookups apply a scoped 20-minute backoff so the CHMAX service
  remains independent and available.
- Automatic single-channel logos use the supported /gchsapi/v1/chIconAndName
  through ChlLogoAddon, not EPG Pro's rejected /schedule/list route.
- Do not hold a synchronized ChlLogoAddon monitor across long network calls.
- Official satellite ZIPs use 12-satellite batches, per-satellite checkpoints,
  avoid repeatedly downloading a validated zero-match package, continue past
  limited failed packages, and retry failures with WorkManager 15-minute
  exponential backoff. Completed images are retained and used offline.
- Manufacturer CHMAX HTTP requests have a bounded 50s call timeout and no
  redirect/credential bypass.

EPG:
- Native verified events and Factory EPG Pro retain precedence over valid
  XMLTV fallback when available.
- OSN SA1 EPGShare feed availability verified against publisher listing.
  Normalization handles exact, non-ambiguous OSN Movie(s)/TV variants.
- XMLTV guide moves to persistent filesDir (legacy cache is migrated). An
  on-demand fetch wakes NOW/NEXT lookup immediately rather than requiring a
  channel zap. Failures display source-specific diagnostics while keeping
  fallback operational.
- Do not change DVB tuner or private OEM services. A TUNER_NOT_LOCKED reading
  is not falsified.

APK: com.smarthub.plus.rx6chmaxepgfix, signed for isolated testing.
Real X5M receiver EPG authorization and OSN coverage remain device-dependent.
