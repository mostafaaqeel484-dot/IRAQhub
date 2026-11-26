# IraqBISS TV · Cinematic Glass v0.3 beta

Standalone native Android TV feed metadata monitor with Apple TV-inspired cinematic presentation (not affiliated with Apple).

### What's new
- Adaptive grid of image cards, an immersive featured-feed hero, remote D-pad focus glow and subtle focus scale.
- Only displays a feed's actual source-provided screenshot or post image when associated with that frequency; no invented images.
- Images in the two top/bottom TV pop-ups when available.
- On-device bounded cache (JPEG-style filename, HTTPS source, 1.5 MB transfer limit, sampled RGB565 bitmaps, cache pruning), safe for 32-bit Amlogic Android TV 14.
- Existing public sources: Live-Feed, Satelliweb, public Telegram previews when accessible, configurable HTTPS RSS.
- Favorite, filter (sports, FTA, 4:2:2), copy transponder, custom sources and diagnostics.

Build CI: `.github/workflows/iraqbiss-glass-apk.yml`
Release: `iraqbiss-glass-v0.3.0-beta`, asset `IraqBISS-tvOS-Glass-v0.3.apk`.

Install: `adb install -r IraqBISS-tvOS-Glass-v0.3.apk`
Then open IraqBISS Glass and enable *Display over other apps* for popups on DVB TV.

Image display and parsers are best effort and source-dependent. No unverified generic/website cover images are substituted. Reported satellite transponders are not verified as on-air. Does not tune DVB or unlock BISS. OEMs may block overlay on protected video surfaces.
